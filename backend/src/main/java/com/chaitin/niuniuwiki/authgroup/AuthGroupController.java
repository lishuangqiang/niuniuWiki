package com.chaitin.niuniuwiki.authgroup;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供访客用户组的增删改查、移动和组织同步接口。
 *
 * @author 程序员牛肉
 * @since 2026-06-14
 */
@RestController
@RequestMapping("/api/pro/v1/auth/group")
public class AuthGroupController {

    private final AuthGroupService service;

    public AuthGroupController(AuthGroupService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ApiResponse<?> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.create(request));
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam("kb_id") String kbId, @RequestParam int id) {
        service.delete(kbId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam("kb_id") String kbId, @RequestParam int id) {
        return ApiResponse.ok(service.detail(kbId, id));
    }

    @GetMapping("/list")
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.list(kbId, Math.max(1, page), Math.min(10000, Math.max(1, perPage))));
    }

    @GetMapping("/tree")
    public ApiResponse<?> tree(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.tree(kbId));
    }

    @PatchMapping("/update")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/move")
    public ApiResponse<Void> move(@RequestBody Map<String, Object> request) {
        service.move(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sync")
    public ApiResponse<?> sync(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.sync(
                String.valueOf(request.getOrDefault("kb_id", "")),
                String.valueOf(request.getOrDefault("source_type", ""))));
    }
}
