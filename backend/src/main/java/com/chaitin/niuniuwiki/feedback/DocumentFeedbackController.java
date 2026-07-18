package com.chaitin.niuniuwiki.feedback;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端文档反馈分页和批量删除接口。
 *
 * @author 程序员牛肉
 * @since 2026-04-16
 */
@RestController
@RequestMapping("/api/pro/v1/document")
public class DocumentFeedbackController {

    private final DocumentFeedbackService service;

    public DocumentFeedbackController(DocumentFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.list(kbId, Math.max(1, page), Math.max(1, perPage)));
    }

    @DeleteMapping("/feedback")
    public ApiResponse<Void> delete(@RequestParam List<String> ids) {
        service.delete(ids);
        return ApiResponse.ok(null);
    }
}
