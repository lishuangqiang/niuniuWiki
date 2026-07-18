package com.chaitin.niuniuwiki.comment;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理评论相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api/v1/comment")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.adminList(kbId, status, page, Math.min(100, perPage)));
    }

    @DeleteMapping("/list")
    public ApiResponse<Void> delete(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "ids[]") List<String> ids
    ) {
        service.delete(kbId, ids);
        return ApiResponse.ok(null);
    }
}
