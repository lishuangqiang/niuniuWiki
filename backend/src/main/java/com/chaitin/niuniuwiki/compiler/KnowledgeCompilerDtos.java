package com.chaitin.niuniuwiki.compiler;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 定义 AI 知识编译器的管理端请求结构。
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
public final class KnowledgeCompilerDtos {

    private KnowledgeCompilerDtos() {
    }

    public record CompileRequest(
            @NotBlank String kbId,
            List<String> nodeIds,
            boolean forceFull
    ) {
    }

    public record RollbackRequest(
            @NotBlank String kbId,
            @NotBlank String versionId
    ) {
    }

    public record ReconcileRequest(
            @NotBlank String kbId
    ) {
    }
}
