package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 为向量和知识变更消息提供持久化幂等、乱序拦截与失败重试状态。
 *
 * @author 程序员牛肉
 * @since 2026-04-30
 */
@Component
public class KnowledgeEventLedger {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;

    public KnowledgeEventLedger(JdbcMaps store, JsonMaps jsonMaps) {
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Map<String, Object> task) {
        String kbId = value(task.get("kb_id"));
        String action = value(task.get("action"));
        String sourceVersion = value(task.get("node_release_id"));
        String aggregateId = aggregateId(task, sourceVersion);
        String eventType = "VECTOR_" + action.toUpperCase(java.util.Locale.ROOT);
        String eventId = firstNonBlank(task.get("event_id"), deterministicId(kbId, eventType, sourceVersion, aggregateId));
        long sequence = number(task.get("event_sequence"), sourceSequence(sourceVersion));
        String id = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_change_events(
                    id, event_id, kb_id, aggregate_id, sequence_no, event_type,
                    source_version, payload, status, recorded_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', now(), now())
                ON CONFLICT DO NOTHING
                """, id, eventId, kbId, aggregateId, sequence, eventType, sourceVersion, jsonMaps.json(task));
        Map<String, Object> event = store.query("""
                SELECT id, event_id, aggregate_id, sequence_no, event_type, source_version, status
                  FROM knowledge_change_events
                 WHERE event_id = ? OR (kb_id = ? AND event_type = ? AND source_version = ? AND ? <> '')
                 ORDER BY created_at LIMIT 1 FOR UPDATE
                """, store.rowMapper(), eventId, kbId, eventType, sourceVersion, sourceVersion)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("知识事件写入失败"));
        String status = value(event.get("status"));
        if (List.of("PROCESSED", "STALE").contains(status)) {
            return Claim.ignored(value(event.get("id")), value(event.get("event_id")), status);
        }
        List<Map<String, Object>> states = store.query("""
                SELECT last_sequence_no, last_source_version FROM knowledge_source_states
                 WHERE kb_id = ? AND aggregate_id = ? FOR UPDATE
                """, store.rowMapper(), kbId, aggregateId);
        if (!states.isEmpty()) {
            long lastSequence = number(states.getFirst().get("last_sequence_no"), 0L);
            String lastVersion = value(states.getFirst().get("last_source_version"));
            if (sequence < lastSequence || sequence == lastSequence && sourceVersion.equals(lastVersion)) {
                store.update("""
                        UPDATE knowledge_change_events
                           SET status = 'STALE', processed_at = now(), error_message = '乱序或重复事件'
                         WHERE id = ?
                        """, event.get("id"));
                return Claim.ignored(value(event.get("id")), value(event.get("event_id")), "STALE");
            }
        }
        store.update("""
                UPDATE knowledge_change_events
                   SET status = 'PROCESSING', attempts = attempts + 1, error_message = ''
                 WHERE id = ?
                """, event.get("id"));
        return new Claim(value(event.get("id")), value(event.get("event_id")), kbId,
                aggregateId, sequence, eventType, sourceVersion, true, "PROCESSING");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Claim claim, String contentHash, String permissionHash, boolean tombstoned) {
        if (!claim.process()) {
            return;
        }
        store.update("""
                UPDATE knowledge_change_events
                   SET status = 'PROCESSED', processed_at = now(), error_message = ''
                 WHERE id = ?
                """, claim.id());
        store.update("""
                INSERT INTO knowledge_source_states(
                    kb_id, aggregate_id, last_sequence_no, last_event_id, last_source_version,
                    content_hash, permission_hash, tombstoned, last_seen_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                ON CONFLICT(kb_id, aggregate_id) DO UPDATE SET
                    last_sequence_no = excluded.last_sequence_no,
                    last_event_id = excluded.last_event_id,
                    last_source_version = excluded.last_source_version,
                    content_hash = excluded.content_hash,
                    permission_hash = excluded.permission_hash,
                    tombstoned = excluded.tombstoned,
                    last_seen_at = now(),
                    updated_at = now()
                WHERE knowledge_source_states.last_sequence_no <= excluded.last_sequence_no
                """, claim.kbId(), claim.aggregateId(), claim.sequence(), claim.eventId(),
                claim.sourceVersion(), value(contentHash), value(permissionHash), tombstoned);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Claim claim, RuntimeException exception) {
        if (!claim.process()) {
            return;
        }
        store.update("""
                UPDATE knowledge_change_events
                   SET status = 'FAILED', error_message = ?, processed_at = now()
                 WHERE id = ?
                """, safeMessage(exception), claim.id());
    }

    public Map<String, Object> enrich(Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>(task);
        result.putIfAbsent("event_id", UUID.randomUUID().toString());
        result.putIfAbsent("event_sequence", System.currentTimeMillis());
        result.putIfAbsent("recorded_at", Instant.now().toString());
        return result;
    }

    private String aggregateId(Map<String, Object> task, String sourceVersion) {
        String nodeId = value(task.get("node_id"));
        if (!nodeId.isBlank()) {
            return nodeId;
        }
        String documentId = value(task.get("doc_id"));
        if (!documentId.isBlank()) {
            List<String> nodes = store.query("""
                    SELECT node_id FROM node_releases WHERE doc_id = ?
                    UNION ALL
                    SELECT node_id FROM node_release_backup WHERE doc_id = ?
                    LIMIT 1
                    """, (row, rowNumber) -> row.getString(1), documentId, documentId);
            if (!nodes.isEmpty()) {
                return nodes.getFirst();
            }
        }
        return firstNonBlank(sourceVersion, documentId, "global");
    }

    private long sourceSequence(String sourceVersion) {
        if (sourceVersion == null || sourceVersion.isBlank()) {
            return System.currentTimeMillis();
        }
        return store.query("""
                SELECT (extract(epoch FROM created_at) * 1000)::bigint
                  FROM node_releases WHERE id = ?
                """, (row, rowNumber) -> row.getLong(1), sourceVersion)
                .stream().findFirst().orElse(System.currentTimeMillis());
    }

    private static String deterministicId(String... values) {
        return UUID.nameUUIDFromBytes(String.join(":", values).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static String firstNonBlank(Object... values) {
        for (Object item : values) {
            String text = value(item);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(500, message.length()));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record Claim(
            String id,
            String eventId,
            String kbId,
            String aggregateId,
            long sequence,
            String eventType,
            String sourceVersion,
            boolean process,
            String status
    ) {
        private static Claim ignored(String id, String eventId, String status) {
            return new Claim(id, eventId, "", "", 0L, "", "", false, status);
        }
    }
}
