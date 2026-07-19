package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.captcha.CaptchaService;
import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.conversation.ConversationService;
import com.chaitin.niuniuwiki.share.ShareAccessService;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
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
    private final ApiBotChatService apiBotChatService;

    public ChatController(
            ChatService chatService,
            ChatStreamService chatStreamService,
            ConversationService conversationService,
            ShareAccessService accessService,
            CaptchaService captchaService,
            ApiBotChatService apiBotChatService
    ) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
        this.conversationService = conversationService;
        this.accessService = accessService;
        this.captchaService = captchaService;
        this.apiBotChatService = apiBotChatService;
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
        KnowledgeAccessScope accessScope = accessService.scope(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        return ApiResponse.ok(Map.of("node_result", chatService.search(
                kbId, String.valueOf(request.getOrDefault("message", "")), accessScope)));
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
        KnowledgeAccessScope accessScope = accessService.scope(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        String runId = String.valueOf(request.getOrDefault("run_id", ""));
        if (runId.isBlank()) {
            throw new com.chaitin.niuniuwiki.common.ApiException("run_id cannot be empty");
        }
        return chatStreamService.resume(kbId, runId,
                String.valueOf(request.getOrDefault("nonce", "")), servletRequest.getRemoteAddr(), accessScope);
    }

    @PostMapping("/completions")
    public Object completions(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest
    ) {
        return apiBotChatService.completions(kbId, authorization, request, servletRequest.getRemoteAddr());
    }

    private SseEmitter chat(
            String kbId,
            int appType,
            Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session,
            boolean requireCaptcha
    ) {
        KnowledgeAccessScope accessScope = accessService.scope(kbId, session);
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
                attachments(request.get("attachments")),
                accessScope);
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

}
