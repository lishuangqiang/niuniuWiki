package com.chaitin.niuniuwiki.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KnowledgeCompilerSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesFencedKnowledgeArtifactsAndBoundsConfidence() throws Exception {
        String response = """
                ```json
                {"artifacts":[{"key":"Release Policy","type":"decision","title":"发布策略",
                "summary":"成功后原子切换","content":"失败时保留旧版本。",
                "facts":[{"subject":"编译器","predicate":"发布方式","object":"原子切换","quote":"成功后切换"}],
                "entities":[],"confidence":1.4}]}
                ```
                """;

        var artifacts = KnowledgeCompilerSupport.parse(objectMapper, response);

        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.getFirst().key()).isEqualTo("release-policy");
        assertThat(artifacts.getFirst().type()).isEqualTo("decision");
        assertThat(artifacts.getFirst().confidence()).isEqualTo(1d);
        assertThat(artifacts.getFirst().facts()).hasSize(1);
    }

    @Test
    void splitsLongDocumentsWithoutLosingContent() {
        String content = "第一段\n" + "知识证据".repeat(9_000) + "\n最后一段";

        var chunks = KnowledgeCompilerSupport.chunks(content);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks).replace("\n", ""))
                .isEqualTo(content.replace("\n", ""));
    }

    @Test
    void fallbackKeepsOriginalEvidence() {
        var fallback = KnowledgeCompilerSupport.fallback("原始文档", "", "不可丢失的原始证据");

        assertThat(fallback.type()).isEqualTo("reference");
        assertThat(fallback.content()).isEqualTo("不可丢失的原始证据");
        assertThat(fallback.summary()).isNotBlank();
        assertThat(fallback.confidence()).isEqualTo(0.5d);
    }
}
