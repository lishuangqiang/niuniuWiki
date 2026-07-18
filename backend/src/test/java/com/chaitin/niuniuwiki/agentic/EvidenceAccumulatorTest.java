package com.chaitin.niuniuwiki.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Evidence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证多路检索证据的内容去重、查询来源合并与页面级聚合。
 *
 * @author 程序员牛肉
 * @since 2026-06-18
 */
class EvidenceAccumulatorTest {

    @Test
    void mergesDuplicateChunksAndPreservesQueryProvenance() {
        EvidenceAccumulator accumulator = new EvidenceAccumulator();

        int first = accumulator.addAll(List.of(evidence("a:1", "node-a", "query-a", "same content", 0.9)));
        int second = accumulator.addAll(List.of(evidence("a:2", "node-a", "query-b", "same   content", 0.8)));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(accumulator.size()).isEqualTo(1);
        assertThat(accumulator.references(8)).singleElement().satisfies(reference ->
                assertThat(reference.get("queries")).isEqualTo(List.of("query-a", "query-b")));
    }

    @Test
    void returnsOneReferencePerNode() {
        EvidenceAccumulator accumulator = new EvidenceAccumulator();
        accumulator.addAll(List.of(
                evidence("a:1", "node-a", "q", "first chunk", 0.9),
                evidence("a:2", "node-a", "q", "second chunk", 0.7),
                evidence("b:1", "node-b", "q", "third chunk", 0.8)));

        assertThat(accumulator.references(8)).hasSize(2);
    }

    private static Evidence evidence(
            String key,
            String nodeId,
            String query,
            String content,
            double score
    ) {
        return new Evidence(key, nodeId, "doc-" + nodeId, "title", "summary", content,
                "/node/" + nodeId, "", score, query, 1, Map.of());
    }
}
