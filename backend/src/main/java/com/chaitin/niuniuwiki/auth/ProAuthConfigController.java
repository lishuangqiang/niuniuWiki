package com.chaitin.niuniuwiki.auth;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 兼容既有前端契约的第三方认证配置接口，不再附加版本限制。
 *
 * @author 程序员牛肉
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/api/pro/v1/auth")
public class ProAuthConfigController {

    private final AuthConfigService service;

    public ProAuthConfigController(AuthConfigService service) {
        this.service = service;
    }

    @GetMapping("/get")
    public ApiResponse<?> get(
            @RequestParam("kb_id") String kbId,
            @RequestParam("source_type") String sourceType
    ) {
        return ApiResponse.ok(service.get(kbId, sourceType));
    }

    @PostMapping("/set")
    public ApiResponse<Void> set(@RequestBody Map<String, Object> request) {
        service.set(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam("kb_id") String kbId, @RequestParam long id) {
        service.delete(kbId, id);
        return ApiResponse.ok(null);
    }
}
