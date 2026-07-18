package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露 AI 知识编译、版本、产物和质量问题的管理端接口。
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge-compiler")
public class KnowledgeCompilerController {

    private final KnowledgeCompilerService service;
    private final KnowledgeReconciliationService reconciliationService;

    public KnowledgeCompilerController(
            KnowledgeCompilerService service,
            KnowledgeReconciliationService reconciliationService
    ) {
        this.service = service;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/compile")
    public ApiResponse<?> compile(@Valid @RequestBody KnowledgeCompilerDtos.CompileRequest request) {
        return ApiResponse.ok(Map.of("run_id", service.requestCompile(request)));
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.overview(kbId));
    }

    @GetMapping("/runs")
    public ApiResponse<?> runs(
            @RequestParam("kb_id") String kbId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage
    ) {
        return ApiResponse.ok(service.runs(kbId, page, perPage));
    }

    @GetMapping("/versions")
    public ApiResponse<?> versions(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.versions(kbId));
    }

    @GetMapping("/artifacts")
    public ApiResponse<?> artifacts(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "version_id", required = false) String versionId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage
    ) {
        return ApiResponse.ok(service.artifacts(kbId, versionId, search, page, perPage));
    }

    @GetMapping("/artifact/detail")
    public ApiResponse<?> artifact(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.artifact(kbId, id));
    }

    @GetMapping("/issues")
    public ApiResponse<?> issues(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "version_id", required = false) String versionId
    ) {
        return ApiResponse.ok(service.issues(kbId, versionId));
    }

    @GetMapping("/release-diagnostics")
    public ApiResponse<?> releaseDiagnostics(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "version_id", required = false) String versionId
    ) {
        return ApiResponse.ok(service.releaseDiagnostics(kbId, versionId));
    }

    @GetMapping("/reconciliation-runs")
    public ApiResponse<?> reconciliationRuns(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(reconciliationService.runs(kbId));
    }

    @PostMapping("/reconcile")
    public ApiResponse<?> reconcile(@Valid @RequestBody KnowledgeCompilerDtos.ReconcileRequest request) {
        return ApiResponse.ok(reconciliationService.reconcileNow(request.kbId()));
    }

    @PostMapping("/rollback")
    public ApiResponse<Void> rollback(@Valid @RequestBody KnowledgeCompilerDtos.RollbackRequest request) {
        service.rollback(request);
        return ApiResponse.ok(null);
    }
}
