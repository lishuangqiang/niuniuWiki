package com.chaitin.niuniuwiki.compiler;

import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 将已发布原始证据增量编译为可追溯、可校验、可回滚的知识版本。
 *
 * <p>模型调用在数据库事务之外执行；只有任务抢占、版本创建和最终版本指针
 * 切换使用短事务，避免长时间占用连接或把半成品暴露给问答链路。</p>
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
@Component
public class KnowledgeCompilerEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeCompilerEngine.class);
    private static final String SYSTEM_PROMPT = """
            你是企业级 AI 知识编译器。请把原始证据编译成少量、边界清晰、可复用的知识页面。
            必须只使用输入证据，不得补写输入中不存在的事实；保留关键限定条件、时间、数值和例外。
            仅输出合法 JSON，不要输出 Markdown 代码围栏。JSON 结构必须为：
            {"artifacts":[{"key":"稳定英文或中文短键","type":"concept|process|decision|reference",
            "title":"标题","summary":"不超过180字摘要","content":"结构清晰的Markdown正文",
            "facts":[{"subject":"主体","predicate":"属性或关系","object":"值","quote":"原文证据"}],
            "entities":[{"name":"实体名","type":"类型","description":"描述"}],"confidence":0.0}]}
            无法确定的字段使用空数组，不得臆测。
            """;

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;
    private final ModelGateway modelGateway;
    private final TransactionTemplate transactions;
    private final VectorTaskPublisher taskPublisher;

    public KnowledgeCompilerEngine(
            JdbcMaps store,
            JsonMaps jsonMaps,
            ObjectMapper objectMapper,
            ModelGateway modelGateway,
            TransactionTemplate transactions,
            VectorTaskPublisher taskPublisher
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
        this.modelGateway = modelGateway;
        this.transactions = transactions;
        this.taskPublisher = taskPublisher;
    }

    public void handle(Map<String, Object> task) {
        String runId = value(task.get("run_id"));
        if (runId.isBlank()) {
            throw new IllegalArgumentException("knowledge compiler task requires run_id");
        }
        RunSpec run = null;
        try {
            run = transactions.execute(status -> claim(runId));
            if (run == null) {
                return;
            }
            BuildStats stats = build(run);
            RunSpec completedRun = run;
            transactions.executeWithoutResult(status -> publish(completedRun, stats));
            LOGGER.info("Knowledge compilation published: kb={}, run={}, version={}, artifacts={}",
                    run.kbId(), run.runId(), run.versionId(), stats.artifactCount);
        } catch (Exception exception) {
            String versionId = run == null ? "" : run.versionId();
            transactions.executeWithoutResult(status -> fail(runId, versionId));
            LOGGER.error("Knowledge compilation failed: run={}, failure_type={}",
                    runId, exception.getClass().getSimpleName());
        } finally {
            dispatchNext(run == null ? "" : run.kbId());
        }
    }

    private RunSpec claim(String runId) {
        int claimed = store.update("""
                UPDATE knowledge_compiler_runs
                   SET status = 'RUNNING', started_at = now(), error_message = ''
                 WHERE id = ? AND status = 'QUEUED'
                   AND NOT EXISTS (
                       SELECT 1 FROM knowledge_compiler_runs other
                        WHERE other.kb_id = knowledge_compiler_runs.kb_id
                          AND other.status = 'RUNNING' AND other.id <> knowledge_compiler_runs.id)
                """, runId);
        if (claimed == 0) {
            return null;
        }
        Map<String, Object> run = store.queryForObject("""
                SELECT id, kb_id, requested_node_ids, requested_release_ids, force_full, created_by
                  FROM knowledge_compiler_runs WHERE id = ?
                """, store.rowMapper(), runId);
        String kbId = value(run.get("kb_id"));
        store.queryForObject("SELECT id FROM knowledge_bases WHERE id = ? FOR UPDATE", String.class, kbId);
        String activeVersion = store.query(
                "SELECT active_version_id FROM knowledge_compiler_states WHERE kb_id = ?",
                (row, rowNumber) -> row.getString(1), kbId).stream().findFirst().orElse("");
        Long next = store.queryForObject(
                "SELECT COALESCE(max(version_no), 0) + 1 FROM knowledge_versions WHERE kb_id = ?",
                Long.class, kbId);
        String versionId = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_versions(
                    id, kb_id, version_no, previous_version_id, run_id, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, 'BUILDING', ?, now())
                """, versionId, kbId, next, blankToNull(activeVersion), runId, value(run.get("created_by")));
        store.update("UPDATE knowledge_compiler_runs SET version_id = ? WHERE id = ?", versionId, runId);
        return new RunSpec(
                runId,
                kbId,
                versionId,
                activeVersion,
                next == null ? 1L : next,
                stringList(run.get("requested_node_ids")),
                stringList(run.get("requested_release_ids")),
                Boolean.TRUE.equals(run.get("force_full")),
                value(run.get("created_by")));
    }

    private BuildStats build(RunSpec run) {
        BuildStats stats = new BuildStats();
        boolean full = run.forceFull() || run.activeVersionId().isBlank()
                || run.nodeIds().isEmpty() && run.releaseIds().isEmpty();
        Set<String> occupiedKeys = new HashSet<>();
        if (!full) {
            copyUnchangedArtifacts(run, occupiedKeys, stats);
        }
        List<Map<String, Object>> sources = sources(run, full);
        stats.sourceCount = sources.size();
        for (Map<String, Object> source : sources) {
            compileSource(run, source, occupiedKeys, stats);
        }
        createIndex(run, occupiedKeys, stats);
        detectConflicts(run, stats);
        lint(run, stats);
        stats.manifestHash = manifest(run.versionId());
        stats.artifactCount = store.queryForObject(
                "SELECT count(*) FROM knowledge_artifacts "
                        + "WHERE version_id = ? AND artifact_key <> '__index__' AND status <> 'CONFLICT'",
                Integer.class, run.versionId());
        captureSourceSnapshots(run, stats);
        buildShadowIndex(run, stats);
        return stats;
    }

    private void copyUnchangedArtifacts(RunSpec run, Set<String> occupiedKeys, BuildStats stats) {
        String changedNodes = KnowledgeCompilerService.postgresTextArray(run.nodeIds());
        List<Map<String, Object>> previous = store.query("""
                SELECT * FROM knowledge_artifacts
                 WHERE version_id = ? AND artifact_key <> '__index__'
                   AND NOT (source_node_ids && ?::text[])
                 ORDER BY artifact_key
                """, store.rowMapper(), run.activeVersionId(), changedNodes);
        for (Map<String, Object> artifact : previous) {
            String oldId = value(artifact.get("id"));
            String newId = UUID.randomUUID().toString();
            insertArtifact(
                    newId,
                    run,
                    value(artifact.get("artifact_key")),
                    value(artifact.get("type")),
                    value(artifact.get("title")),
                    value(artifact.get("summary")),
                    value(artifact.get("content")),
                    jsonItems(artifact.get("facts")),
                    jsonItems(artifact.get("entities")),
                    stringList(artifact.get("source_node_ids")),
                    stringList(artifact.get("source_release_ids")),
                    decimal(artifact.get("confidence")));
            occupiedKeys.add(value(artifact.get("artifact_key")));
            List<Map<String, Object>> dependencies = store.query("""
                    SELECT source_node_id, source_release_id, dependency_type
                      FROM knowledge_dependencies WHERE artifact_id = ?
                    """, store.rowMapper(), oldId);
            for (Map<String, Object> dependency : dependencies) {
                insertDependency(run, newId, value(dependency.get("source_node_id")),
                        value(dependency.get("source_release_id")), value(dependency.get("dependency_type")));
            }
            stats.reusedCount++;
        }
    }

    private List<Map<String, Object>> sources(RunSpec run, boolean full) {
        if (full) {
            return store.query("""
                    SELECT nr.id, nr.node_id, nr.name, nr.content, nr.meta, nr.updated_at,
                           links.nav_id, nr.parent_id, n.permissions, kr.id AS source_version
                      FROM kb_releases kr
                      JOIN kb_release_node_releases links ON links.release_id = kr.id
                      JOIN node_releases nr ON nr.id = links.node_release_id
                      LEFT JOIN nodes n ON n.id = nr.node_id
                     WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                       AND nr.type = 2
                     ORDER BY nr.position, nr.node_id
                    """, store.rowMapper(), run.kbId());
        }
        if (!run.releaseIds().isEmpty()) {
            return store.query("""
                    SELECT nr.id, nr.node_id, nr.name, nr.content, nr.meta, nr.updated_at,
                           links.nav_id, nr.parent_id, n.permissions, links.release_id AS source_version
                      FROM node_releases nr
                      LEFT JOIN kb_release_node_releases links ON links.node_release_id = nr.id
                      LEFT JOIN nodes n ON n.id = nr.node_id
                     WHERE nr.kb_id = ? AND nr.type = 2 AND nr.id = ANY(?::text[])
                     ORDER BY nr.updated_at
                    """, store.rowMapper(), run.kbId(),
                    KnowledgeCompilerService.postgresTextArray(run.releaseIds()));
        }
        return store.query("""
                SELECT DISTINCT ON (nr.node_id)
                       nr.id, nr.node_id, nr.name, nr.content, nr.meta, nr.updated_at,
                       link.nav_id, nr.parent_id, n.permissions, kr.id AS source_version
                  FROM kb_releases kr
                  JOIN kb_release_node_releases link ON link.release_id = kr.id
                  JOIN node_releases nr ON nr.id = link.node_release_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND nr.type = 2 AND nr.node_id = ANY(?::text[])
                 ORDER BY nr.node_id, kr.created_at DESC, nr.updated_at DESC
                """, store.rowMapper(), run.kbId(), KnowledgeCompilerService.postgresTextArray(run.nodeIds()));
    }

    private void compileSource(
            RunSpec run,
            Map<String, Object> source,
            Set<String> occupiedKeys,
            BuildStats stats
    ) {
        String nodeId = value(source.get("node_id"));
        String releaseId = value(source.get("id"));
        String title = value(source.get("name"));
        String content = value(source.get("content"));
        String summary = value(jsonMaps.jsonMap(source.get("meta")).get("summary"));
        List<String> chunks = KnowledgeCompilerSupport.chunks(content);
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            List<KnowledgeCompilerSupport.ArtifactDraft> drafts;
            try {
                String response = modelGateway.completeText(SYSTEM_PROMPT,
                        "来源标题：" + title + "\n来源分片：" + (chunkIndex + 1) + "/" + chunks.size()
                                + "\n\n原始证据：\n" + chunks.get(chunkIndex));
                drafts = KnowledgeCompilerSupport.parse(objectMapper, response);
                stats.modelCalls++;
            } catch (Exception exception) {
                String fallbackTitle = chunks.size() == 1 ? title : title + "（第" + (chunkIndex + 1) + "部分）";
                drafts = List.of(KnowledgeCompilerSupport.fallback(fallbackTitle, summary, chunks.get(chunkIndex)));
                stats.fallbackCount++;
            }
            for (int artifactIndex = 0; artifactIndex < drafts.size(); artifactIndex++) {
                KnowledgeCompilerSupport.ArtifactDraft draft = drafts.get(artifactIndex);
                String baseKey = nodeId + "/" + draft.key();
                String key = uniqueKey(baseKey, occupiedKeys);
                String artifactId = UUID.randomUUID().toString();
                insertArtifact(
                        artifactId,
                        run,
                        key,
                        draft.type(),
                        draft.title(),
                        draft.summary(),
                        draft.content(),
                        draft.facts(),
                        draft.entities(),
                        List.of(nodeId),
                        List.of(releaseId),
                        draft.confidence());
                insertDependency(run, artifactId, nodeId, releaseId, "derived_from");
                stats.compiledCount++;
            }
        }
    }

    private void createIndex(RunSpec run, Set<String> occupiedKeys, BuildStats stats) {
        List<Map<String, Object>> artifacts = store.query("""
                SELECT title, summary, source_node_ids, source_release_ids
                  FROM knowledge_artifacts WHERE version_id = ? AND artifact_key <> '__index__'
                 ORDER BY type, title
                """, store.rowMapper(), run.versionId());
        if (artifacts.isEmpty()) {
            insertLint(run, null, "NO_SOURCE", "ERROR", "没有可编译的已发布文档", Map.of());
            stats.errorCount++;
            return;
        }
        StringBuilder content = new StringBuilder("# 知识索引\n\n");
        Set<String> nodes = new LinkedHashSet<>();
        Set<String> releases = new LinkedHashSet<>();
        for (Map<String, Object> artifact : artifacts) {
            content.append("- **").append(value(artifact.get("title"))).append("**：")
                    .append(value(artifact.get("summary"))).append('\n');
            nodes.addAll(stringList(artifact.get("source_node_ids")));
            releases.addAll(stringList(artifact.get("source_release_ids")));
        }
        insertArtifact(
                UUID.randomUUID().toString(), run, "__index__", "reference", "知识索引",
                "当前知识版本包含 " + artifacts.size() + " 个可复用知识页面。",
                content.toString(), List.of(), List.of(), List.copyOf(nodes), List.copyOf(releases), 1d);
        occupiedKeys.add("__index__");
    }

    private void detectConflicts(RunSpec run, BuildStats stats) {
        List<Map<String, Object>> artifacts = store.query("""
                SELECT id, title, facts, source_node_ids, source_release_ids
                  FROM knowledge_artifacts
                 WHERE version_id = ? AND artifact_key <> '__index__'
                """, store.rowMapper(), run.versionId());
        Map<String, Claim> firstClaims = new HashMap<>();
        Set<String> emitted = new HashSet<>();
        for (Map<String, Object> artifact : artifacts) {
            for (Map<String, Object> fact : jsonItems(artifact.get("facts"))) {
                String key = KnowledgeCompilerSupport.factKey(fact);
                String object = KnowledgeCompilerSupport.normalize(fact.get("object"));
                if (key.equals("::") || object.isBlank()) {
                    continue;
                }
                Claim current = new Claim(value(artifact.get("id")), value(artifact.get("title")), fact,
                        stringList(artifact.get("source_node_ids")),
                        stringList(artifact.get("source_release_ids")));
                Claim previous = firstClaims.putIfAbsent(key, current);
                if (previous == null
                        || KnowledgeCompilerSupport.normalize(previous.fact().get("object")).equals(object)) {
                    continue;
                }
                // 同一份证据中的同谓词多值通常表示列表（例如“支持能力”“包含项目”），
                // 只有不同证据来源给出不同值时才定义为知识冲突。
                if (previous.artifactId().equals(current.artifactId())
                        || !java.util.Collections.disjoint(previous.releaseIds(), current.releaseIds())) {
                    continue;
                }
                String pair = key + "::" + List.of(
                        KnowledgeCompilerSupport.normalize(previous.fact().get("object")), object)
                        .stream().sorted().toList();
                if (!emitted.add(pair)) {
                    continue;
                }
                String conflictId = UUID.randomUUID().toString();
                store.update("""
                        INSERT INTO knowledge_conflicts(
                            id, kb_id, version_id, artifact_id, conflict_key, kind, severity,
                            claim_a, claim_b, status, created_at)
                        VALUES (?, ?, ?, ?, ?, 'FACT_VALUE_MISMATCH', 'WARNING',
                                ?::jsonb, ?::jsonb, 'OPEN', now())
                        """, conflictId, run.kbId(), run.versionId(), current.artifactId(), key,
                        jsonMaps.json(claimMap(previous)), jsonMaps.json(claimMap(current)));
                insertLint(run, current.artifactId(), "CONFLICTING_FACT", "WARNING",
                        "同一事实存在多个不同取值：" + key, Map.of("conflict_id", conflictId));
                store.update("UPDATE knowledge_artifacts SET status = 'CONFLICT' WHERE id IN (?, ?)",
                        previous.artifactId(), current.artifactId());
                stats.conflictCount++;
            }
        }
    }

    private void lint(RunSpec run, BuildStats stats) {
        List<Map<String, Object>> artifacts = store.query("""
                SELECT id, title, summary, content, facts, source_release_ids
                  FROM knowledge_artifacts
                 WHERE version_id = ? AND artifact_key <> '__index__'
                """, store.rowMapper(), run.versionId());
        Map<String, String> titles = new HashMap<>();
        for (Map<String, Object> artifact : artifacts) {
            String id = value(artifact.get("id"));
            if (value(artifact.get("content")).isBlank()) {
                insertLint(run, id, "EMPTY_CONTENT", "ERROR", "知识页面正文为空", Map.of());
                stats.errorCount++;
            }
            if (value(artifact.get("summary")).isBlank()) {
                insertLint(run, id, "EMPTY_SUMMARY", "WARNING", "知识页面缺少摘要", Map.of());
                stats.warningCount++;
            }
            if (stringList(artifact.get("source_release_ids")).isEmpty()) {
                insertLint(run, id, "MISSING_SOURCE", "ERROR", "知识页面无法追溯到原始证据", Map.of());
                stats.errorCount++;
            }
            String normalizedTitle = KnowledgeCompilerSupport.normalize(artifact.get("title"));
            String previous = titles.putIfAbsent(normalizedTitle, id);
            if (previous != null) {
                insertLint(run, id, "DUPLICATE_TITLE", "WARNING", "存在同名知识页面",
                        Map.of("other_artifact_id", previous));
                stats.warningCount++;
            }
            for (Map<String, Object> fact : jsonItems(artifact.get("facts"))) {
                if (value(fact.get("quote")).isBlank()) {
                    insertLint(run, id, "FACT_WITHOUT_QUOTE", "WARNING", "结构化事实缺少原文引句", fact);
                    stats.warningCount++;
                }
            }
        }
    }

    private void insertArtifact(
            String id,
            RunSpec run,
            String key,
            String type,
            String title,
            String summary,
            String content,
            List<Map<String, Object>> facts,
            List<Map<String, Object>> entities,
            List<String> nodeIds,
            List<String> releaseIds,
            double confidence
    ) {
        String supersedesId = "";
        if (!run.activeVersionId().isBlank()) {
            supersedesId = store.query("""
                    SELECT id FROM knowledge_artifacts
                     WHERE version_id = ? AND identity_key = ?
                     ORDER BY recorded_at DESC LIMIT 1
                    """, (row, rowNumber) -> row.getString(1), run.activeVersionId(), key)
                    .stream().findFirst().orElse("");
            if (supersedesId.isBlank()) {
                supersedesId = store.query("""
                        SELECT id FROM knowledge_artifacts
                         WHERE version_id = ? AND artifact_key = ?
                         ORDER BY recorded_at DESC LIMIT 1
                        """, (row, rowNumber) -> row.getString(1), run.activeVersionId(), key)
                        .stream().findFirst().orElse("");
            }
        }
        Map<String, Object> sourcePermissions = sourcePermissions(nodeIds);
        store.update("""
                INSERT INTO knowledge_artifacts(
                    id, kb_id, version_id, artifact_key, type, title, summary, content,
                    facts, entities, source_node_ids, source_release_ids, content_hash,
                    confidence, status, identity_key, valid_from, valid_to, recorded_at,
                    source_version, knowledge_version, supersedes_id, source_permissions,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::text[], ?::text[], ?, ?,
                        'CANDIDATE', ?, NULL, NULL, now(), ?, ?, ?, ?::jsonb, now(), now())
                """, id, run.kbId(), run.versionId(), key, type, title, summary, content,
                jsonMaps.json(Map.of("items", facts)), jsonMaps.json(Map.of("items", entities)),
                KnowledgeCompilerService.postgresTextArray(nodeIds),
                KnowledgeCompilerService.postgresTextArray(releaseIds),
                KnowledgeCompilerSupport.hash(content), confidence, key,
                releaseIds.isEmpty() ? "" : releaseIds.getFirst(), run.versionNo(),
                blankToNull(supersedesId), jsonMaps.json(sourcePermissions));
    }

    private Map<String, Object> sourcePermissions(List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = store.query(
                "SELECT id, permissions FROM nodes WHERE id = ANY(?::text[]) ORDER BY id",
                store.rowMapper(), KnowledgeCompilerService.postgresTextArray(nodeIds));
        Map<String, Object> sources = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            sources.put(value(row.get("id")), jsonMaps.jsonMap(row.get("permissions")));
        }
        return Map.of("sources", sources);
    }

    private void insertDependency(
            RunSpec run,
            String artifactId,
            String nodeId,
            String releaseId,
            String type
    ) {
        store.update("""
                INSERT INTO knowledge_dependencies(
                    id, kb_id, version_id, artifact_id, source_node_id, source_release_id,
                    dependency_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID().toString(), run.kbId(), run.versionId(), artifactId,
                nodeId, releaseId, type.isBlank() ? "derived_from" : type);
    }

    private void insertLint(
            RunSpec run,
            String artifactId,
            String rule,
            String severity,
            String message,
            Map<String, Object> details
    ) {
        store.update("""
                INSERT INTO knowledge_lint_issues(
                    id, kb_id, version_id, artifact_id, rule_code, severity, message,
                    details, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'OPEN', now())
                """, UUID.randomUUID().toString(), run.kbId(), run.versionId(), artifactId,
                rule, severity, message, jsonMaps.json(details));
    }

    private String manifest(String versionId) {
        List<Map<String, Object>> artifacts = store.query("""
                SELECT artifact_key, content_hash FROM knowledge_artifacts
                 WHERE version_id = ? AND artifact_key <> '__index__' AND status <> 'CONFLICT'
                 ORDER BY artifact_key
                """, store.rowMapper(), versionId);
        StringBuilder manifest = new StringBuilder();
        artifacts.forEach(artifact -> manifest.append(artifact.get("artifact_key"))
                .append(':').append(artifact.get("content_hash")).append('\n'));
        return KnowledgeCompilerSupport.hash(manifest.toString());
    }

    private void captureSourceSnapshots(RunSpec run, BuildStats stats) {
        Map<String, TemporalReleaseValidator.SourceSnapshot> current = new LinkedHashMap<>();
        List<Map<String, Object>> sources = store.query("""
                SELECT nr.node_id, nr.id AS node_release_id, kr.id AS source_version,
                       nr.name, links.nav_id, nr.parent_id, nr.content, n.permissions
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND nr.type = 2
                 ORDER BY nr.node_id
                """, store.rowMapper(), run.kbId());
        for (Map<String, Object> source : sources) {
            String nodeId = value(source.get("node_id"));
            Map<String, Object> permissions = jsonMaps.jsonMap(source.get("permissions"));
            TemporalReleaseValidator.SourceSnapshot snapshot = new TemporalReleaseValidator.SourceSnapshot(
                    nodeId,
                    value(source.get("node_release_id")),
                    value(source.get("source_version")),
                    value(source.get("name")),
                    value(source.get("nav_id")),
                    value(source.get("parent_id")),
                    KnowledgeCompilerSupport.hash(value(source.get("content"))),
                    KnowledgeCompilerSupport.hash(jsonMaps.json(permissions)),
                    permissions);
            current.put(nodeId, snapshot);
            store.update("""
                    INSERT INTO knowledge_source_snapshots(
                        id, kb_id, version_id, node_id, node_release_id, source_version,
                        name, nav_id, parent_id, content_hash, permission_hash, permissions, recorded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    ON CONFLICT(version_id, node_id) DO UPDATE SET
                        node_release_id = excluded.node_release_id,
                        source_version = excluded.source_version,
                        name = excluded.name,
                        nav_id = excluded.nav_id,
                        parent_id = excluded.parent_id,
                        content_hash = excluded.content_hash,
                        permission_hash = excluded.permission_hash,
                        permissions = excluded.permissions,
                        recorded_at = now()
                    """, UUID.randomUUID().toString(), run.kbId(), run.versionId(), snapshot.nodeId(),
                    snapshot.releaseId(), snapshot.sourceVersion(), snapshot.name(), snapshot.navId(),
                    snapshot.parentId(), snapshot.contentHash(), snapshot.permissionHash(),
                    jsonMaps.json(snapshot.permissions()));
        }
        Map<String, TemporalReleaseValidator.SourceSnapshot> previous = sourceSnapshots(run.activeVersionId());
        List<TemporalReleaseValidator.SourceChange> changes = TemporalReleaseValidator.diff(previous, current);
        for (TemporalReleaseValidator.SourceChange change : changes) {
            store.update("""
                    INSERT INTO knowledge_version_changes(
                        id, kb_id, version_id, previous_version_id, node_id, change_type,
                        before_snapshot, after_snapshot, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now())
                    """, UUID.randomUUID().toString(), run.kbId(), run.versionId(),
                    blankToNull(run.activeVersionId()), change.nodeId(), change.type(),
                    jsonMaps.json(change.before() == null ? Map.of() : change.before()),
                    jsonMaps.json(change.after() == null ? Map.of() : change.after()));
        }
        stats.previousSourceCount = previous.size();
        stats.currentSourceCount = current.size();
        stats.sourceChanges = changes;
        stats.deletedCount = (int) changes.stream().filter(change -> "DELETED".equals(change.type())).count();
        stats.renamedCount = (int) changes.stream().filter(change -> "RENAMED".equals(change.type())).count();
        stats.movedCount = (int) changes.stream().filter(change -> "MOVED".equals(change.type())).count();
        stats.permissionChangeCount = (int) changes.stream()
                .filter(change -> "PERMISSION_CHANGED".equals(change.type())).count();
    }

    private Map<String, TemporalReleaseValidator.SourceSnapshot> sourceSnapshots(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = store.query("""
                SELECT node_id, node_release_id, source_version, name, nav_id, parent_id,
                       content_hash, permission_hash, permissions
                  FROM knowledge_source_snapshots WHERE version_id = ? ORDER BY node_id
                """, store.rowMapper(), versionId);
        Map<String, TemporalReleaseValidator.SourceSnapshot> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String nodeId = value(row.get("node_id"));
            result.put(nodeId, new TemporalReleaseValidator.SourceSnapshot(
                    nodeId,
                    value(row.get("node_release_id")),
                    value(row.get("source_version")),
                    value(row.get("name")),
                    value(row.get("nav_id")),
                    value(row.get("parent_id")),
                    value(row.get("content_hash")),
                    value(row.get("permission_hash")),
                    jsonMaps.jsonMap(row.get("permissions"))));
        }
        return result;
    }

    private void buildShadowIndex(RunSpec run, BuildStats stats) {
        String indexId = UUID.randomUUID().toString();
        store.update("""
                INSERT INTO knowledge_shadow_indexes(
                    id, kb_id, version_id, status, manifest_hash, document_count,
                    permissions_hash, built_at, created_at)
                VALUES (?, ?, ?, 'BUILDING', ?, 0, '', NULL, now())
                """, indexId, run.kbId(), run.versionId(), stats.manifestHash);
        List<Map<String, Object>> artifacts = store.query("""
                SELECT id, artifact_key, identity_key, title, summary, content, content_hash,
                       source_node_ids, source_release_ids, source_permissions
                  FROM knowledge_artifacts
                 WHERE version_id = ? AND artifact_key <> '__index__' AND status <> 'CONFLICT'
                 ORDER BY artifact_key
                """, store.rowMapper(), run.versionId());
        StringBuilder permissionManifest = new StringBuilder();
        for (Map<String, Object> artifact : artifacts) {
            Map<String, Object> permissions = jsonMaps.jsonMap(artifact.get("source_permissions"));
            store.update("""
                    INSERT INTO knowledge_index_documents(
                        id, index_id, kb_id, version_id, artifact_id, identity_key, title, summary,
                        content, content_hash, source_node_ids, source_release_ids, permissions, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::jsonb, now())
                    """, UUID.randomUUID().toString(), indexId, run.kbId(), run.versionId(),
                    artifact.get("id"), value(artifact.get("identity_key")), value(artifact.get("title")),
                    value(artifact.get("summary")), value(artifact.get("content")),
                    value(artifact.get("content_hash")),
                    KnowledgeCompilerService.postgresTextArray(stringList(artifact.get("source_node_ids"))),
                    KnowledgeCompilerService.postgresTextArray(stringList(artifact.get("source_release_ids"))),
                    jsonMaps.json(permissions));
            permissionManifest.append(artifact.get("artifact_key")).append(':')
                    .append(KnowledgeCompilerSupport.hash(jsonMaps.json(permissions))).append('\n');
        }
        String indexManifest = indexManifest(indexId);
        String permissionsHash = KnowledgeCompilerSupport.hash(permissionManifest.toString());
        store.update("""
                UPDATE knowledge_shadow_indexes
                   SET status = 'READY', manifest_hash = ?, document_count = ?,
                       permissions_hash = ?, built_at = now()
                 WHERE id = ?
                """, indexManifest, artifacts.size(), permissionsHash, indexId);
        store.update("UPDATE knowledge_versions SET shadow_index_id = ? WHERE id = ?", indexId, run.versionId());
        stats.indexId = indexId;
        stats.indexDocumentCount = artifacts.size();
        stats.indexManifestHash = indexManifest;
    }

    private String indexManifest(String indexId) {
        List<Map<String, Object>> documents = store.query("""
                SELECT identity_key, content_hash FROM knowledge_index_documents
                 WHERE index_id = ? ORDER BY identity_key
                """, store.rowMapper(), indexId);
        StringBuilder manifest = new StringBuilder();
        documents.forEach(document -> manifest.append(document.get("identity_key"))
                .append(':').append(document.get("content_hash")).append('\n'));
        return KnowledgeCompilerSupport.hash(manifest.toString());
    }

    private void publish(RunSpec run, BuildStats stats) {
        Integer errors = store.queryForObject("""
                SELECT count(*) FROM knowledge_lint_issues
                 WHERE version_id = ? AND severity = 'ERROR' AND status = 'OPEN'
                """, Integer.class, run.versionId());
        String currentActiveVersion = store.query("""
                SELECT active_version_id FROM knowledge_compiler_states WHERE kb_id = ? FOR UPDATE
                """, (row, rowNumber) -> row.getString(1), run.kbId()).stream().findFirst().orElse("");
        TemporalReleaseValidator.ValidationReport validation = TemporalReleaseValidator.validate(
                stats.previousSourceCount,
                stats.currentSourceCount,
                stats.artifactCount,
                stats.indexDocumentCount,
                errors == null ? 0 : errors,
                stats.manifestHash,
                stats.indexManifestHash,
                stats.sourceChanges);
        List<TemporalReleaseValidator.ValidationCheck> checks = new ArrayList<>(validation.checks());
        boolean baseStable = currentActiveVersion.equals(run.activeVersionId());
        checks.add(new TemporalReleaseValidator.ValidationCheck(
                "BASE_VERSION_STABLE",
                baseStable ? "INFO" : "ERROR",
                baseStable ? "PASSED" : "FAILED",
                baseStable ? "构建期间线上版本未发生变化" : "构建期间线上版本已变化，禁止覆盖更新的版本",
                Map.of("expected_base", run.activeVersionId(), "actual_base", currentActiveVersion)));
        boolean publishable = validation.publishable() && baseStable;
        for (TemporalReleaseValidator.ValidationCheck check : checks) {
            store.update("""
                    INSERT INTO knowledge_release_validations(
                        id, kb_id, version_id, check_code, severity, status, message, metrics, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    """, UUID.randomUUID().toString(), run.kbId(), run.versionId(), check.code(),
                    check.severity(), check.status(), check.message(), jsonMaps.json(check.metrics()));
        }
        Map<String, Object> validationReport = new LinkedHashMap<>();
        validationReport.put("publishable", publishable);
        validationReport.put("checks", checks);
        validationReport.put("deleted_count", validation.deletedCount());
        validationReport.put("permission_change_count", validation.permissionChangeCount());
        validationReport.put("deletion_ratio", validation.deletionRatio());
        store.update("""
                UPDATE knowledge_versions
                   SET validation_status = ?, validation_report = ?::jsonb
                 WHERE id = ?
                """, publishable ? "PASSED" : "FAILED", jsonMaps.json(validationReport), run.versionId());
        store.update("""
                UPDATE knowledge_shadow_indexes
                   SET status = ?, validation_report = ?::jsonb, validated_at = now()
                 WHERE id = ?
                """, publishable ? "VALIDATED" : "FAILED", jsonMaps.json(validationReport), stats.indexId);
        if (!publishable) {
            throw new ApiException("知识发布门禁未通过");
        }

        Map<String, Object> summary = stats.toMap();
        summary.put("validation", validationReport);
        String previousIndexId = store.query("""
                SELECT active_index_id FROM knowledge_compiler_states WHERE kb_id = ?
                """, (row, rowNumber) -> row.getString(1), run.kbId()).stream().findFirst().orElse("");
        if (!run.activeVersionId().isBlank()) {
            store.update("""
                    UPDATE knowledge_artifacts
                       SET status = 'EXPIRED', valid_to = now(), updated_at = now()
                     WHERE version_id = ? AND status IN ('EFFECTIVE', 'CONFLICT')
                    """, run.activeVersionId());
            store.update("""
                    UPDATE knowledge_version_activations SET valid_to = now()
                     WHERE kb_id = ? AND valid_to IS NULL
                    """, run.kbId());
        }
        store.update("""
                UPDATE knowledge_artifacts
                   SET status = CASE WHEN status = 'CONFLICT' THEN 'CONFLICT' ELSE 'EFFECTIVE' END,
                       valid_from = now(), valid_to = NULL, updated_at = now()
                 WHERE version_id = ?
                """, run.versionId());
        store.update("""
                UPDATE knowledge_versions
                   SET status = 'PUBLISHED', manifest_hash = ?, stats = ?::jsonb,
                       published_at = now(), activated_at = now()
                 WHERE id = ? AND status = 'BUILDING'
                """, stats.manifestHash, jsonMaps.json(summary), run.versionId());
        if (!previousIndexId.isBlank()) {
            store.update("""
                    UPDATE knowledge_shadow_indexes
                       SET status = 'RETIRED', retired_at = now()
                     WHERE id = ? AND id <> ?
                    """, previousIndexId, stats.indexId);
        }
        store.update("""
                UPDATE knowledge_shadow_indexes
                   SET status = 'ACTIVE', activated_at = now(), retired_at = NULL
                 WHERE id = ?
                """, stats.indexId);
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
                """, run.kbId(), run.versionId(), blankToNull(run.activeVersionId()),
                stats.indexId, blankToNull(previousIndexId));
        store.update("""
                INSERT INTO knowledge_version_activations(
                    id, kb_id, version_id, previous_version_id, valid_from,
                    reason, activated_by, created_at)
                VALUES (?, ?, ?, ?, now(), 'publish', ?, now())
                """, UUID.randomUUID().toString(), run.kbId(), run.versionId(),
                blankToNull(run.activeVersionId()), run.createdBy());
        store.update("""
                UPDATE knowledge_compiler_runs
                   SET status = 'SUCCEEDED', stats = ?::jsonb, completed_at = now()
                 WHERE id = ?
                """, jsonMaps.json(summary), run.runId());
    }

    private void fail(String runId, String versionId) {
        if (!versionId.isBlank()) {
            store.update("UPDATE knowledge_versions SET status = 'FAILED' WHERE id = ? AND status = 'BUILDING'",
                    versionId);
        }
        store.update("""
                UPDATE knowledge_compiler_runs
                   SET status = 'FAILED',
                       error_message = '知识编译失败，请检查模型配置、源文档与质量检查结果',
                       completed_at = now()
                 WHERE id = ? AND status IN ('QUEUED', 'RUNNING')
                """, runId);
    }

    private void dispatchNext(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return;
        }
        List<String> queued = store.query("""
                SELECT id FROM knowledge_compiler_runs
                 WHERE kb_id = ? AND status = 'QUEUED'
                 ORDER BY created_at LIMIT 1
                """, (row, rowNumber) -> row.getString(1), kbId);
        if (!queued.isEmpty()) {
            taskPublisher.compileNow(kbId, queued.getFirst());
        }
    }

    private static Map<String, Object> claimMap(Claim claim) {
        return Map.of(
                "artifact_id", claim.artifactId(),
                "artifact_title", claim.artifactTitle(),
                "fact", claim.fact(),
                "source_node_ids", claim.nodeIds(),
                "source_release_ids", claim.releaseIds());
    }

    private static String uniqueKey(String requested, Set<String> occupied) {
        String key = requested;
        int suffix = 2;
        while (!occupied.add(key)) {
            key = requested + "-" + suffix++;
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonItems(Object value) {
        Object items = jsonMaps.jsonMap(value).get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, entry) -> converted.put(String.valueOf(key), entry));
                result.add(converted);
            }
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Object[] array) {
            return java.util.Arrays.stream(array).map(KnowledgeCompilerEngine::value)
                    .filter(item -> !item.isBlank()).distinct().toList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(KnowledgeCompilerEngine::value)
                    .filter(item -> !item.isBlank()).distinct().toList();
        }
        return List.of();
    }

    private static double decimal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null ? 0d : new BigDecimal(String.valueOf(value)).doubleValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record RunSpec(
            String runId,
            String kbId,
            String versionId,
            String activeVersionId,
            long versionNo,
            List<String> nodeIds,
            List<String> releaseIds,
            boolean forceFull,
            String createdBy
    ) {
    }

    private record Claim(
            String artifactId,
            String artifactTitle,
            Map<String, Object> fact,
            List<String> nodeIds,
            List<String> releaseIds
    ) {
    }

    private static final class BuildStats {
        private int sourceCount;
        private int artifactCount;
        private int compiledCount;
        private int reusedCount;
        private int modelCalls;
        private int fallbackCount;
        private int conflictCount;
        private int warningCount;
        private int errorCount;
        private int previousSourceCount;
        private int currentSourceCount;
        private int deletedCount;
        private int renamedCount;
        private int movedCount;
        private int permissionChangeCount;
        private int indexDocumentCount;
        private String manifestHash = "";
        private String indexManifestHash = "";
        private String indexId = "";
        private List<TemporalReleaseValidator.SourceChange> sourceChanges = List.of();

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source_count", sourceCount);
            result.put("artifact_count", artifactCount);
            result.put("compiled_count", compiledCount);
            result.put("reused_count", reusedCount);
            result.put("model_calls", modelCalls);
            result.put("fallback_count", fallbackCount);
            result.put("degraded", fallbackCount > 0);
            result.put("conflict_count", conflictCount);
            result.put("warning_count", warningCount);
            result.put("error_count", errorCount);
            result.put("previous_source_count", previousSourceCount);
            result.put("current_source_count", currentSourceCount);
            result.put("deleted_count", deletedCount);
            result.put("renamed_count", renamedCount);
            result.put("moved_count", movedCount);
            result.put("permission_change_count", permissionChangeCount);
            result.put("index_document_count", indexDocumentCount);
            result.put("shadow_index_id", indexId);
            result.put("manifest_hash", manifestHash);
            result.put("index_manifest_hash", indexManifestHash);
            return result;
        }
    }
}
