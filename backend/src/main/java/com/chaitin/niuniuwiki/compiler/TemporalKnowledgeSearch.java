package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.retrieval.Evidence;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 只从当前原子激活的影子索引检索时序知识，并把结果还原到原始文档引用。
 *
 * @author 程序员牛肉
 * @since 2026-05-22
 */
@Component
public class TemporalKnowledgeSearch {

    private final JdbcMaps store;

    public TemporalKnowledgeSearch(JdbcMaps store) {
        this.store = store;
    }

    public List<Evidence> search(
            String kbId,
            String query,
            int hop,
            KnowledgeAccessScope accessScope,
            CancellationSignal cancellationSignal
    ) {
        cancellationSignal.check();
        String keyword = query == null ? "" : query.strip();
        if (keyword.isBlank()) {
            return List.of();
        }
        String pattern = "%" + keyword.replaceAll("\\s+", "%") + "%";
        List<Map<String, Object>> rows = store.query("""
                SELECT doc.artifact_id, doc.identity_key, doc.title, doc.summary, doc.content,
                       doc.source_node_ids[1] AS node_id,
                       doc.source_release_ids[1] AS node_release_id,
                       nr.doc_id, nr.name AS source_name, nr.meta->>'emoji' AS emoji,
                       v.id AS knowledge_version_id, v.version_no,
                       ts_rank_cd(doc.search_vector, plainto_tsquery('simple', ?)) AS rank
                  FROM knowledge_compiler_states state
                  JOIN knowledge_shadow_indexes idx
                    ON idx.id = state.active_index_id AND idx.status = 'ACTIVE'
                  JOIN knowledge_index_documents doc ON doc.index_id = idx.id
                  JOIN knowledge_versions v ON v.id = doc.version_id
                  JOIN knowledge_artifacts artifact
                    ON artifact.id = doc.artifact_id AND artifact.status = 'EFFECTIVE'
                  LEFT JOIN node_releases nr ON nr.id = doc.source_release_ids[1]
                  LEFT JOIN nodes n ON n.id = doc.source_node_ids[1]
                 WHERE state.kb_id = ?
                   AND (COALESCE(n.permissions->>'answerable', 'open') = 'open'
                        OR (n.permissions->>'answerable' = 'partial' AND EXISTS (
                            SELECT 1 FROM node_auth_groups nag
                             WHERE nag.node_id = doc.source_node_ids[1] AND nag.perm = 'answerable'
                               AND nag.auth_group_id = ANY(?::int[]))))
                   AND (doc.search_vector @@ plainto_tsquery('simple', ?)
                        OR doc.title ILIKE ? OR doc.summary ILIKE ? OR doc.content ILIKE ?)
                 ORDER BY rank DESC, artifact.confidence DESC, doc.title
                 LIMIT 6
                """, store.rowMapper(), keyword, kbId, accessScope.postgresGroupArray(),
                keyword, pattern, pattern, pattern);
        cancellationSignal.check();
        List<Evidence> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String content = value(row.get("content"));
            String nodeId = value(row.get("node_id"));
            String releaseId = value(row.get("node_release_id"));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "temporal_shadow_index");
            metadata.put("rank", index + 1);
            metadata.put("node_release_id", releaseId);
            metadata.put("knowledge_version_id", value(row.get("knowledge_version_id")));
            metadata.put("knowledge_version", row.getOrDefault("version_no", 0));
            metadata.put("identity_key", value(row.get("identity_key")));
            result.add(new Evidence(
                    "temporal:" + row.get("artifact_id") + ":" + hash(content),
                    nodeId,
                    value(row.get("doc_id")),
                    value(row.get("source_name")).isBlank()
                            ? value(row.get("title")) : value(row.get("source_name")),
                    value(row.get("summary")),
                    content,
                    "/node/" + nodeId + (releaseId.isBlank() ? "" : "?release_id=" + releaseId),
                    value(row.get("emoji")),
                    number(row.get("rank"), 1d / (index + 1)),
                    query,
                    hop,
                    metadata));
        }
        return result;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < Math.min(8, digest.length); index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (Exception exception) {
            return Integer.toHexString(value(value).hashCode());
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
