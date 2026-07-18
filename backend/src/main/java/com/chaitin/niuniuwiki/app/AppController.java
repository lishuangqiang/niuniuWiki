package com.chaitin.niuniuwiki.app;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理应用配置相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-14
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppController {

    private final AppService service;

    public AppController(AppService service) {
        this.service = service;
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam("kb_id") String kbId, @RequestParam int type) {
        return ApiResponse.ok(service.detail(kbId, type));
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestParam String id, @RequestBody Map<String, Object> request) {
        service.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        service.delete(kbId, id);
        return ApiResponse.ok(null);
    }
}
