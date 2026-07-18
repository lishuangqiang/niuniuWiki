package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Evidence;
import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.compiler.TemporalKnowledgeSearch;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import com.chaitin.niuniuwiki.rag.RagClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将 RAGLite 的向量片段解析为具备原文权限、页面链接和检索来源的标准化 Agent 证据。
 *
 * @author 程序员牛肉
 * @since 2026-06-29
 */
@Component
public class AgenticKnowledgeRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticKnowledgeRetriever.class);

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final RagClient ragClient;
    private final TemporalKnowledgeSearch temporalSearch;

    public AgenticKnowledgeRetriever(
            MyBatisStore store,
            JsonMaps jsonMaps,
            RagClient ragClient,
            TemporalKnowledgeSearch temporalSearch
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.ragClient = ragClient;
        this.temporalSearch = temporalSearch;
    }

    public List<Evidence> retrieve(
            String kbId,
            String query,
            int hop,
            CancellationSignal cancellationSignal
    ) {
        cancellationSignal.check();
        List<Evidence> temporal = temporalSearch.search(kbId, query, hop, cancellationSignal);
        try {
            String datasetId = store.queryForObject(
                    "SELECT dataset_id FROM knowledge_bases WHERE id = ?", String.class, kbId);
            List<Map<String, Object>> chunks = ragClient.retrieve(
                    datasetId, query, List.of(), List.of(), cancellationSignal);
            List<Evidence> result = fromChunks(kbId, query, hop, chunks);
            if (!result.isEmpty() || !temporal.isEmpty()) {
                List<Evidence> merged = new ArrayList<>(temporal);
                merged.addAll(result);
                return merged;
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (ApiException exception) {
            LOGGER.warn("Agentic RAG vector retrieval failed for knowledge base {}: {}", kbId, exception.getMessage());
        }
        cancellationSignal.check();
        List<Evidence> fallback = databaseFallback(kbId, query, hop);
        if (temporal.isEmpty()) {
            return fallback;
        }
        List<Evidence> merged = new ArrayList<>(temporal);
        merged.addAll(fallback);
        return merged;
    }

    private List<Evidence> fromChunks(
            String kbId,
            String query,
            int hop,
            List<Map<String, Object>> chunks
    ) {
        List<String> documentIds = chunks.stream()
                .map(chunk -> value(chunk.get("document_id")))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> releases = store.query("""
                SELECT DISTINCT ON (nr.doc_id)
                       nr.doc_id, nr.id AS node_release_id, links.release_id AS source_version,
                       nr.node_id, nr.name, nr.meta->>'summary' AS summary,
                       nr.meta->>'emoji' AS emoji, links.nav_id, nav.name AS nav_name
                  FROM node_releases nr
                  JOIN kb_release_node_releases links ON links.node_release_id = nr.id
                  JOIN kb_releases kr ON kr.id = links.release_id
                  LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE nr.doc_id = ANY(?::text[])
                   AND kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND COALESCE(n.permissions->>'answerable', 'open') <> 'closed'
                 ORDER BY nr.doc_id, nr.updated_at DESC
                """, store.rowMapper(), postgresTextArray(documentIds), kbId);
        Map<String, Map<String, Object>> byDocument = new HashMap<>();
        releases.forEach(release -> byDocument.put(value(release.get("doc_id")), release));
        String baseUrl = baseUrl(kbId);
        List<Evidence> result = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            Map<String, Object> chunk = chunks.get(index);
            String documentId = value(chunk.get("document_id"));
            Map<String, Object> release = byDocument.get(documentId);
            if (release == null) {
                continue;
            }
            String content = value(chunk.get("content"));
            String chunkId = firstNonBlank(chunk.get("chunk_id"), chunk.get("id"), hash(content));
            double score = score(chunk, index);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("rank", index + 1);
            metadata.put("nav_id", value(release.get("nav_id")));
            metadata.put("nav_name", value(release.get("nav_name")));
            metadata.put("vector_score", score);
            metadata.put("node_release_id", value(release.get("node_release_id")));
            metadata.put("source_version", value(release.get("source_version")));
            metadata.put("knowledge_version_id", activeKnowledgeVersion(kbId));
            result.add(new Evidence(
                    documentId + ":" + chunkId,
                    value(release.get("node_id")),
                    documentId,
                    value(release.get("name")),
                    value(release.get("summary")),
                    content,
                    baseUrl + "/node/" + release.get("node_id"),
                    value(release.get("emoji")),
                    score,
                    query,
                    hop,
                    metadata));
        }
        return result;
    }

    private List<Evidence> databaseFallback(String kbId, String query, int hop) {
        String normalized = value(query).strip().replaceAll("\\s+", "%");
        String pattern = "%" + normalized + "%";
        List<Map<String, Object>> nodes = store.query("""
                SELECT nr.doc_id, nr.id AS node_release_id, links.release_id AS source_version,
                       nr.node_id, nr.name, nr.content,
                       nr.meta->>'summary' AS summary, nr.meta->>'emoji' AS emoji,
                       links.nav_id, nav.name AS nav_name
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND nr.type = 2 AND (nr.name ILIKE ? OR nr.content ILIKE ?)
                   AND COALESCE(n.permissions->>'answerable', 'open') <> 'closed'
                 ORDER BY CASE WHEN nr.name ILIKE ? THEN 0 ELSE 1 END, nr.updated_at DESC
                 LIMIT 10
                """, store.rowMapper(), kbId, pattern, pattern, pattern);
        String baseUrl = baseUrl(kbId);
        List<Evidence> result = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String content = value(node.get("content"));
            String documentId = value(node.get("doc_id"));
            result.add(new Evidence(
                    documentId + ":fallback:" + hash(content),
                    value(node.get("node_id")), documentId, value(node.get("name")),
                    value(node.get("summary")), content,
                    baseUrl + "/node/" + node.get("node_id"), value(node.get("emoji")),
                    1d / (index + 1), query, hop,
                    Map.of(
                            "rank", index + 1,
                            "source", "postgres_fallback",
                            "node_release_id", value(node.get("node_release_id")),
                            "source_version", value(node.get("source_version")),
                            "knowledge_version_id", activeKnowledgeVersion(kbId))));
        }
        return result;
    }

    private String baseUrl(String kbId) {
        Map<String, Object> kb = store.queryForObject(
                "SELECT access_settings FROM knowledge_bases WHERE id = ?", store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(kb.get("access_settings"));
        String base = value(settings.get("base_url")).replaceAll("/+$", "");
        if (!base.isBlank()) {
            return base;
        }
        List<?> hosts = settings.get("hosts") instanceof List<?> list ? list : List.of();
        List<?> sslPorts = settings.get("ssl_ports") instanceof List<?> list ? list : List.of();
        List<?> ports = settings.get("ports") instanceof List<?> list ? list : List.of();
        if (hosts.isEmpty()) {
            return "";
        }
        String host = value(hosts.getFirst());
        if (!sslPorts.isEmpty()) {
            int port = ((Number) sslPorts.getFirst()).intValue();
            return "https://" + host + (port == 443 ? "" : ":" + port);
        }
        if (!ports.isEmpty()) {
            int port = ((Number) ports.getFirst()).intValue();
            return "http://" + host + (port == 80 ? "" : ":" + port);
        }
        return "http://" + host;
    }

    private String activeKnowledgeVersion(String kbId) {
        return store.query(
                "SELECT active_version_id FROM knowledge_compiler_states WHERE kb_id = ?",
                (row, rowNumber) -> row.getString(1), kbId).stream().findFirst().orElse("");
    }

    private static double score(Map<String, Object> chunk, int index) {
        for (String key : List.of("rerank_score", "score", "similarity")) {
            Object value = chunk.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return 1d / (index + 1);
    }

    private static String firstNonBlank(Object... values) {
        for (Object item : values) {
            String safe = value(item);
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "chunk";
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < Math.min(8, bytes.length); index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception exception) {
            return Integer.toHexString(value(value).hashCode());
        }
    }

    private static String postgresTextArray(List<String> values) {
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append('"').append(values.get(index)
                    .replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return result.append('}').toString();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
