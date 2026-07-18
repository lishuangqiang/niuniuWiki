package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEvent;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Evidence;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Reflection;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalBudget;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalPlan;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.UsageSnapshot;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 持久化 Agent 运行、步骤、预算快照与证据，使多跳检索可以审计并在中断后恢复。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
@Component
public class AgenticRagStore {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;

    public AgenticRagStore(MyBatisStore store, JsonMaps jsonMaps) {
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    public void createRun(AgenticRagModels.RunRequest request) {
        store.update("""
                INSERT INTO agentic_rag_runs(
                    id, kb_id, conversation_id, user_message_id, question, status, started_at, updated_at, created_at)
                VALUES (?, ?, ?, ?, ?, 'PLANNING', now(), now(), now())
                ON CONFLICT (id) DO NOTHING
                """, request.runId(), request.kbId(), request.conversationId(),
                request.userMessageId(), request.question());
    }

    public void savePlan(String runId, RetrievalPlan plan, RetrievalBudget budget, UsageSnapshot usage) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET mode = ?, status = 'RUNNING', plan = ?::jsonb, budget = ?::jsonb,
                       usage = ?::jsonb, clarification_question = ?, updated_at = now()
                 WHERE id = ?
                """, plan.mode().name(), jsonMaps.json(plan), jsonMaps.json(budget),
                jsonMaps.json(usage), value(plan.clarificationQuestion()), runId);
    }

    public void appendEvent(AgentEvent event, Map<String, Object> input, Map<String, Object> output) {
        store.update("""
                INSERT INTO agentic_rag_steps(
                    id, run_id, sequence_no, stage, status, iteration, message, input, output, metrics, created_at)
                VALUES (?, ?, COALESCE((SELECT MAX(sequence_no) + 1 FROM agentic_rag_steps WHERE run_id = ?), 1),
                        ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now())
                """, UUID.randomUUID().toString(), event.runId(), event.runId(), event.stage(), event.status(),
                event.iteration(), event.message(), jsonMaps.json(safe(input)), jsonMaps.json(safe(output)),
                jsonMaps.json(event.metrics()));
    }

    public void saveEvidence(String runId, Evidence evidence) {
        store.update("""
                INSERT INTO agentic_rag_evidence(
                    id, run_id, evidence_key, node_id, document_id, title, summary, content, url, emoji,
                    score, query, hop, metadata, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                ON CONFLICT (run_id, evidence_key) DO UPDATE SET
                    score = GREATEST(agentic_rag_evidence.score, EXCLUDED.score),
                    query = CASE WHEN agentic_rag_evidence.query = EXCLUDED.query
                                 THEN agentic_rag_evidence.query
                                 ELSE agentic_rag_evidence.query || ' | ' || EXCLUDED.query END,
                    hop = LEAST(agentic_rag_evidence.hop, EXCLUDED.hop),
                    metadata = agentic_rag_evidence.metadata || EXCLUDED.metadata,
                    updated_at = now()
                """, UUID.randomUUID().toString(), runId, evidence.evidenceKey(), value(evidence.nodeId()),
                value(evidence.documentId()), value(evidence.title()), value(evidence.summary()),
                value(evidence.content()), value(evidence.url()), value(evidence.emoji()), evidence.score(),
                value(evidence.query()), evidence.hop(), jsonMaps.json(safe(evidence.metadata())));
    }

    public void checkpoint(
            String runId,
            int iteration,
            UsageSnapshot usage,
            Reflection reflection
    ) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET current_iteration = ?, usage = ?::jsonb, evidence_sufficient = ?,
                       evidence_confidence = ?, stop_reason = ?, updated_at = now()
                 WHERE id = ?
                """, iteration, jsonMaps.json(usage), reflection.sufficient(), reflection.confidence(),
                value(usage.stopReason()), runId);
    }

    public void markGenerating(String runId, UsageSnapshot usage, boolean sufficient, double confidence) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'GENERATING', usage = ?::jsonb, evidence_sufficient = ?,
                       evidence_confidence = ?, updated_at = now()
                 WHERE id = ?
                """, jsonMaps.json(usage), sufficient, confidence, runId);
    }

    public void complete(String runId, String answerMessageId, UsageSnapshot usage, String stopReason) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'COMPLETED', answer_message_id = ?, usage = ?::jsonb,
                       stop_reason = ?, completed_at = now(), updated_at = now(), error_message = ''
                 WHERE id = ?
                """, value(answerMessageId), jsonMaps.json(usage), value(stopReason), runId);
    }

    public void cancel(String runId, String reason) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'CANCELLED', stop_reason = 'CANCELLED', error_message = ?,
                       completed_at = now(), updated_at = now()
                 WHERE id = ? AND status NOT IN ('COMPLETED', 'CANCELLED')
                """, value(reason), runId);
    }

    public void fail(String runId, String message) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'FAILED', error_message = ?, completed_at = now(), updated_at = now()
                 WHERE id = ? AND status <> 'COMPLETED'
                """, value(message), runId);
    }

    public int pauseInterruptedRuns() {
        return store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'PAUSED', stop_reason = 'PROCESS_RESTART', updated_at = now()
                 WHERE status IN ('PLANNING', 'RUNNING', 'GENERATING')
                """);
    }

    public void resume(String runId) {
        store.update("""
                UPDATE agentic_rag_runs
                   SET status = 'RUNNING', completed_at = NULL, error_message = '',
                       stop_reason = '', updated_at = now()
                 WHERE id = ?
                """, runId);
    }

    public Map<String, Object> run(String runId) {
        return store.queryForObject(
                "SELECT * FROM agentic_rag_runs WHERE id = ?", store.rowMapper(), runId);
    }

    public List<Map<String, Object>> evidence(String runId) {
        return store.query("""
                SELECT evidence_key, node_id, document_id, title, summary, content, url, emoji,
                       score, query, hop, metadata
                  FROM agentic_rag_evidence WHERE run_id = ?
                 ORDER BY score DESC, created_at
                """, store.rowMapper(), runId);
    }

    public List<Map<String, Object>> steps(String runId) {
        return store.query("""
                SELECT sequence_no, stage, status, iteration, message, input, output, metrics, created_at
                  FROM agentic_rag_steps WHERE run_id = ? ORDER BY sequence_no
                """, store.rowMapper(), runId);
    }

    public List<Map<String, Object>> list(String kbId, int limit) {
        return store.query("""
                SELECT id, conversation_id, question, mode, status, usage, current_iteration,
                       evidence_sufficient, evidence_confidence, stop_reason, error_message,
                       started_at, completed_at, created_at
                  FROM agentic_rag_runs WHERE kb_id = ? ORDER BY created_at DESC LIMIT ?
                """, store.rowMapper(), kbId, Math.max(1, Math.min(200, limit)));
    }

    public Map<String, Object> jsonMap(Object value) {
        return jsonMaps.jsonMap(value);
    }

    private static Map<String, Object> safe(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
