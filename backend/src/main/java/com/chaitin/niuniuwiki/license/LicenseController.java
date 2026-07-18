package com.chaitin.niuniuwiki.license;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供兼容旧客户端的全功能状态查询接口。
 *
 * @author 程序员牛肉
 * @since 2026-06-28
 */
@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    @GetMapping
    public ApiResponse<Map<String, Object>> currentLicense() {
        return ApiResponse.ok(Map.of(
                "edition", 2,
                "started_at", 0,
                "expired_at", 0,
                "state", 1));
    }
}
