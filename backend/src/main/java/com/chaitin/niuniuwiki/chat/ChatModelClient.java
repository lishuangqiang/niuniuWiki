package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.model.ModelGateway.Completion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 统一封装推理模型选择、OpenAI 兼容请求、Token 用量解析以及可取消调用。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
@Component
public class ChatModelClient implements ModelGateway {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelClient.class);

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;
    private final Map<String, TokenLimitParameter> learnedTokenParameters = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ChatModelClient(JdbcMaps store, JsonMaps jsonMaps, ObjectMapper objectMapper) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
    }

    public Completion complete(
            String systemPrompt,
            String userPrompt,
            CancellationSignal cancellationSignal,
            int maxTokens,
            Duration timeout
    ) {
        return complete(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)),
                cancellationSignal, maxTokens, timeout);
    }

    public Completion complete(
            List<Map<String, Object>> messages,
            CancellationSignal cancellationSignal,
            int maxTokens,
            Duration timeout
    ) {
        Map<String, Object> model = configuredModel();
        Map<String, Object> parameters = jsonMaps.jsonMap(model.get("parameters"));
        TokenLimitParameter configuredParameter = TokenLimitParameter.configured(parameters);
        String capabilityKey = capabilityKey(model);
        TokenLimitParameter persistedParameter = TokenLimitParameter.learned(parameters);
        TokenLimitParameter learnedParameter = learnedTokenParameters.getOrDefault(
                capabilityKey, persistedParameter);
        List<TokenLimitParameter> candidates = tokenParameterCandidates(
                configuredParameter, learnedParameter);
        ModelRequestException lastCompatibilityFailure = null;
        for (TokenLimitParameter candidate : candidates) {
            try {
                Completion completion = execute(
                        model, parameters, messages, cancellationSignal, maxTokens, timeout, candidate);
                if (configuredParameter == TokenLimitParameter.AUTO) {
                    TokenLimitParameter previous = learnedTokenParameters.put(capabilityKey, candidate);
                    if (previous != candidate) {
                        LOGGER.info("Learned output token parameter {} for provider {} model {}",
                                candidate.name().toLowerCase(Locale.ROOT),
                                value(model.get("provider")), value(model.get("model")));
                    }
                    if (persistedParameter != candidate) {
                        persistLearnedTokenParameter(model, candidate);
                    }
                }
                return completion;
            } catch (ModelRequestException exception) {
                if (configuredParameter != TokenLimitParameter.AUTO
                        || !exception.canRetryWithoutTokenLimit()
                        || candidate == candidates.getLast()) {
                    throw exception;
                }
                lastCompatibilityFailure = exception;
            }
        }
        throw lastCompatibilityFailure == null
                ? new ApiException("模型请求失败: 没有可用的输出 Token 参数策略")
                : lastCompatibilityFailure;
    }

    private Completion execute(
            Map<String, Object> model,
            Map<String, Object> parameters,
            List<Map<String, Object>> messages,
            CancellationSignal cancellationSignal,
            int maxTokens,
            Duration timeout,
            TokenLimitParameter tokenParameter
    ) {
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            cancellationSignal.check();
            String baseUrl = value(model.get("base_url")).replaceAll("/+$", "");
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model.get("model"));
            request.put("messages", messages);
            request.put("stream", false);
            int configuredMax = configuredMaximum(parameters, tokenParameter);
            int safeMax = configuredMax > 0 ? Math.min(configuredMax, maxTokens) : maxTokens;
            if (safeMax > 0 && tokenParameter.hasExternalName()) {
                request.put(tokenParameter.externalName(), safeMax);
            }
            if (parameters.get("temperature") instanceof Number temperature) {
                request.put("temperature", temperature.doubleValue());
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout == null ? Duration.ofMinutes(2) : timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
            String apiKey = value(model.get("api_key"));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            String apiHeader = value(model.get("api_header"));
            if (apiHeader.contains(":")) {
                int separator = apiHeader.indexOf(':');
                builder.header(apiHeader.substring(0, separator).trim(), apiHeader.substring(separator + 1).trim());
            }
            future = httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = await(future, cancellationSignal, timeout);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelRequestException(response.statusCode(), safeErrorMessage(response.body()));
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
            Object choicesValue = body.get("choices");
            if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()
                    || !(choices.getFirst() instanceof Map<?, ?> choice)
                    || !(choice.get("message") instanceof Map<?, ?> message)) {
                throw new ApiException("模型响应格式无效");
            }
            String content = value(message.get("content"));
            Map<String, Object> usage = jsonMaps.jsonMap(body.get("usage"));
            int promptTokens = integer(usage.get("prompt_tokens"), estimateTokens(messages));
            int completionTokens = integer(usage.get("completion_tokens"), estimateTokens(content));
            return new Completion(content, promptTokens, completionTokens,
                    value(model.get("provider")), value(model.get("model")));
        } catch (CancellationException exception) {
            if (future != null) {
                future.cancel(true);
            }
            throw exception;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            if (future != null) {
                future.cancel(true);
            }
            throw new ApiException("模型请求失败: " + exception.getMessage());
        }
    }

    private static int configuredMaximum(
            Map<String, Object> parameters,
            TokenLimitParameter tokenParameter
    ) {
        if (tokenParameter.hasExternalName()
                && parameters.get(tokenParameter.externalName()) instanceof Number number) {
            return number.intValue();
        }
        if (parameters.get("max_tokens") instanceof Number number) {
            return number.intValue();
        }
        if (parameters.get("max_completion_tokens") instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static List<TokenLimitParameter> tokenParameterCandidates(
            TokenLimitParameter configured,
            TokenLimitParameter learned
    ) {
        if (configured != TokenLimitParameter.AUTO) {
            return List.of(configured);
        }
        LinkedHashSet<TokenLimitParameter> candidates = new LinkedHashSet<>();
        if (learned != null) {
            candidates.add(learned);
        }
        candidates.add(TokenLimitParameter.MAX_TOKENS);
        candidates.add(TokenLimitParameter.MAX_COMPLETION_TOKENS);
        candidates.add(TokenLimitParameter.NONE);
        return List.copyOf(candidates);
    }

    private static String capabilityKey(Map<String, Object> model) {
        return value(model.get("base_url")).replaceAll("/+$", "")
                + "|" + value(model.get("model"));
    }

    private void persistLearnedTokenParameter(
            Map<String, Object> model,
            TokenLimitParameter tokenParameter
    ) {
        String modelId = value(model.get("id"));
        if (modelId.isBlank()) {
            return;
        }
        try {
            store.update(
                    "UPDATE models SET parameters = jsonb_set(COALESCE(parameters, '{}'::jsonb), "
                            + "'{learned_output_token_parameter}', to_jsonb(?::text), true), updated_at = now() "
                            + "WHERE id = ?",
                    tokenParameter.name().toLowerCase(Locale.ROOT), modelId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to persist learned output token parameter for provider {} model {}: {}",
                    value(model.get("provider")), value(model.get("model")), exception.getMessage());
        }
    }

    public Map<String, Object> configuredModel() {
        List<Map<String, Object>> settings = store.query(
                "SELECT value FROM system_settings WHERE key = 'model_setting_mode'",
                store.rowMapper());
        if (!settings.isEmpty()) {
            Map<String, Object> mode = jsonMaps.jsonMap(settings.getFirst().get("value"));
            if ("auto".equals(mode.get("mode")) && !value(mode.get("auto_mode_api_key")).isBlank()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("provider", value(mode.get("auto_mode_provider")));
                result.put("model", value(mode.get("chat_model")).isBlank()
                        ? "DeepSeek-V3" : mode.get("chat_model"));
                result.put("base_url", "https://api.baizhi.cloud/v1");
                result.put("api_key", mode.get("auto_mode_api_key"));
                result.put("api_header", "");
                result.put("parameters", Map.of());
                return result;
            }
        }
        return store.query(
                        "SELECT id, provider, model, base_url, api_key, api_header, parameters FROM models "
                                + "WHERE type = 'chat' AND is_active = true ORDER BY created_at LIMIT 1",
                        store.rowMapper())
                .stream().findFirst()
                .orElseThrow(() -> new ApiException("请前往管理后台，点击右上角的“系统设置”配置推理大模型。"));
    }

    private static HttpResponse<String> await(
            CompletableFuture<HttpResponse<String>> future,
            CancellationSignal cancellationSignal,
            Duration timeout
    ) throws ExecutionException, InterruptedException, TimeoutException {
        long timeoutNanos = (timeout == null ? Duration.ofMinutes(2) : timeout).toNanos();
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            cancellationSignal.check();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                future.cancel(true);
                throw new TimeoutException("模型请求超时");
            }
            try {
                return future.get(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining), 250L), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Poll the cancellation signal while the remote model is generating.
            }
        }
    }

    private static int estimateTokens(Object value) {
        if (value instanceof List<?> list) {
            int characters = 0;
            for (Object item : list) {
                characters += String.valueOf(item).length();
            }
            return Math.max(1, characters / 3);
        }
        return Math.max(1, value(value).length() / 3);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String safeErrorMessage(String responseBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(value(responseBody), MAP_TYPE);
            Map<String, Object> error = jsonMaps.jsonMap(body.get("error"));
            String message = value(error.get("message"));
            if (message.isBlank()) {
                message = value(body.get("message"));
            }
            return message.isBlank() ? "上游未返回错误说明" : truncate(message, 500);
        } catch (Exception ignored) {
            return "上游响应格式无效";
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    enum TokenLimitParameter {
        AUTO(""),
        MAX_TOKENS("max_tokens"),
        MAX_COMPLETION_TOKENS("max_completion_tokens"),
        NONE("");

        private final String externalName;

        TokenLimitParameter(String externalName) {
            this.externalName = externalName;
        }

        String externalName() {
            return externalName;
        }

        boolean hasExternalName() {
            return !externalName.isBlank();
        }

        static TokenLimitParameter configured(Map<String, Object> parameters) {
            String configured = value(parameters.get("output_token_parameter"))
                    .strip().toLowerCase(Locale.ROOT);
            if (configured.isBlank() || "auto".equals(configured)) {
                return AUTO;
            }
            return Arrays.stream(values())
                    .filter(value -> value.name().toLowerCase(Locale.ROOT).equals(configured)
                            || value.externalName.equals(configured))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(
                            "不支持的 output_token_parameter: " + configured));
        }

        static TokenLimitParameter learned(Map<String, Object> parameters) {
            String learned = value(parameters.get("learned_output_token_parameter"))
                    .strip().toLowerCase(Locale.ROOT);
            if (learned.isBlank()) {
                return null;
            }
            return Arrays.stream(values())
                    .filter(value -> value != AUTO)
                    .filter(value -> value.name().toLowerCase(Locale.ROOT).equals(learned)
                            || value.externalName.equals(learned))
                    .findFirst()
                    .orElse(null);
        }
    }
}
