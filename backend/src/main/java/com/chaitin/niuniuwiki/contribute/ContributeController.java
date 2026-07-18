package com.chaitin.niuniuwiki.contribute;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端文档贡献查询和审核接口。
 *
 * @author 程序员牛肉
 * @since 2026-06-29
 */
@RestController
@RequestMapping("/api/pro/v1/contribute")
public class ContributeController {

    private final ContributeService service;

    public ContributeController(ContributeService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "node_name", required = false) String nodeName,
            @RequestParam(name = "auth_name", required = false) String authName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ApiResponse.ok(service.list(
                kbId, status, nodeName, authName, Math.max(1, page), Math.max(1, perPage)));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.detail(kbId, id));
    }

    @PostMapping("/audit")
    public ApiResponse<?> audit(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.audit(request));
    }
}
