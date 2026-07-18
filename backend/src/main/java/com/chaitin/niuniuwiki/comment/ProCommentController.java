package com.chaitin.niuniuwiki.comment;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供评论批量审核接口。
 *
 * @author 程序员牛肉
 * @since 2026-05-30
 */
@RestController
@RequestMapping("/api/pro/v1")
public class ProCommentController {

    private final CommentService service;

    public ProCommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping("/comment_moderate")
    public ApiResponse<Void> moderate(@RequestBody Map<String, Object> request) {
        List<String> ids = request.get("ids") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        int status = request.get("status") instanceof Number number
                ? number.intValue() : Integer.parseInt(String.valueOf(request.getOrDefault("status", 0)));
        service.moderate(ids, status);
        return ApiResponse.ok(null);
    }
}
