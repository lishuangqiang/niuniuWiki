package com.chaitin.niuniuwiki.knowledgebase;

import com.chaitin.niuniuwiki.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理知识库相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-13
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge_base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, String>> create(@Valid @RequestBody KnowledgeBaseDtos.CreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @GetMapping("/list")
    public ApiResponse<?> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam String id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/detail")
    public ApiResponse<Void> update(@Valid @RequestBody KnowledgeBaseDtos.UpdateRequest request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/detail")
    public ApiResponse<Void> delete(@RequestParam String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/release")
    public ApiResponse<?> release(@Valid @RequestBody KnowledgeBaseDtos.ReleaseRequest request) {
        return ApiResponse.ok(Map.of("id", service.createRelease(request)));
    }

    @GetMapping("/release/list")
    public ApiResponse<?> releases(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage
    ) {
        return ApiResponse.ok(service.releases(kbId, page, perPage));
    }

    @GetMapping("/user/list")
    public ApiResponse<?> users(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.users(kbId));
    }

    @PostMapping("/user/invite")
    public ApiResponse<Void> invite(@Valid @RequestBody KnowledgeBaseDtos.UserPermissionRequest request) {
        service.invite(request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/user/update")
    public ApiResponse<Void> updateUser(@Valid @RequestBody KnowledgeBaseDtos.UserPermissionRequest request) {
        service.updateUser(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/user/delete")
    public ApiResponse<Void> deleteUser(
            @RequestParam("kb_id") String kbId,
            @RequestParam("user_id") String userId
    ) {
        service.deleteUser(kbId, userId);
        return ApiResponse.ok(null);
    }
}
