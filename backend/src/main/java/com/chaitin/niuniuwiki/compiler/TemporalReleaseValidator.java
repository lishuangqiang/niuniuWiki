package com.chaitin.niuniuwiki.compiler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计算两个知识来源快照之间的语义变更，并执行发布前完整性和异常删除门禁。
 *
 * @author 程序员牛肉
 * @since 2026-06-08
 */
public final class TemporalReleaseValidator {

    private static final double MAX_DELETION_RATIO = 0.35d;
    private static final int MIN_SOURCES_FOR_DELETION_GUARD = 5;
    private static final int MIN_DELETIONS_TO_BLOCK = 3;

    private TemporalReleaseValidator() {
    }

    public static List<SourceChange> diff(
            Map<String, SourceSnapshot> previous,
            Map<String, SourceSnapshot> current
    ) {
        List<SourceChange> changes = new ArrayList<>();
        Set<String> nodeIds = new LinkedHashSet<>();
        nodeIds.addAll(previous.keySet());
        nodeIds.addAll(current.keySet());
        for (String nodeId : nodeIds) {
            SourceSnapshot before = previous.get(nodeId);
            SourceSnapshot after = current.get(nodeId);
            if (before == null) {
                changes.add(new SourceChange(nodeId, "ADDED", null, after));
                continue;
            }
            if (after == null) {
                changes.add(new SourceChange(nodeId, "DELETED", before, null));
                continue;
            }
            if (!before.permissionHash().equals(after.permissionHash())) {
                changes.add(new SourceChange(nodeId, "PERMISSION_CHANGED", before, after));
            }
            if (!before.name().equals(after.name())) {
                changes.add(new SourceChange(nodeId, "RENAMED", before, after));
            }
            if (!before.navId().equals(after.navId()) || !before.parentId().equals(after.parentId())) {
                changes.add(new SourceChange(nodeId, "MOVED", before, after));
            }
            if (!before.contentHash().equals(after.contentHash())
                    || !before.releaseId().equals(after.releaseId())) {
                changes.add(new SourceChange(nodeId, "UPDATED", before, after));
            }
        }
        return List.copyOf(changes);
    }

    public static ValidationReport validate(
            int previousSourceCount,
            int currentSourceCount,
            int artifactCount,
            int indexDocumentCount,
            int lintErrors,
            String artifactManifest,
            String indexManifest,
            List<SourceChange> changes
    ) {
        List<ValidationCheck> checks = new ArrayList<>();
        boolean indexComplete = artifactCount > 0 && artifactCount == indexDocumentCount;
        checks.add(new ValidationCheck(
                "INDEX_COMPLETENESS",
                indexComplete ? "INFO" : "ERROR",
                indexComplete ? "PASSED" : "FAILED",
                indexComplete ? "影子索引文档数与知识产物一致" : "影子索引文档数与知识产物不一致",
                Map.of("artifact_count", artifactCount, "index_document_count", indexDocumentCount)));

        boolean manifestMatches = artifactManifest != null && !artifactManifest.isBlank()
                && artifactManifest.equals(indexManifest);
        checks.add(new ValidationCheck(
                "MANIFEST_INTEGRITY",
                manifestMatches ? "INFO" : "ERROR",
                manifestMatches ? "PASSED" : "FAILED",
                manifestMatches ? "影子索引清单校验通过" : "影子索引清单与知识版本不一致",
                Map.of("manifest_matches", manifestMatches)));

        checks.add(new ValidationCheck(
                "KNOWLEDGE_LINT",
                lintErrors == 0 ? "INFO" : "ERROR",
                lintErrors == 0 ? "PASSED" : "FAILED",
                lintErrors == 0 ? "知识质量规则检查通过" : "存在阻断发布的知识质量错误",
                Map.of("error_count", lintErrors)));

        long deleted = changes.stream().filter(change -> "DELETED".equals(change.type())).count();
        double deletionRatio = previousSourceCount == 0 ? 0d : (double) deleted / previousSourceCount;
        boolean deletionBlocked = previousSourceCount >= MIN_SOURCES_FOR_DELETION_GUARD
                && deleted >= MIN_DELETIONS_TO_BLOCK
                && deletionRatio > MAX_DELETION_RATIO;
        checks.add(new ValidationCheck(
                "MASS_DELETION_GUARD",
                deletionBlocked ? "ERROR" : "INFO",
                deletionBlocked ? "FAILED" : "PASSED",
                deletionBlocked ? "检测到异常规模的来源删除，已阻断版本发布" : "来源删除比例处于安全范围",
                Map.of(
                        "previous_source_count", previousSourceCount,
                        "current_source_count", currentSourceCount,
                        "deleted_count", deleted,
                        "deletion_ratio", deletionRatio,
                        "threshold", MAX_DELETION_RATIO)));

        long permissionChanges = changes.stream()
                .filter(change -> "PERMISSION_CHANGED".equals(change.type())).count();
        checks.add(new ValidationCheck(
                "PERMISSION_DRIFT",
                permissionChanges > 0 ? "WARNING" : "INFO",
                "PASSED",
                permissionChanges > 0 ? "检测到权限变化；内容版本发布不会覆盖当前权限" : "来源权限未发生变化",
                Map.of("changed_count", permissionChanges)));

        boolean blocked = checks.stream().anyMatch(check -> "ERROR".equals(check.severity())
                && "FAILED".equals(check.status()));
        return new ValidationReport(!blocked, checks, deleted, permissionChanges, deletionRatio);
    }

    public record SourceSnapshot(
            String nodeId,
            String releaseId,
            String sourceVersion,
            String name,
            String navId,
            String parentId,
            String contentHash,
            String permissionHash,
            Map<String, Object> permissions
    ) {
    }

    public record SourceChange(
            String nodeId,
            String type,
            SourceSnapshot before,
            SourceSnapshot after
    ) {
    }

    public record ValidationCheck(
            String code,
            String severity,
            String status,
            String message,
            Map<String, Object> metrics
    ) {
    }

    public record ValidationReport(
            boolean publishable,
            List<ValidationCheck> checks,
            long deletedCount,
            long permissionChangeCount,
            double deletionRatio
    ) {
    }
}
