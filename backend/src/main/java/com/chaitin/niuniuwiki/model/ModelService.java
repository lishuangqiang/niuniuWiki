package com.chaitin.niuniuwiki.model;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import com.chaitin.niuniuwiki.rag.RagClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封装大模型相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-12
 */
@Service
public class ModelService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final RagClient ragClient;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ModelService(
            MyBatisStore store,
            JsonMaps jsonMaps,
            ObjectMapper objectMapper,
            AuthService authService,
            RagClient ragClient
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.ragClient = ragClient;
    }

    public List<Map<String, Object>> list() {
        requireAdmin();
        return store.query(
                "SELECT id, provider, model, api_key, api_header, base_url, api_version, type, is_active, "
                        + "prompt_tokens, completion_tokens, total_tokens, parameters FROM models ORDER BY created_at",
                store.rowMapper());
    }

    public Map<String, Object> create(ModelDtos.CreateRequest request) {
        requireAdmin();
        String id = UUID.randomUUID().toString();
        store.update(
                "INSERT INTO models(id, provider, model, api_key, api_header, base_url, api_version, type, "
                        + "is_active, parameters, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, ?::jsonb, now(), now())",
                id, request.provider(), request.model(), value(request.apiKey()), value(request.apiHeader()),
                request.baseUrl(), value(request.apiVersion()), request.type(), jsonMaps.json(request.parameters()));
        Map<String, Object> model = store.queryForObject("SELECT * FROM models WHERE id = ?", store.rowMapper(), id);
        ragClient.upsertModel(model);
        return model;
    }

    public void update(ModelDtos.UpdateRequest request) {
        requireAdmin();
        if (request.isActive() != null && !"analysis-vl".equals(request.type())) {
            throw new ApiException("仅支持修改视觉模型的启用状态");
        }
        store.update(
                "UPDATE models SET provider = ?, model = ?, api_key = ?, api_header = ?, base_url = ?, "
                        + "api_version = ?, type = ?, parameters = ?::jsonb, is_active = COALESCE(?, is_active), "
                        + "updated_at = now() WHERE id = ?",
                request.provider(), request.model(), value(request.apiKey()), value(request.apiHeader()),
                request.baseUrl(), value(request.apiVersion()), request.type(), jsonMaps.json(request.parameters()),
                request.isActive(), request.id());
        ragClient.upsertModel(store.queryForObject("SELECT * FROM models WHERE id = ?", store.rowMapper(), request.id()));
    }

    public Map<String, Object> check(ModelDtos.CreateRequest request) {
        requireAdmin();
        try {
            String type = request.type().startsWith("analysis") ? "chat" : request.type();
            if (isBaiLianNativeEmbedding(request, type)) {
                return checkBaiLianEmbedding(request);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.model());
            HttpResponse<String> response;
            if ("embedding".equals(type)) {
                body.put("input", "NiuniuWiki model connectivity test");
                response = send(request.baseUrl(), "/embeddings", request.apiKey(), request.apiHeader(),
                        objectMapper.writeValueAsString(body));
            } else {
                body.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK")));
                response = checkChatCompatibility(request, body);
            }
            return Map.of(
                    "error", response.statusCode() >= 200 && response.statusCode() < 300 ? "" : response.body(),
                    "content", response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
        } catch (Exception exception) {
            return Map.of("error", exception.getMessage(), "content", "");
        }
    }

    private HttpResponse<String> checkChatCompatibility(
            ModelDtos.CreateRequest request,
            Map<String, Object> baseBody
    ) throws Exception {
        HttpResponse<String> lastResponse = null;
        for (String parameter : List.of("max_tokens", "max_completion_tokens", "")) {
            Map<String, Object> body = new LinkedHashMap<>(baseBody);
            if (!parameter.isBlank()) {
                body.put(parameter, 8);
            }
            HttpResponse<String> response = send(
                    request.baseUrl(), "/chat/completions", request.apiKey(), request.apiHeader(),
                    objectMapper.writeValueAsString(body));
            lastResponse = response;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response;
            }
            if (response.statusCode() != 400 && response.statusCode() != 422) {
                return response;
            }
        }
        if (lastResponse != null) {
            return lastResponse;
        }
        throw new ApiException("模型参数兼容性检查未执行");
    }

    private Map<String, Object> checkBaiLianEmbedding(ModelDtos.CreateRequest request) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("text_type", "document");
        parameters.put("encoding_format", "float");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("input", Map.of("texts", List.of("牛牛 Wiki 模型连通性测试")));
        body.put("parameters", parameters);

        HttpResponse<String> response = sendAbsolute(
                baiLianEmbeddingEndpoint(request.baseUrl()),
                request.apiKey(),
                request.apiHeader(),
                objectMapper.writeValueAsString(body));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Map.of("error", response.body(), "content", "");
        }

        Map<String, Object> responseBody = objectMapper.readValue(response.body(), MAP_TYPE);
        Object outputValue = responseBody.get("output");
        if (!(outputValue instanceof Map<?, ?> output)
                || !(output.get("embeddings") instanceof List<?> embeddings)
                || embeddings.isEmpty()) {
            String message = String.valueOf(responseBody.getOrDefault("message", "empty embeddings"));
            return Map.of("error", message, "content", "");
        }
        Object first = embeddings.getFirst();
        int dimensions = first instanceof Map<?, ?> item && item.get("embedding") instanceof List<?> vector
                ? vector.size()
                : 0;
        if (dimensions == 0) {
            return Map.of("error", "empty embeddings", "content", "");
        }
        return Map.of("error", "", "content", "dim is : " + dimensions);
    }

    public Map<String, Object> supported(ModelDtos.ProviderRequest request) {
        requireAdmin();
        try {
            HttpResponse<String> response = send(
                    request.baseUrl(), "/models", request.apiKey(), request.apiHeader(), null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("get user model list failed: " + response.body());
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
            List<Map<String, String>> models = new ArrayList<>();
            if (body.get("data") instanceof List<?> data) {
                for (Object item : data) {
                    if (item instanceof Map<?, ?> model && model.get("id") != null) {
                        models.add(Map.of("model", String.valueOf(model.get("id"))));
                    }
                }
            }
            return Map.of("models", models);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("get user model list failed: " + exception.getMessage());
        }
    }

    @Transactional
    public void switchMode(ModelDtos.SwitchModeRequest request) {
        requireAdmin();
        if ("auto".equals(request.mode()) && value(request.autoModeApiKey()).isBlank()) {
            throw new ApiException("auto mode api key is required");
        }
        if ("manual".equals(request.mode())) {
            for (String type : List.of("chat", "embedding", "rerank", "analysis")) {
                Integer count = store.queryForObject("SELECT count(*) FROM models WHERE type = ?", Integer.class, type);
                if (count == null || count == 0) {
                    throw new ApiException("需要配置 " + type + " 模型");
                }
                store.update("UPDATE models SET is_active = true WHERE type = ?", type);
            }
        }
        Map<String, Object> setting = new LinkedHashMap<>();
        setting.put("mode", request.mode());
        setting.put("auto_mode_api_key", value(request.autoModeApiKey()));
        setting.put("auto_mode_provider", value(request.autoModeProvider()));
        setting.put("chat_model", value(request.chatModel()));
        setting.put("is_manual_embedding_updated", false);
        store.update(
                "INSERT INTO system_settings(key, value, description, created_at, updated_at) "
                        + "VALUES ('model_setting_mode', ?::jsonb, 'Model setting mode configuration', now(), now()) "
                        + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
                jsonMaps.json(setting));
    }

    public Map<String, Object> modeSetting() {
        requireAdmin();
        return store.query(
                        "SELECT value FROM system_settings WHERE key = 'model_setting_mode'",
                        store.rowMapper())
                .stream()
                .findFirst()
                .map(row -> jsonMaps.jsonMap(row.get("value")))
                .orElseGet(() -> Map.of(
                        "mode", "manual",
                        "auto_mode_api_key", "",
                        "chat_model", "",
                        "is_manual_embedding_updated", false));
    }

    private HttpResponse<String> send(
            String baseUrl,
            String path,
            String apiKey,
            String apiHeader,
            String body
    ) throws Exception {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return sendAbsolute(normalized + path, apiKey, apiHeader, body);
    }

    private HttpResponse<String> sendAbsolute(
            String url,
            String apiKey,
            String apiHeader,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        if (apiHeader != null && apiHeader.contains(":")) {
            int separator = apiHeader.indexOf(':');
            builder.header(apiHeader.substring(0, separator).trim(), apiHeader.substring(separator + 1).trim());
        }
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isBaiLianNativeEmbedding(ModelDtos.CreateRequest request, String type) {
        if (!"BaiLian".equals(request.provider()) || !"embedding".equals(type)) {
            return false;
        }
        String baseUrl = value(request.baseUrl());
        return baseUrl.contains("dashscope.aliyuncs.com")
                || baseUrl.contains("dashscope-intl.aliyuncs.com")
                || baseUrl.contains("/api/v1/services/embeddings/");
    }

    private static String baiLianEmbeddingEndpoint(String baseUrl) {
        String normalized = value(baseUrl).trim().replaceFirst("#+$", "");
        if (normalized.isBlank() || normalized.contains("/compatible-mode/")) {
            if (normalized.contains("dashscope-intl.aliyuncs.com")) {
                return "https://dashscope-intl.aliyuncs.com/api/v1/services/embeddings/"
                        + "text-embedding/text-embedding";
            }
            return "https://dashscope.aliyuncs.com/api/v1/services/embeddings/"
                    + "text-embedding/text-embedding";
        }
        return normalized;
    }

    private void requireAdmin() {
        authService.requireAdmin();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
