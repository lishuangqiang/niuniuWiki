package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.captcha.CaptchaService;
import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.conversation.ConversationService;
import com.chaitin.niuniuwiki.share.ShareAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 处理智能问答相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-01
 */
@RestController
@RequestMapping("/share/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;
    private final ConversationService conversationService;
    private final ShareAccessService accessService;
    private final CaptchaService captchaService;
    private final ObjectMapper objectMapper;
    private final MyBatisStore store;
    private final JsonMaps jsonMaps;

    public ChatController(
            ChatService chatService,
            ChatStreamService chatStreamService,
            ConversationService conversationService,
            ShareAccessService accessService,
            CaptchaService captchaService,
            ObjectMapper objectMapper,
            MyBatisStore store,
            JsonMaps jsonMaps
    ) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
        this.conversationService = conversationService;
        this.accessService = accessService;
        this.captchaService = captchaService;
        this.objectMapper = objectMapper;
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        return chat(kbId, 1, request, servletRequest, session, true);
    }

    @PostMapping(value = "/widget", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter widget(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        return chat(kbId, 2, request, servletRequest, session, true);
    }

    @PostMapping("/search")
    public ApiResponse<?> search(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        return ApiResponse.ok(Map.of("node_result", chatService.search(kbId, String.valueOf(request.getOrDefault("message", "")))));
    }

    @PostMapping("/widget/search")
    public ApiResponse<?> widgetSearch(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        return search(kbId, request, session);
    }

    @PostMapping("/feedback")
    public ApiResponse<String> feedback(@RequestBody Map<String, Object> request) {
        conversationService.feedback(request);
        return ApiResponse.ok("success");
    }

    @PostMapping("/cancel")
    public ApiResponse<?> cancel(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        String runId = String.valueOf(request.getOrDefault("run_id", ""));
        if (runId.isBlank()) {
            throw new com.chaitin.niuniuwiki.common.ApiException("run_id cannot be empty");
        }
        return ApiResponse.ok(Map.of("cancelled", chatStreamService.cancel(kbId, runId), "run_id", runId));
    }

    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        String runId = String.valueOf(request.getOrDefault("run_id", ""));
        if (runId.isBlank()) {
            throw new com.chaitin.niuniuwiki.common.ApiException("run_id cannot be empty");
        }
        return chatStreamService.resume(kbId, runId,
                String.valueOf(request.getOrDefault("nonce", "")), servletRequest.getRemoteAddr());
    }

    @PostMapping("/completions")
    public Object completions(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest
    ) {
        validateApiBot(kbId, authorization);
        String lastUserMessage = lastUserMessage(request);
        ChatService.ChatResult result = chatService.ask(
                kbId, 9, lastUserMessage, "", "", servletRequest.getRemoteAddr(), List.of(), List.of());
        String model = String.valueOf(request.getOrDefault("model", "niuniu-wiki"));
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = Instant.now().getEpochSecond();
        if (Boolean.TRUE.equals(request.get("stream"))) {
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

    private SseEmitter chat(
            String kbId,
            int appType,
            Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session,
            boolean requireCaptcha
    ) {
        accessService.authorize(kbId, session);
        if (requireCaptcha) {
            captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        }
        return chatStreamService.stream(
                kbId,
                appType,
                String.valueOf(request.getOrDefault("message", "")),
                String.valueOf(request.getOrDefault("conversation_id", "")),
                String.valueOf(request.getOrDefault("nonce", "")),
                servletRequest.getRemoteAddr(),
                stringList(request.get("image_paths")),
                attachments(request.get("attachments")));
    }

    private void validateApiBot(String kbId, String authorization) {
        accessService.settings(kbId);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.chaitin.niuniuwiki.common.ApiException("Authorization header is required");
        }
        String expected = apiBotSecret(kbId);
        if (expected.isBlank() || !expected.equals(authorization.substring(7))) {
            throw new com.chaitin.niuniuwiki.common.ApiException("Invalid Authorization key");
        }
    }

    private String apiBotSecret(String kbId) {
        List<Map<String, Object>> apps = store.query(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 9",
                store.rowMapper(), kbId);
        if (apps.isEmpty()) {
            return "";
        }
        Map<String, Object> settings = jsonMaps.jsonMap(apps.getFirst().get("settings"));
        Map<String, Object> api = jsonMaps.jsonMap(settings.get("openai_api_bot_settings"));
        if (!Boolean.TRUE.equals(api.get("is_enabled"))) {
            return "";
        }
        return String.valueOf(api.getOrDefault("secret_key", ""));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private List<ChatService.ChatAttachment> attachments(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<?, ?> attachment = (Map<?, ?>) item;
                    long size = attachment.get("size") instanceof Number number ? number.longValue() : 0L;
                    return new ChatService.ChatAttachment(
                            stringValue(attachment.get("name")),
                            stringValue(attachment.get("type")),
                            size,
                            stringValue(attachment.get("content")));
                })
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String lastUserMessage(Map<String, Object> request) {
        Object messagesValue = request.get("messages");
        if (!(messagesValue instanceof List<?> messages)) {
            throw new com.chaitin.niuniuwiki.common.ApiException("messages cannot be empty");
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
        throw new com.chaitin.niuniuwiki.common.ApiException("no user message found");
    }

}
