package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 Agent 运行列表、完整执行轨迹和管理端终止能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-07
 */
@RestController
@RequestMapping("/api/v1/agentic-rag")
public class AgenticRagController {

    private final AgenticRagService service;
    private final AgentRunRegistry registry;
    private final AuthService authService;

    public AgenticRagController(
            AgenticRagService service,
            AgentRunRegistry registry,
            AuthService authService
    ) {
        this.service = service;
        this.registry = registry;
        this.authService = authService;
    }

    @GetMapping("/runs")
    public ApiResponse<?> runs(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        return ApiResponse.ok(service.list(kbId, limit));
    }

    @GetMapping("/run")
    public ApiResponse<?> run(
            @RequestParam("kb_id") String kbId,
            @RequestParam("run_id") String runId
    ) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        return ApiResponse.ok(service.detail(kbId, runId));
    }

    @PostMapping("/cancel")
    public ApiResponse<?> cancel(@RequestBody Map<String, Object> request) {
        String kbId = String.valueOf(request.getOrDefault("kb_id", ""));
        String runId = String.valueOf(request.getOrDefault("run_id", ""));
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        service.context(runId, kbId);
        boolean requested = registry.cancel(runId);
        return ApiResponse.ok(Map.of("run_id", runId, "cancelled", requested));
    }
}
