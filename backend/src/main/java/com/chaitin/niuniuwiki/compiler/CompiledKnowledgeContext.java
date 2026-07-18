package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 为问答链路读取当前已发布知识版本中的结构化上下文。
 *
 * <p>只按本次 RAG 已命中的原始文档选择派生知识，因此不会扩大用户权限边界，
 * 也不会绕过原始文档引用和可见性判断。</p>
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
@Component
public class CompiledKnowledgeContext {

    private static final int MAX_CHARACTERS = 18_000;

    private final MyBatisStore store;

    public CompiledKnowledgeContext(MyBatisStore store) {
        this.store = store;
    }

    public String forSources(String kbId, List<String> nodeIds) {
        List<String> safeIds = nodeIds == null ? List.of() : nodeIds.stream()
                .filter(java.util.Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (safeIds.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> artifacts = store.query("""
                SELECT a.type, a.title, a.summary, a.content
                  FROM knowledge_compiler_states s
                  JOIN knowledge_shadow_indexes idx
                    ON idx.id = s.active_index_id AND idx.status = 'ACTIVE'
                  JOIN knowledge_index_documents doc ON doc.index_id = idx.id
                  JOIN knowledge_artifacts a ON a.id = doc.artifact_id
                 WHERE s.kb_id = ? AND a.artifact_key <> '__index__'
                   AND a.status = 'EFFECTIVE' AND doc.source_node_ids && ?::text[]
                 ORDER BY a.confidence DESC, a.title
                 LIMIT 8
                """, store.rowMapper(), kbId, KnowledgeCompilerService.postgresTextArray(safeIds));
        StringBuilder result = new StringBuilder();
        for (Map<String, Object> artifact : artifacts) {
            String block = "\n[已编译知识：" + value(artifact.get("title")) + "]\n"
                    + value(artifact.get("summary")) + "\n"
                    + value(artifact.get("content")) + "\n";
            int remaining = MAX_CHARACTERS - result.length();
            if (remaining <= 0) {
                break;
            }
            result.append(block, 0, Math.min(block.length(), remaining));
        }
        return result.toString();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
