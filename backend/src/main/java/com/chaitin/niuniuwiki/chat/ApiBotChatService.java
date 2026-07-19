package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import com.chaitin.niuniuwiki.share.ShareAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 实现 OpenAI 兼容问答协议，并将 API Bot 鉴权与 HTTP 控制器隔离。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class ApiBotChatService {

    private final ChatService chatService;
    private final ShareAccessService accessService;
    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;

    public ApiBotChatService(
            ChatService chatService,
            ShareAccessService accessService,
            JdbcMaps store,
            JsonMaps jsonMaps,
            ObjectMapper objectMapper
    ) {
        this.chatService = chatService;
        this.accessService = accessService;
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
    }

    public Object completions(String kbId, String authorization, Map<String, Object> request, String remoteIp) {
        validate(kbId, authorization);
        ChatService.ChatResult result = chatService.ask(
                kbId, 9, lastUserMessage(request), "", "", remoteIp, List.of(), List.of(),
                KnowledgeAccessScope.publicAccess());
        String model = String.valueOf(request.getOrDefault("model", "niuniu-wiki"));
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = Instant.now().getEpochSecond();
        if (Boolean.TRUE.equals(request.get("stream"))) {
            return stream(result, id, created, model);
        }
        return Map.of(
                "id", id,
                "object", "chat.completion",
                "created", created,
                "model", model,
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", result.answer()),
                        "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0));
    }

    private SseEmitter stream(ChatService.ChatResult result, String id, long created, String model) {
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            Map<String, Object> chunk = Map.of(
                    "id", id,
                    "object", "chat.completion.chunk",
                    "created", created,
                    "model", model,
                    "choices", List.of(Map.of(
                            "index", 0,
                            "delta", Map.of("role", "assistant", "content", result.answer()),
                            "finish_reason", "stop")));
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void validate(String kbId, String authorization) {
        accessService.settings(kbId);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException("Authorization header is required");
        }
        String expected = secret(kbId);
        if (expected.isBlank() || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authorization.substring(7).getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException("Invalid Authorization key");
        }
    }

    private String secret(String kbId) {
        List<Map<String, Object>> apps = store.query(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 9", store.rowMapper(), kbId);
        if (apps.isEmpty()) {
            return "";
        }
        Map<String, Object> settings = jsonMaps.jsonMap(apps.getFirst().get("settings"));
        Map<String, Object> api = jsonMaps.jsonMap(settings.get("openai_api_bot_settings"));
        return Boolean.TRUE.equals(api.get("is_enabled"))
                ? String.valueOf(api.getOrDefault("secret_key", "")) : "";
    }

    private String lastUserMessage(Map<String, Object> request) {
        Object messagesValue = request.get("messages");
        if (!(messagesValue instanceof List<?> messages)) {
            throw new ApiException("messages cannot be empty");
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof Map<?, ?> message && "user".equals(message.get("role"))) {
                Object content = message.get("content");
                if (content instanceof String text) {
                    return text;
                }
                if (content instanceof List<?> parts) {
                    return parts.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .filter(part -> "text".equals(part.get("type")))
                            .map(part -> String.valueOf(part.get("text") == null ? "" : part.get("text")))
                            .reduce((left, right) -> left + " " + right)
                            .orElse("");
                }
            }
        }
        throw new ApiException("no user message found");
    }
}
