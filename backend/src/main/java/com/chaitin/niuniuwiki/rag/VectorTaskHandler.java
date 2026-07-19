package com.chaitin.niuniuwiki.rag;

import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.compiler.KnowledgeCompilerService;
import com.chaitin.niuniuwiki.compiler.KnowledgeEventLedger;
import com.chaitin.niuniuwiki.common.JsonMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Component;

/**
 * 提供 NiuniuWiki 后端的RAG 与向量任务基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-05-03
 */
@Component
public class VectorTaskHandler {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final RagClient rag;
    private final ModelGateway modelGateway;
    private final KnowledgeCompilerService compilerService;
    private final KnowledgeEventLedger eventLedger;

    public VectorTaskHandler(
            JdbcMaps store,
            JsonMaps jsonMaps,
            RagClient rag,
            ModelGateway modelGateway,
            KnowledgeCompilerService compilerService,
            KnowledgeEventLedger eventLedger
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.rag = rag;
        this.modelGateway = modelGateway;
        this.compilerService = compilerService;
        this.eventLedger = eventLedger;
    }

    public void handle(Map<String, Object> task) {
        KnowledgeEventLedger.Claim claim = eventLedger.claim(task);
        if (!claim.process()) {
            return;
        }
        String action = value(task.get("action"));
        String kbId = value(task.get("kb_id"));
        try {
            switch (action) {
                case "upsert" -> upsert(kbId, value(task.get("node_release_id")));
                case "delete" -> rag.deleteDocuments(dataset(kbId), List.of(value(task.get("doc_id"))));
                case "update_group_ids" -> rag.updateDocumentGroups(
                        dataset(kbId), value(task.get("doc_id")), integerList(task.get("group_ids")));
                case "summary" -> summarize(kbId, value(task.get("node_id")));
                default -> throw new IllegalArgumentException("unsupported vector action: " + action);
            }
            eventLedger.complete(claim, "", "", "delete".equals(action));
        } catch (RuntimeException exception) {
            eventLedger.fail(claim, exception);
            throw exception;
        }
    }

    public void handleDocumentStatus(Map<String, Object> event) {
        String documentId = value(event.get("id"));
        List<String> nodeIds = store.query(
                "SELECT DISTINCT node_id FROM node_releases WHERE doc_id = ?",
                (rs, rowNum) -> rs.getString(1), documentId);
        Map<String, Object> ragInfo = Map.of(
                "status", value(event.get("status")),
                "message", value(event.get("message")),
                "synced_at", java.time.Instant.now().toString());
        nodeIds.forEach(nodeId -> store.update(
                "UPDATE nodes SET rag_info = ?::jsonb WHERE id = ?", jsonMaps.json(ragInfo), nodeId));
    }

    private void upsert(String kbId, String releaseId) {
        Map<String, Object> release = store.queryForObject(
                "SELECT nr.*, kb.dataset_id FROM node_releases nr "
                        + "JOIN knowledge_bases kb ON kb.id = nr.kb_id WHERE nr.id = ? AND nr.kb_id = ?",
                store.rowMapper(), releaseId, kbId);
        if (number(release.get("type")) == 1) {
            return;
        }
        List<Integer> groupIds = store.query(
                "SELECT auth_group_id FROM node_auth_groups WHERE node_id = ? AND perm = 'answerable'",
                (rs, rowNum) -> rs.getInt(1), release.get("node_id"));
        String documentId = rag.uploadDocument(
                value(release.get("dataset_id")), releaseId, value(release.get("doc_id")),
                value(release.get("name")), value(release.get("content")), groupIds);
        store.update("UPDATE node_releases SET doc_id = ? WHERE id = ?", documentId, releaseId);
        List<String> oldDocumentIds = store.query(
                "SELECT doc_id FROM node_releases WHERE node_id = ? AND id <> ? AND doc_id <> ''",
                (rs, rowNum) -> rs.getString(1), release.get("node_id"), releaseId);
        if (!oldDocumentIds.isEmpty()) {
            rag.deleteDocuments(value(release.get("dataset_id")), oldDocumentIds);
            store.update("UPDATE node_releases SET doc_id = '' WHERE node_id = ? AND id <> ?",
                    release.get("node_id"), releaseId);
        }
        compilerService.requestAutomaticCompile(kbId, value(release.get("node_id")), releaseId);
    }

    private void summarize(String kbId, String nodeId) {
        Map<String, Object> node = store.queryForObject(
                "SELECT type, name, content, meta, status FROM nodes WHERE id = ? AND kb_id = ?",
                store.rowMapper(), nodeId, kbId);
        if (number(node.get("type")) == 1) {
            return;
        }
        String summary = modelGateway.completeText(
                "你是文档摘要助手。请用不超过180个中文字符准确概括文档，只输出摘要。",
                "标题：" + value(node.get("name")) + "\n\n正文：" + value(node.get("content")));
        Map<String, Object> meta = jsonMaps.jsonMap(node.get("meta"));
        meta.put("summary", summary);
        int status = number(node.get("status"));
        store.update("UPDATE nodes SET meta = ?::jsonb, status = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                jsonMaps.json(meta), status == 2 ? 1 : status, nodeId, kbId);
    }

    private String dataset(String kbId) {
        return store.queryForObject("SELECT dataset_id FROM knowledge_bases WHERE id = ?", String.class, kbId);
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        list.forEach(item -> result.add(item instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(item))));
        return result;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
