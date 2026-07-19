package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.common.PageResult;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理知识编译任务、版本指针、编译产物与回滚操作。
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
@Service
public class KnowledgeCompilerService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final VectorTaskPublisher taskPublisher;

    public KnowledgeCompilerService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            AuthService authService,
            VectorTaskPublisher taskPublisher
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.taskPublisher = taskPublisher;
    }

    @Transactional
    public String requestCompile(KnowledgeCompilerDtos.CompileRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        return createRun(
                request.kbId(),
                cleanIds(request.nodeIds()),
                List.of(),
                request.forceFull(),
                "manual",
                AuthContext.get().userId());
    }

    @Transactional
    public String requestAutomaticCompile(String kbId, String nodeId, String releaseId) {
        return createRun(kbId, List.of(nodeId), List.of(releaseId), false, "document_learned", "system");
    }

    @Transactional
    public String requestReleaseCompile(String kbId, String sourceVersion, String createdBy) {
        return createRun(kbId, List.of(), List.of(sourceVersion), true, "knowledge_release", createdBy);
    }

    @Transactional
    public String requestReconciliationCompile(String kbId) {
        return createRun(kbId, List.of(), List.of(), true, "reconciliation", "system");
    }

    public Map<String, Object> overview(String kbId) {
        require(kbId);
        Map<String, Object> overview = store.queryForObject("""
                SELECT kb.id AS kb_id,
                       s.active_version_id,
                       s.previous_version_id,
                       s.active_index_id,
                       s.previous_index_id,
                       s.last_reconciled_at,
                       v.version_no AS active_version_no,
                       v.manifest_hash,
                       v.validation_status,
                       v.published_at,
                       idx.status AS active_index_status,
                       idx.document_count AS index_document_count,
                       COALESCE((SELECT count(*) FROM knowledge_artifacts a
                                 WHERE a.version_id = s.active_version_id AND a.artifact_key <> '__index__'), 0)
                           AS artifact_count,
                       COALESCE((SELECT count(DISTINCT d.source_node_id) FROM knowledge_dependencies d
                                 WHERE d.version_id = s.active_version_id), 0) AS source_count,
                       COALESCE((SELECT count(*) FROM knowledge_conflicts c
                                 WHERE c.version_id = s.active_version_id AND c.status = 'OPEN'), 0)
                           AS conflict_count,
                       COALESCE((SELECT count(*) FROM knowledge_lint_issues i
                                 WHERE i.version_id = s.active_version_id AND i.status = 'OPEN'), 0)
                           AS issue_count
                  FROM knowledge_bases kb
                  LEFT JOIN knowledge_compiler_states s ON s.kb_id = kb.id
                  LEFT JOIN knowledge_versions v ON v.id = s.active_version_id
                  LEFT JOIN knowledge_shadow_indexes idx ON idx.id = s.active_index_id
                 WHERE kb.id = ?
                """, store.rowMapper(), kbId);
        List<Map<String, Object>> latest = store.query("""
                SELECT id, trigger_type, status, force_full, version_id, stats, error_message,
                       started_at, completed_at, created_at
                  FROM knowledge_compiler_runs
                 WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1
                """, store.rowMapper(), kbId);
        overview.put("latest_run", latest.isEmpty() ? null : latest.getFirst());
        return overview;
    }

    public PageResult<List<Map<String, Object>>> runs(String kbId, int page, int perPage) {
        require(kbId);
        long total = store.queryForObject(
                "SELECT count(*) FROM knowledge_compiler_runs WHERE kb_id = ?", Long.class, kbId);
        List<Map<String, Object>> runs = store.query("""
                SELECT r.id, r.trigger_type, r.status, r.requested_node_ids, r.force_full,
                       r.version_id, v.version_no, r.stats, r.error_message, r.created_by,
                       r.started_at, r.completed_at, r.created_at
                  FROM knowledge_compiler_runs r
                  LEFT JOIN knowledge_versions v ON v.id = r.version_id
                 WHERE r.kb_id = ?
                 ORDER BY r.created_at DESC OFFSET ? LIMIT ?
                """, store.rowMapper(), kbId, Math.max(0, page - 1) * perPage, perPage);
        return new PageResult<>(total, runs);
    }

    public List<Map<String, Object>> versions(String kbId) {
        require(kbId);
        return store.query("""
                SELECT v.id, v.version_no, v.previous_version_id, v.run_id, v.status,
                       v.manifest_hash, v.stats, v.created_by, v.published_at, v.created_at,
                       v.shadow_index_id, v.validation_status, v.validation_report,
                       v.activated_at, idx.status AS index_status, idx.document_count,
                       (v.id = s.active_version_id) AS active
                  FROM knowledge_versions v
                  LEFT JOIN knowledge_compiler_states s ON s.kb_id = v.kb_id
                  LEFT JOIN knowledge_shadow_indexes idx ON idx.id = v.shadow_index_id
                 WHERE v.kb_id = ? ORDER BY v.version_no DESC LIMIT 100
                """, store.rowMapper(), kbId);
    }

    public PageResult<List<Map<String, Object>>> artifacts(
            String kbId,
            String versionId,
            String search,
            int page,
            int perPage
    ) {
        require(kbId);
        String resolvedVersion = resolveVersion(kbId, versionId);
        if (resolvedVersion.isBlank()) {
            return new PageResult<>(0, List.of());
        }
        StringBuilder where = new StringBuilder(
                " WHERE a.kb_id = ? AND a.version_id = ? AND a.artifact_key <> '__index__'");
        List<Object> arguments = new ArrayList<>(List.of(kbId, resolvedVersion));
        if (search != null && !search.isBlank()) {
            where.append(" AND (a.title ILIKE ? OR a.summary ILIKE ? OR a.content ILIKE ?)");
            String pattern = "%" + search.strip().replaceAll("\\s+", "%") + "%";
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        long total = store.queryForObject(
                "SELECT count(*) FROM knowledge_artifacts a" + where,
                Long.class,
                arguments.toArray());
        arguments.add(Math.max(0, page - 1) * perPage);
        arguments.add(perPage);
        List<Map<String, Object>> artifacts = store.query("""
                SELECT a.id, a.artifact_key, a.type, a.title, a.summary, a.confidence,
                       a.source_node_ids, a.source_release_ids, a.content_hash, a.status,
                       a.identity_key, a.valid_from, a.valid_to, a.recorded_at,
                       a.source_version, a.knowledge_version, a.supersedes_id, a.updated_at
                  FROM knowledge_artifacts a
                """ + where + " ORDER BY a.type, a.title OFFSET ? LIMIT ?",
                store.rowMapper(), arguments.toArray());
        return new PageResult<>(total, artifacts);
    }

    public Map<String, Object> artifact(String kbId, String id) {
        require(kbId);
        Map<String, Object> artifact = store.queryForObject("""
                SELECT a.*, v.version_no
                  FROM knowledge_artifacts a
                  JOIN knowledge_versions v ON v.id = a.version_id
                 WHERE a.id = ? AND a.kb_id = ?
                """, store.rowMapper(), id, kbId);
        artifact.put("dependencies", store.query("""
                SELECT d.source_node_id, d.source_release_id, d.dependency_type,
                       nr.name AS source_name
                  FROM knowledge_dependencies d
                  LEFT JOIN node_releases nr ON nr.id = d.source_release_id
                 WHERE d.artifact_id = ? ORDER BY d.created_at
                """, store.rowMapper(), id));
        artifact.put("timeline", store.query("""
                SELECT a.id, a.version_id, a.knowledge_version, a.status, a.valid_from, a.valid_to,
                       a.recorded_at, a.source_version, a.supersedes_id, a.content_hash,
                       v.published_at, (a.version_id = s.active_version_id) AS active
                  FROM knowledge_artifacts a
                  JOIN knowledge_versions v ON v.id = a.version_id
                  LEFT JOIN knowledge_compiler_states s ON s.kb_id = a.kb_id
                 WHERE a.kb_id = ? AND a.identity_key = ?
                 ORDER BY a.knowledge_version DESC, a.recorded_at DESC
                """, store.rowMapper(), kbId, artifact.get("identity_key")));
        return artifact;
    }

    public Map<String, Object> releaseDiagnostics(String kbId, String versionId) {
        require(kbId);
        String resolvedVersion = resolveVersion(kbId, versionId);
        if (resolvedVersion.isBlank()) {
            return Map.of("validations", List.of(), "changes", List.of(), "activations", List.of());
        }
        return Map.of(
                "validations", store.query("""
                        SELECT check_code, severity, status, message, metrics, created_at
                          FROM knowledge_release_validations
                         WHERE kb_id = ? AND version_id = ?
                         ORDER BY CASE severity WHEN 'ERROR' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, created_at
                        """, store.rowMapper(), kbId, resolvedVersion),
                "changes", store.query("""
                        SELECT node_id, change_type, before_snapshot, after_snapshot, created_at
                          FROM knowledge_version_changes
                         WHERE kb_id = ? AND version_id = ?
                         ORDER BY created_at, node_id, change_type
                        """, store.rowMapper(), kbId, resolvedVersion),
                "activations", store.query("""
                        SELECT id, version_id, previous_version_id, valid_from, valid_to,
                               reason, activated_by, created_at
                          FROM knowledge_version_activations
                         WHERE kb_id = ? AND version_id = ? ORDER BY valid_from DESC
                        """, store.rowMapper(), kbId, resolvedVersion));
    }

    public Map<String, Object> issues(String kbId, String versionId) {
        require(kbId);
        String resolvedVersion = resolveVersion(kbId, versionId);
        if (resolvedVersion.isBlank()) {
            return Map.of("conflicts", List.of(), "lint_issues", List.of());
        }
        return Map.of(
                "conflicts", store.query("""
                        SELECT c.*, a.title AS artifact_title
                          FROM knowledge_conflicts c
                          LEFT JOIN knowledge_artifacts a ON a.id = c.artifact_id
                         WHERE c.kb_id = ? AND c.version_id = ?
                         ORDER BY CASE c.severity WHEN 'ERROR' THEN 0 ELSE 1 END, c.created_at DESC
                        """, store.rowMapper(), kbId, resolvedVersion),
                "lint_issues", store.query("""
                        SELECT i.*, a.title AS artifact_title
                          FROM knowledge_lint_issues i
                          LEFT JOIN knowledge_artifacts a ON a.id = i.artifact_id
                         WHERE i.kb_id = ? AND i.version_id = ?
                         ORDER BY CASE i.severity WHEN 'ERROR' THEN 0 ELSE 1 END, i.created_at DESC
                        """, store.rowMapper(), kbId, resolvedVersion));
    }

    @Transactional
    public void rollback(KnowledgeCompilerDtos.RollbackRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        Integer valid = store.queryForObject("""
                SELECT count(*) FROM knowledge_versions v
                  JOIN knowledge_shadow_indexes i ON i.id = v.shadow_index_id
                 WHERE v.id = ? AND v.kb_id = ? AND v.status = 'PUBLISHED'
                   AND v.validation_status = 'PASSED' AND i.status IN ('ACTIVE', 'RETIRED', 'VALIDATED')
                """, Integer.class, request.versionId(), request.kbId());
        if (valid == null || valid == 0) {
            throw new ApiException("目标知识版本不存在、未通过门禁或没有可恢复索引");
        }
        Map<String, Object> state = store.query("""
                SELECT active_version_id, active_index_id FROM knowledge_compiler_states
                 WHERE kb_id = ? FOR UPDATE
                """, store.rowMapper(), request.kbId()).stream().findFirst()
                .orElse(Map.of("active_version_id", "", "active_index_id", ""));
        String currentVersion = value(state.get("active_version_id"));
        String currentIndex = value(state.get("active_index_id"));
        String targetIndex = store.queryForObject(
                "SELECT shadow_index_id FROM knowledge_versions WHERE id = ?", String.class, request.versionId());
        if (request.versionId().equals(currentVersion)) {
            return;
        }
        store.update("UPDATE knowledge_version_activations SET valid_to = now() "
                + "WHERE kb_id = ? AND valid_to IS NULL", request.kbId());
        if (!currentVersion.isBlank()) {
            store.update("""
                    UPDATE knowledge_artifacts SET status = 'EXPIRED', valid_to = now(), updated_at = now()
                     WHERE version_id = ? AND status IN ('EFFECTIVE', 'CONFLICT')
                    """, currentVersion);
        }
        store.update("""
                UPDATE knowledge_artifacts
                   SET status = CASE
                       WHEN EXISTS (SELECT 1 FROM knowledge_conflicts c
                                    WHERE c.artifact_id = knowledge_artifacts.id AND c.status = 'OPEN')
                       THEN 'CONFLICT' ELSE 'EFFECTIVE' END,
                       valid_from = now(), valid_to = NULL, updated_at = now()
                 WHERE version_id = ?
                """, request.versionId());
        if (!currentIndex.isBlank()) {
            store.update("UPDATE knowledge_shadow_indexes SET status = 'RETIRED', retired_at = now() WHERE id = ?",
                    currentIndex);
        }
        store.update("""
                UPDATE knowledge_shadow_indexes
                   SET status = 'ACTIVE', activated_at = now(), retired_at = NULL
                 WHERE id = ?
                """, targetIndex);
        store.update("""
                INSERT INTO knowledge_compiler_states(
                    kb_id, active_version_id, previous_version_id, active_index_id,
                    previous_index_id, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (kb_id) DO UPDATE
                SET previous_version_id = knowledge_compiler_states.active_version_id,
                    previous_index_id = knowledge_compiler_states.active_index_id,
                    active_version_id = excluded.active_version_id,
                    active_index_id = excluded.active_index_id,
                    updated_at = now()
                """, request.kbId(), request.versionId(), blankToNull(currentVersion),
                targetIndex, blankToNull(currentIndex));
        store.update("""
                INSERT INTO knowledge_version_activations(
                    id, kb_id, version_id, previous_version_id, valid_from,
                    reason, activated_by, created_at)
                VALUES (?, ?, ?, ?, now(), 'rollback', ?, now())
                """, UUID.randomUUID().toString(), request.kbId(), request.versionId(),
                blankToNull(currentVersion), AuthContext.get().userId());
        String runId = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_compiler_runs(
                    id, kb_id, trigger_type, status, version_id, stats, created_by,
                    started_at, completed_at, created_at)
                VALUES (?, ?, 'rollback', 'ROLLED_BACK', ?, ?::jsonb, ?, now(), now(), now())
                """, runId, request.kbId(), request.versionId(),
                jsonMaps.json(Map.of(
                        "rollback_to", request.versionId(),
                        "content_version_changed", true,
                        "permissions_restored", false)), AuthContext.get().userId());
    }

    private String createRun(
            String kbId,
            List<String> nodeIds,
            List<String> releaseIds,
            boolean forceFull,
            String triggerType,
            String createdBy
    ) {
        store.queryForObject("SELECT id FROM knowledge_bases WHERE id = ? FOR UPDATE", String.class, kbId);
        List<Map<String, Object>> queued = store.query("""
                SELECT id, requested_node_ids, requested_release_ids, force_full
                  FROM knowledge_compiler_runs
                 WHERE kb_id = ? AND status = 'QUEUED'
                 ORDER BY created_at LIMIT 1 FOR UPDATE
                """, store.rowMapper(), kbId);
        if (!queued.isEmpty()) {
            Map<String, Object> existing = queued.getFirst();
            List<String> mergedNodes = new ArrayList<>(stringList(existing.get("requested_node_ids")));
            mergedNodes.addAll(nodeIds);
            List<String> mergedReleases = new ArrayList<>(stringList(existing.get("requested_release_ids")));
            mergedReleases.addAll(releaseIds);
            store.update("""
                    UPDATE knowledge_compiler_runs
                       SET requested_node_ids = ?::text[], requested_release_ids = ?::text[],
                           force_full = force_full OR ?, trigger_type = ?, created_by = ?
                     WHERE id = ?
                    """, postgresTextArray(cleanIds(mergedNodes)), postgresTextArray(cleanIds(mergedReleases)),
                    forceFull, triggerType, createdBy, existing.get("id"));
            return value(existing.get("id"));
        }
        boolean running = Boolean.TRUE.equals(store.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM knowledge_compiler_runs WHERE kb_id = ? AND status = 'RUNNING')
                """, Boolean.class, kbId));
        String id = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_compiler_runs(
                    id, kb_id, trigger_type, status, requested_node_ids, requested_release_ids,
                    force_full, stats, created_by, created_at)
                VALUES (?, ?, ?, 'QUEUED', ?::text[], ?::text[], ?, '{}'::jsonb, ?, now())
                """, id, kbId, triggerType, postgresTextArray(nodeIds), postgresTextArray(releaseIds),
                forceFull, createdBy);
        if (!running) {
            taskPublisher.compileAfterCommit(kbId, id);
        }
        return id;
    }

    private String resolveVersion(String kbId, String requested) {
        if (requested != null && !requested.isBlank()) {
            Integer valid = store.queryForObject(
                    "SELECT count(*) FROM knowledge_versions WHERE id = ? AND kb_id = ?",
                    Integer.class, requested, kbId);
            if (valid == null || valid == 0) {
                throw new ApiException("知识版本不存在");
            }
            return requested;
        }
        return store.query(
                "SELECT active_version_id FROM knowledge_compiler_states WHERE kb_id = ?",
                (row, rowNumber) -> row.getString(1), kbId).stream().findFirst().orElse("");
    }

    private void require(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
    }

    private static List<String> cleanIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(KnowledgeCompilerService::value)
                    .filter(item -> !item.isBlank()).distinct().toList();
        }
        if (value instanceof Object[] array) {
            return java.util.Arrays.stream(array).map(KnowledgeCompilerService::value)
                    .filter(item -> !item.isBlank()).distinct().toList();
        }
        return List.of();
    }

    static String postgresTextArray(List<String> values) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
