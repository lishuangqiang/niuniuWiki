package com.chaitin.niuniuwiki.integration;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 将外部消息发布意图与业务数据写入同一个数据库事务。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class IntegrationOutboxService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;

    public IntegrationOutboxService(JdbcMaps store, JsonMaps jsonMaps) {
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    public String enqueue(String subject, Map<String, Object> payload) {
        String id = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO integration_outbox(
                    id, subject, payload, status, available_at, created_at, updated_at)
                VALUES (?, ?, ?::jsonb, 'PENDING', now(), now(), now())
                """, id, subject, jsonMaps.json(payload));
        return id;
    }

    public List<Map<String, Object>> claimBatch(int limit) {
        return store.query("""
                WITH candidates AS (
                    SELECT id FROM integration_outbox
                     WHERE (status IN ('PENDING', 'FAILED') AND available_at <= now())
                        OR (status = 'PROCESSING' AND claimed_at < now() - interval '5 minutes')
                     ORDER BY created_at
                     FOR UPDATE SKIP LOCKED
                     LIMIT ?
                )
                UPDATE integration_outbox outbox
                   SET status = 'PROCESSING', claimed_at = now(), attempts = attempts + 1,
                       error_message = '', updated_at = now()
                  FROM candidates
                 WHERE outbox.id = candidates.id
                RETURNING outbox.id, outbox.subject, outbox.payload, outbox.attempts
                """, store.rowMapper(), Math.max(1, Math.min(100, limit)));
    }

    public void published(String id) {
        store.update("""
                UPDATE integration_outbox
                   SET status = 'PUBLISHED', published_at = now(), updated_at = now(), error_message = ''
                 WHERE id = ?
                """, id);
    }

    public void failed(String id, String message) {
        store.update("""
                UPDATE integration_outbox
                   SET status = 'FAILED', error_message = ?,
                       available_at = now() + LEAST(attempts, 12) * interval '5 seconds',
                       updated_at = now()
                 WHERE id = ?
                """, safeMessage(message), id);
    }

    private static String safeMessage(String message) {
        String safe = message == null ? "消息发布失败" : message;
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
