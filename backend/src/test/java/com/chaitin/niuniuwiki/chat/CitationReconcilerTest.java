package com.chaitin.niuniuwiki.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证候选证据只在被回答明确引用后才进入用户可见的引用集合。
 *
 * @author 程序员牛肉
 * @since 2026-06-17
 */
class CitationReconcilerTest {

    private final List<Map<String, Object>> candidates = List.of(
            reference("文档一", "/node/1"),
            reference("文档二", "/node/2"),
            reference("文档三", "/node/3"),
            reference("文档四", "/node/4"),
            reference("文档五", "/node/5"));

    @Test
    void keepsOnlyActuallyCitedDocumentsAndRenumbersThem() {
        CitationReconciler.Result result = CitationReconciler.reconcile(
                "结论来自第二篇。[文档 2] 补充来自第五篇。[文档 5] 再次引用第二篇。[文档 2]",
                candidates);

        assertThat(result.answer()).isEqualTo(
                "结论来自第二篇。[文档 1] 补充来自第五篇。[文档 2] 再次引用第二篇。[文档 1]");
        assertThat(result.references()).extracting(reference -> reference.get("name"))
                .containsExactly("文档二", "文档五");
    }

    @Test
    void exposesNoReferencesWhenModelDidNotCiteCandidates() {
        CitationReconciler.Result result = CitationReconciler.reconcile("没有引用的回答。", candidates);

        assertThat(result.answer()).isEqualTo("没有引用的回答。");
        assertThat(result.references()).isEmpty();
    }

    @Test
    void removesHallucinatedCitationNumbers() {
        CitationReconciler.Result result = CitationReconciler.reconcile(
                "有效引用。[文档 1] 无效引用。[文档 99]", candidates);

        assertThat(result.answer()).isEqualTo("有效引用。[文档 1] 无效引用。");
        assertThat(result.references()).containsExactly(candidates.getFirst());
    }

    @Test
    void appendsLinksForFilteredReferencesOnly() {
        String answer = CitationReconciler.appendReferenceBlock(
                "结论。[文档 1]", List.of(candidates.get(1)));

        assertThat(answer).contains("> [1]. [文档二](/node/2)");
        assertThat(answer).doesNotContain("文档一");
    }

    private static Map<String, Object> reference(String name, String url) {
        return Map.of("name", name, "url", url);
    }
}
