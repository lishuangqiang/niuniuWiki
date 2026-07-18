package com.chaitin.niuniuwiki.apitoken;

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
 * 暴露完整可用的知识库 API Token 管理接口。
 *
 * @author 程序员牛肉
 * @since 2026-06-20
 */
@RestController
@RequestMapping("/api/pro/v1/token")
public class ApiTokenController {

    private final ApiTokenService service;

    public ApiTokenController(ApiTokenService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ApiResponse<?> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/list")
    public ApiResponse<?> list(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.list(kbId));
    }

    @PatchMapping("/update")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        service.delete(kbId, id);
        return ApiResponse.ok(null);
    }
}
