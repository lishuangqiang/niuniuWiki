package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

/**
 * 周期性对账发布快照、事件消费状态和向量来源，补偿丢失的增量事件。
 *
 * @author 程序员牛肉
 * @since 2026-07-02
 */
@Service
public class KnowledgeReconciliationService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final VectorTaskPublisher vectorTasks;
    private final KnowledgeCompilerService compilerService;
    private final ApplicationContext applicationContext;

    public KnowledgeReconciliationService(
            MyBatisStore store,
            JsonMaps jsonMaps,
            AuthService authService,
            VectorTaskPublisher vectorTasks,
            KnowledgeCompilerService compilerService,
            ApplicationContext applicationContext
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.vectorTasks = vectorTasks;
        this.compilerService = compilerService;
        this.applicationContext = applicationContext;
    }

    public Map<String, Object> reconcileNow(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        return reconcile(kbId);
    }

    @Scheduled(cron = "0 17 * * * *")
    public void scheduledReconciliation() {
        if (!(applicationContext instanceof WebApplicationContext)) {
            return;
        }
        List<String> kbIds = store.query(
                "SELECT id FROM knowledge_bases ORDER BY id",
                (row, rowNumber) -> row.getString(1));
        for (String kbId : kbIds) {
            try {
                reconcile(kbId);
            } catch (RuntimeException ignored) {
                // 单个知识库异常不会阻断其他知识库的周期对账；运行记录会保留诊断结果。
            }
        }
    }

    public List<Map<String, Object>> runs(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        return store.query("""
                SELECT id, status, expected_count, actual_count, missing_count, stale_count,
                       report, compile_run_id, started_at, completed_at, created_at
                  FROM knowledge_reconciliation_runs
                 WHERE kb_id = ? ORDER BY created_at DESC LIMIT 30
                """, store.rowMapper(), kbId);
    }

    private Map<String, Object> reconcile(String kbId) {
        String id = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_reconciliation_runs(id, kb_id, status, started_at, created_at)
                VALUES (?, ?, 'RUNNING', now(), now())
                """, id, kbId);
        try {
            List<Map<String, Object>> expectedRows = store.query("""
                    SELECT nr.node_id, nr.id AS node_release_id, nr.doc_id
                      FROM kb_releases kr
                      JOIN kb_release_node_releases links ON links.release_id = kr.id
                      JOIN node_releases nr ON nr.id = links.node_release_id
                     WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                       AND nr.type = 2
                     ORDER BY nr.node_id
                    """, store.rowMapper(), kbId);
            Map<String, Map<String, Object>> expected = new LinkedHashMap<>();
            expectedRows.forEach(row -> expected.put(value(row.get("node_id")), row));
            List<Map<String, Object>> stateRows = store.query("""
                    SELECT aggregate_id, last_source_version, tombstoned
                      FROM knowledge_source_states WHERE kb_id = ?
                    """, store.rowMapper(), kbId);
            Map<String, Map<String, Object>> actual = new LinkedHashMap<>();
            stateRows.forEach(row -> actual.put(value(row.get("aggregate_id")), row));

            Set<String> missing = new LinkedHashSet<>();
            for (Map.Entry<String, Map<String, Object>> entry : expected.entrySet()) {
                Map<String, Object> state = actual.get(entry.getKey());
                if (state == null || !value(entry.getValue().get("node_release_id"))
                        .equals(value(state.get("last_source_version")))) {
                    missing.add(entry.getKey());
                }
            }
            Set<String> stale = new LinkedHashSet<>();
            for (String aggregateId : actual.keySet()) {
                if (!expected.containsKey(aggregateId)
                        && !Boolean.TRUE.equals(actual.get(aggregateId).get("tombstoned"))) {
                    stale.add(aggregateId);
                }
            }

            for (String nodeId : missing) {
                Map<String, Object> source = expected.get(nodeId);
                vectorTasks.upsertAfterCommit(kbId, value(source.get("node_release_id")), nodeId);
            }
            for (String nodeId : stale) {
                String releaseId = value(actual.get(nodeId).get("last_source_version"));
                String documentId = store.query("""
                        SELECT doc_id FROM node_releases WHERE id = ? AND doc_id <> ''
                        UNION ALL
                        SELECT doc_id FROM node_release_backup WHERE id = ? AND doc_id <> ''
                        LIMIT 1
                        """, (row, rowNumber) -> row.getString(1), releaseId, releaseId)
                        .stream().findFirst().orElse("");
                vectorTasks.deleteAfterCommit(kbId, documentId);
            }
            String compileRunId = missing.isEmpty() && stale.isEmpty()
                    ? "" : compilerService.requestReconciliationCompile(kbId);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("missing_node_ids", new ArrayList<>(missing));
            report.put("stale_node_ids", new ArrayList<>(stale));
            report.put("replayed_event_count", missing.size() + stale.size());
            report.put("consistent", missing.isEmpty() && stale.isEmpty());
            store.update("""
                    UPDATE knowledge_reconciliation_runs
                       SET status = 'COMPLETED', expected_count = ?, actual_count = ?,
                           missing_count = ?, stale_count = ?, report = ?::jsonb,
                           compile_run_id = ?, completed_at = now()
                     WHERE id = ?
                    """, expected.size(), actual.size(), missing.size(), stale.size(),
                    jsonMaps.json(report), blankToNull(compileRunId), id);
            store.update("""
                    INSERT INTO knowledge_compiler_states(kb_id, last_reconciled_at, updated_at)
                    VALUES (?, now(), now())
                    ON CONFLICT(kb_id) DO UPDATE SET last_reconciled_at = now(), updated_at = now()
                    """, kbId);
            report.put("run_id", id);
            report.put("compile_run_id", compileRunId);
            return report;
        } catch (RuntimeException exception) {
            store.update("""
                    UPDATE knowledge_reconciliation_runs
                       SET status = 'FAILED', report = ?::jsonb, completed_at = now()
                     WHERE id = ?
                    """, jsonMaps.json(Map.of("error", safeMessage(exception))), id);
            throw exception;
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(500, message.length()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
