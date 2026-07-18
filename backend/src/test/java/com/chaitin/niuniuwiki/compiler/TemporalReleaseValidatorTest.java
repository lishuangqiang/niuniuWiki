package com.chaitin.niuniuwiki.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证时序知识发布的变更识别与异常删除门禁。
 *
 * @author 程序员牛肉
 * @since 2026-06-19
 */
class TemporalReleaseValidatorTest {

    @Test
    void detectsRenameMovePermissionAndContentChangesIndependently() {
        TemporalReleaseValidator.SourceSnapshot before = source(
                "n1", "r1", "旧标题", "nav-a", "", "content-a", "perm-a");
        TemporalReleaseValidator.SourceSnapshot after = source(
                "n1", "r2", "新标题", "nav-b", "folder", "content-b", "perm-b");

        List<String> types = TemporalReleaseValidator.diff(
                        Map.of("n1", before), Map.of("n1", after)).stream()
                .map(TemporalReleaseValidator.SourceChange::type)
                .toList();

        assertThat(types).containsExactly("PERMISSION_CHANGED", "RENAMED", "MOVED", "UPDATED");
    }

    @Test
    void blocksLargeAccidentalDeletionButAllowsSmallKnowledgeBaseChange() {
        Map<String, TemporalReleaseValidator.SourceSnapshot> previous = new LinkedHashMap<>();
        Map<String, TemporalReleaseValidator.SourceSnapshot> current = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            TemporalReleaseValidator.SourceSnapshot source = source(
                    "n" + index, "r" + index, "标题" + index, "nav", "", "c" + index, "p");
            previous.put(source.nodeId(), source);
            if (index < 4) {
                current.put(source.nodeId(), source);
            }
        }

        TemporalReleaseValidator.ValidationReport report = TemporalReleaseValidator.validate(
                10, 4, 4, 4, 0, "same", "same",
                TemporalReleaseValidator.diff(previous, current));
        TemporalReleaseValidator.ValidationReport small = TemporalReleaseValidator.validate(
                2, 1, 1, 1, 0, "same", "same",
                List.of(new TemporalReleaseValidator.SourceChange("n2", "DELETED", previous.get("n2"), null)));

        assertThat(report.publishable()).isFalse();
        assertThat(report.checks()).anyMatch(check -> check.code().equals("MASS_DELETION_GUARD")
                && check.status().equals("FAILED"));
        assertThat(small.publishable()).isTrue();
    }

    private static TemporalReleaseValidator.SourceSnapshot source(
            String nodeId,
            String releaseId,
            String name,
            String navId,
            String parentId,
            String contentHash,
            String permissionHash
    ) {
        return new TemporalReleaseValidator.SourceSnapshot(
                nodeId, releaseId, releaseId, name, navId, parentId,
                contentHash, permissionHash, Map.of("answerable", "open"));
    }
}
