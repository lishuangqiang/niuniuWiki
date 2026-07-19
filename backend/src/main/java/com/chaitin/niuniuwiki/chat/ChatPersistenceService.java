package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.UsageSnapshot;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RunRequest;
import com.chaitin.niuniuwiki.agentic.AgenticRagStore;
import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定义问答链路的短事务边界，模型与检索调用不会占用数据库事务。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class ChatPersistenceService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AgenticRagStore agenticRagStore;

    public ChatPersistenceService(JdbcMaps store, JsonMaps jsonMaps, AgenticRagStore agenticRagStore) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.agenticRagStore = agenticRagStore;
    }

    @Transactional
    public OpenTurn openTurn(
            String kbId,
            int appType,
            String message,
            String conversationId,
            String nonce,
            String remoteIp,
            List<String> imagePaths,
            List<ChatService.ChatAttachment> attachments,
            String runId,
            KnowledgeAccessScope accessScope
    ) {
        String appId = appId(kbId, appType);
        boolean newConversation = conversationId == null || conversationId.isBlank();
        String resolvedConversationId = newConversation ? UUID.randomUUID().toString() : conversationId;
        String conversationNonce = newConversation ? UUID.randomUUID().toString() : value(nonce);
        if (newConversation) {
            store.update(
                    "INSERT INTO conversations(id, nonce, kb_id, app_id, subject, remote_ip, info, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, now())",
                    resolvedConversationId, conversationNonce, kbId, appId, message, value(remoteIp));
        } else {
            Integer valid = store.queryForObject(
                    "SELECT count(*) FROM conversations WHERE id = ? AND kb_id = ? AND nonce = ?",
                    Integer.class, resolvedConversationId, kbId, nonce);
            if (valid == null || valid == 0) {
                throw new ApiException("invalid conversation nonce");
            }
        }

        String userMessageId = UUID.randomUUID().toString();
        Map<String, Object> userInfo = attachments.isEmpty()
                ? Map.of()
                : Map.of("attachments", attachments.stream()
                        .map(attachment -> Map.of(
                                "name", attachment.name(),
                                "size", attachment.size(),
                                "type", attachment.type()))
                        .toList());
        store.update(
                "INSERT INTO conversation_messages(id, conversation_id, app_id, kb_id, role, content, remote_ip, "
                        + "info, image_paths, created_at) "
                        + "VALUES (?, ?, ?, ?, 'user', ?, ?, ?::jsonb, ?::text[], now())",
                userMessageId, resolvedConversationId, appId, kbId, message, value(remoteIp),
                jsonMaps.json(userInfo), postgresTextArray(imagePaths));
        List<Map<String, Object>> history = store.query(
                "SELECT role, content FROM conversation_messages WHERE conversation_id = ? AND id <> ? "
                        + "ORDER BY created_at",
                store.rowMapper(), resolvedConversationId, userMessageId);
        agenticRagStore.createRun(new RunRequest(runId, kbId, resolvedConversationId,
                userMessageId, message, history, accessScope));
        return new OpenTurn(resolvedConversationId, conversationNonce, appId, userMessageId, history);
    }

    @Transactional
    public void saveAnswer(
            String kbId,
            String conversationId,
            String appId,
            String userMessageId,
            String assistantMessageId,
            String remoteIp,
            String answer,
            ModelGateway.Completion completion,
            Map<String, Object> info,
            List<Map<String, Object>> references,
            String runId,
            UsageSnapshot completedUsage
    ) {
        store.update(
                "INSERT INTO conversation_messages(id, conversation_id, app_id, kb_id, role, content, provider, "
                        + "model, remote_ip, info, parent_id, agent_run_id, created_at) "
                        + "VALUES (?, ?, ?, ?, 'assistant', ?, ?, ?, ?, ?::jsonb, ?, ?, now())",
                assistantMessageId, conversationId, appId, kbId, answer,
                completion.provider(), completion.model(), remoteIp, jsonMaps.json(info), userMessageId, runId);
        for (int index = 0; index < references.size(); index++) {
            Map<String, Object> reference = references.get(index);
            store.update("""
                    INSERT INTO message_citations(
                        id, message_id, conversation_id, citation_no, node_id, node_release_id,
                        knowledge_version_id, name, summary, url, emoji, recorded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    """, UUID.randomUUID().toString(), assistantMessageId, conversationId, index + 1,
                    value(reference.get("node_id")), value(reference.get("node_release_id")),
                    value(reference.get("knowledge_version_id")), value(reference.get("name")),
                    value(reference.get("summary")), value(reference.get("url")), value(reference.get("emoji")));
            store.update("""
                    INSERT INTO conversation_references(
                        conversation_id, app_id, node_id, name, url, favicon,
                        node_release_id, knowledge_version_id, recorded_at)
                    VALUES (?, ?, ?, ?, ?, '', NULLIF(?, ''), NULLIF(?, ''), now())
                    ON CONFLICT (conversation_id, node_id)
                    WHERE conversation_id IS NOT NULL AND node_id IS NOT NULL
                    DO UPDATE SET name = excluded.name, url = excluded.url,
                                  node_release_id = excluded.node_release_id,
                                  knowledge_version_id = excluded.knowledge_version_id,
                                  recorded_at = excluded.recorded_at
                    """, conversationId, appId, reference.get("node_id"), reference.get("name"),
                    reference.get("url"), value(reference.get("node_release_id")),
                    value(reference.get("knowledge_version_id")));
            captureReferenceSnapshot(kbId, conversationId, assistantMessageId, reference);
        }
        agenticRagStore.complete(runId, assistantMessageId, completedUsage, completedUsage.stopReason());
    }

    private String appId(String kbId, int appType) {
        List<String> existing = store.query(
                "SELECT id FROM apps WHERE kb_id = ? AND type = ?",
                (row, rowNumber) -> row.getString(1), kbId, appType);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        String id = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO apps(id, kb_id, name, type, settings, created_at, updated_at)
                VALUES (?, ?, '', ?, '{}'::jsonb, now(), now())
                ON CONFLICT(kb_id, type) DO NOTHING
                """, id, kbId, appType);
        return store.queryForObject(
                "SELECT id FROM apps WHERE kb_id = ? AND type = ?", String.class, kbId, appType);
    }

    private void captureReferenceSnapshot(
            String kbId,
            String conversationId,
            String messageId,
            Map<String, Object> reference
    ) {
        String nodeId = value(reference.get("node_id"));
        String releaseId = value(reference.get("node_release_id"));
        if (nodeId.isBlank()) {
            return;
        }
        List<Map<String, Object>> releases = releaseId.isBlank() ? List.of() : store.query("""
                SELECT id, node_id, name, content, meta
                  FROM node_releases
                 WHERE id = ? AND kb_id = ? AND node_id = ?
                UNION ALL
                SELECT id, node_id, name, content, meta
                  FROM node_release_backup
                 WHERE id = ? AND kb_id = ? AND node_id = ?
                 LIMIT 1
                """, store.rowMapper(), releaseId, kbId, nodeId, releaseId, kbId, nodeId);
        Map<String, Object> release = releases.isEmpty() ? Map.of() : releases.getFirst();
        Map<String, Object> current = store.query(
                "SELECT permissions FROM nodes WHERE id = ? AND kb_id = ?",
                store.rowMapper(), nodeId, kbId).stream().findFirst().orElse(Map.of());
        store.update("""
                INSERT INTO conversation_reference_snapshots(
                    id, conversation_id, message_id, kb_id, node_id, node_release_id, knowledge_version_id,
                    name, content, meta, permissions, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now())
                ON CONFLICT(message_id, node_id) DO NOTHING
                """, UUID.randomUUID().toString(), conversationId, messageId, kbId, nodeId,
                releaseId, value(reference.get("knowledge_version_id")),
                value(release.getOrDefault("name", reference.get("name"))),
                value(release.get("content")), jsonMaps.json(jsonMaps.jsonMap(release.get("meta"))),
                jsonMaps.json(jsonMaps.jsonMap(current.get("permissions"))));
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

    public record OpenTurn(
            String conversationId,
            String nonce,
            String appId,
            String userMessageId,
            List<Map<String, Object>> history
    ) {
    }
}
