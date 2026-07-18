package com.chaitin.niuniuwiki.conversation;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理会话相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-04
 */
@RestController
@RequestMapping("/api/v1/conversation")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "app_id", required = false) String appId,
            @RequestParam(required = false) String subject,
            @RequestParam(name = "remote_ip", required = false) String remoteIp,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.list(kbId, appId, subject, remoteIp, page, Math.min(100, perPage)));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.detail(kbId, id, false));
    }

    @GetMapping("/message/list")
    public ApiResponse<?> messageList(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.feedbackMessages(kbId, page, Math.min(100, perPage)));
    }

    @GetMapping("/message/detail")
    public ApiResponse<Map<String, Object>> message(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.message(kbId, id));
    }
}
