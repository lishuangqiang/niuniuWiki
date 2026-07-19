package com.chaitin.niuniuwiki.node;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.prompt.PromptService;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封装文档节点相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-27
 */
@Service
public class NodeService {

    private static final double MAX_POSITION = 1e38;
    private static final double MIN_GAP = 1e-5;

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final VectorTaskPublisher vectorTasks;
    private final ModelGateway modelGateway;
    private final PromptService promptService;

    @Autowired
    public NodeService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            AuthService authService,
            VectorTaskPublisher vectorTasks,
            ModelGateway modelGateway,
            PromptService promptService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.vectorTasks = vectorTasks;
        this.modelGateway = modelGateway;
        this.promptService = promptService;
    }

    /**
     * 保留给独立迁移测试和旧嵌入调用的构造入口。
     */
    public NodeService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            AuthService authService,
            VectorTaskPublisher vectorTasks,
            ModelGateway modelGateway
    ) {
        this(store, jsonMaps, authService, vectorTasks, modelGateway, null);
    }

    public List<Map<String, Object>> list(String kbId, String navId, String search) {
        require(kbId);
        StringBuilder sql = new StringBuilder("""
                SELECT n.id, n.nav_id, n.type, n.status, n.rag_info, n.name, n.parent_id, n.position,
                       n.created_at, n.edit_time AS updated_at, n.creator_id, n.editor_id, n.permissions,
                       n.meta->>'summary' AS summary, n.meta->>'emoji' AS emoji,
                       n.meta->>'content_type' AS content_type, cu.account AS creator, eu.account AS editor,
                       (SELECT nr.publisher_id FROM node_releases nr WHERE nr.node_id = n.id
                        ORDER BY nr.updated_at DESC LIMIT 1) AS publisher_id
                  FROM nodes n
                  LEFT JOIN users cu ON n.creator_id = cu.id
                  LEFT JOIN users eu ON n.editor_id = eu.id
                 WHERE n.kb_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        if (navId != null && !navId.isBlank()) {
            sql.append(" AND n.nav_id = ?");
            args.add(navId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (n.name ILIKE ? OR n.content ILIKE ?)");
            args.add("%" + search + "%");
            args.add("%" + search + "%");
        }
        sql.append(" ORDER BY n.position");
        return store.query(sql.toString(), store.rowMapper(), args.toArray());
    }

    public List<Map<String, Object>> listGrouped(
            String kbId,
            List<String> navIds,
            String search,
            String status
    ) {
        require(kbId);
        List<Map<String, Object>> nodes = list(kbId, null, search);
        nodes = switch (status == null ? "" : status) {
            case "released" -> nodes.stream().filter(node -> number(node.get("status")) == 2).toList();
            case "unpublished" -> nodes.stream().filter(node -> number(node.get("status")) != 2).toList();
            case "unstudied" -> nodes.stream().filter(this::unstudied).toList();
            default -> nodes;
        };
        Set<String> selected = navIds == null ? Set.of() : new HashSet<>(navIds);
        List<Map<String, Object>> navs = store.query(
                "SELECT id, name, position FROM navs WHERE kb_id = ? ORDER BY position",
                store.rowMapper(), kbId);
        Map<String, Map<String, Object>> latestReleasedNav = new HashMap<>();
        List<Map<String, Object>> released = store.query(
                "SELECT DISTINCT ON (nr.nav_id) nr.nav_id AS id, nr.name, nr.position FROM nav_releases nr "
                        + "JOIN kb_releases kr ON nr.release_id = kr.id WHERE nr.kb_id = ? "
                        + "ORDER BY nr.nav_id, kr.created_at DESC",
                store.rowMapper(), kbId);
        released.forEach(nav -> latestReleasedNav.put(String.valueOf(nav.get("id")), nav));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> nav : navs) {
            String id = String.valueOf(nav.get("id"));
            if (!selected.isEmpty() && !selected.contains(id)) {
                continue;
            }
            List<Map<String, Object>> items = nodes.stream()
                    .filter(node -> id.equals(String.valueOf(node.get("nav_id"))))
                    .toList();
            if (search != null && !search.isBlank() && items.isEmpty()) {
                continue;
            }
            Map<String, Object> release = latestReleasedNav.get(id);
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("nav_name", nav.get("name"));
            group.put("nav_id", id);
            group.put("position", nav.get("position"));
            group.put("count", items.size());
            group.put("is_released", release != null
                    && String.valueOf(nav.get("name")).equals(String.valueOf(release.get("name")))
                    && Double.compare(decimal(nav.get("position")), decimal(release.get("position"))) == 0);
            group.put("list", items);
            result.add(group);
        }
        return result;
    }

    public Map<String, Object> stats(String kbId) {
        require(kbId);
        long unpublished = store.queryForObject(
                "SELECT count(*) FROM nodes WHERE kb_id = ? AND type = 2 AND status <> 2", Long.class, kbId);
        long unstudied = store.queryForObject(
                "SELECT count(*) FROM nodes WHERE kb_id = ? AND type = 2 "
                        + "AND UPPER(COALESCE(rag_info->>'status', '')) "
                        + "NOT IN ('SUCCESS', 'SUCCEEDED', 'COMPLETED')",
                Long.class, kbId);
        long navCount = listGrouped(kbId, List.of(), "", "").stream()
                .filter(group -> !Boolean.TRUE.equals(group.get("is_released"))).count();
        return Map.of(
                "unpublished_count", unpublished,
                "unstudied_count", unstudied,
                "unreleased_nav_count", navCount);
    }

    @Transactional
    public String create(NodeDtos.CreateRequest request) {
        require(request.kbId());
        if (request.type() != 1 && request.type() != 2) {
            throw new ApiException("type must be 1 or 2");
        }
        ensureNav(request.kbId(), request.navId());
        double position = request.position() == null
                ? positionAfterLast(request.kbId(), parent(request.parentId()))
                : request.position();
        validatePosition(position);
        String id = UUID.randomUUID().toString();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", value(request.summary()));
        meta.put("emoji", value(request.emoji()));
        meta.put("content_type", value(request.contentType()));
        Map<String, Object> ragInfo = Map.of("status", "PENDING", "message", "");
        Map<String, Object> permissions = Map.of("answerable", "open", "visitable", "open", "visible", "open");
        String userId = AuthContext.get().userId();
        store.update(
                "INSERT INTO nodes(id, kb_id, nav_id, type, status, rag_info, name, content, meta, parent_id, "
                        + "position, creator_id, editor_id, edit_time, permissions, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 0, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, ?, now(), ?::jsonb, now(), now())",
                id, request.kbId(), request.navId(), request.type(), jsonMaps.json(ragInfo), request.name(),
                value(request.content()), jsonMaps.json(meta), parent(request.parentId()), position, userId, userId,
                jsonMaps.json(permissions));
        return id;
    }

    public Map<String, Object> detail(String kbId, String id, String format) {
        require(kbId);
        Map<String, Object> node = store.queryForObject("""
                SELECT n.*, creator.account AS creator_account, editor.account AS editor_account,
                       release.publisher_id, publisher.account AS publisher_account,
                       COALESCE(stats.pv, 0) AS pv
                  FROM nodes n
                  LEFT JOIN users creator ON creator.id = n.creator_id
                  LEFT JOIN users editor ON editor.id = n.editor_id
                  LEFT JOIN LATERAL (
                       SELECT publisher_id FROM node_releases
                        WHERE node_id = n.id ORDER BY updated_at DESC LIMIT 1
                  ) release ON true
                  LEFT JOIN users publisher ON publisher.id = release.publisher_id
                  LEFT JOIN node_stats stats ON stats.node_id = n.id
                 WHERE n.id = ? AND n.kb_id = ?
                """, store.rowMapper(), id, kbId);
        return node;
    }

    @Transactional
    public void update(NodeDtos.UpdateRequest request) {
        require(request.kbId());
        Map<String, Object> current = detail(request.kbId(), request.id(), "raw");
        Map<String, Object> meta = jsonMaps.jsonMap(current.get("meta"));
        boolean contentChanged = false;
        if (request.emoji() != null) {
            meta.put("emoji", request.emoji());
            contentChanged = true;
        }
        if (request.summary() != null) {
            meta.put("summary", request.summary());
            contentChanged = true;
        }
        if (request.contentType() != null && value(meta.get("content_type")).isBlank()) {
            meta.put("content_type", request.contentType());
            contentChanged = true;
        }
        if (request.navId() != null) {
            ensureNav(request.kbId(), request.navId());
        }
        if (request.position() != null) {
            validatePosition(request.position());
        }
        int status = number(current.get("status"));
        boolean changed = contentChanged
                || request.name() != null && !request.name().equals(current.get("name"))
                || request.content() != null && !request.content().equals(current.get("content"))
                || request.navId() != null && !request.navId().equals(current.get("nav_id"))
                || request.position() != null && Double.compare(request.position(), decimal(current.get("position"))) != 0;
        store.update(
                "UPDATE nodes SET name = COALESCE(?, name), content = COALESCE(?, content), "
                        + "nav_id = COALESCE(?, nav_id), position = COALESCE(?, position), meta = ?::jsonb, "
                        + "editor_id = ?, status = ?, edit_time = CASE WHEN ? THEN now() ELSE edit_time END, updated_at = now() "
                        + "WHERE id = ? AND kb_id = ?",
                request.name(), request.content(), request.navId(), request.position(), jsonMaps.json(meta),
                AuthContext.get().userId(), changed && status != 0 ? 1 : status, changed, request.id(), request.kbId());
    }

    @Transactional
    public void action(NodeDtos.ActionRequest request) {
        require(request.kbId());
        if (!"delete".equals(request.action())) {
            throw new ApiException("unsupported node action");
        }
        request.ids().forEach(id -> deleteTree(request.kbId(), id));
    }

    @Transactional
    public void move(NodeDtos.MoveRequest request) {
        require(request.kbId());
        String parentId = parent(request.parentId());
        double previous = nodePosition(request.kbId(), request.prevId(), 0);
        double next = nodePosition(request.kbId(), request.nextId(), MAX_POSITION);
        if (next - previous < MIN_GAP) {
            reorder(request.kbId(), parentId);
            previous = nodePosition(request.kbId(), request.prevId(), 0);
            next = nodePosition(request.kbId(), request.nextId(), MAX_POSITION);
        }
        store.update(
                "UPDATE nodes SET parent_id = ?, position = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                parentId, previous + (next - previous) / 2, request.id(), request.kbId());
    }

    @Transactional
    public void batchMove(NodeDtos.BatchMoveRequest request) {
        require(request.kbId());
        double position = positionAfterLast(request.kbId(), parent(request.parentId()));
        double increment = Math.max(1000, (MAX_POSITION - position) / (request.ids().size() + 1));
        for (int index = 0; index < request.ids().size(); index++) {
            store.update(
                    "UPDATE nodes SET parent_id = ?, position = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                    parent(request.parentId()), position + increment * index, request.ids().get(index), request.kbId());
        }
    }

    @Transactional
    public void moveNav(NodeDtos.MoveNavRequest request) {
        require(request.kbId());
        ensureNav(request.kbId(), request.navId());
        for (String id : request.ids()) {
            store.update("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM nodes WHERE id = ? AND kb_id = ?
                        UNION ALL
                        SELECT n.id FROM nodes n JOIN tree t ON n.parent_id = t.id WHERE n.kb_id = ?
                    )
                    UPDATE nodes SET nav_id = ?, updated_at = now() WHERE id IN (SELECT id FROM tree)
                    """, id, request.kbId(), request.kbId(), request.navId());
        }
    }

    public List<Map<String, Object>> recommend(String kbId, List<String> nodeIds, List<String> navIds) {
        require(kbId);
        if ((nodeIds == null || nodeIds.isEmpty()) && (navIds == null || navIds.isEmpty())) {
            throw new ApiException("node_ids or nav_ids is required");
        }
        List<Map<String, Object>> released = releasedNodes(kbId);
        Set<String> wantedNodes = nodeIds == null ? Set.of() : new HashSet<>(nodeIds);
        Set<String> wantedNavs = navIds == null ? Set.of() : new HashSet<>(navIds);
        Map<String, List<Map<String, Object>>> children = new HashMap<>();
        released.forEach(node -> children.computeIfAbsent(value(node.get("parent_id")), ignored -> new ArrayList<>()).add(node));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : released) {
            if ((!wantedNavs.isEmpty() && wantedNavs.contains(value(node.get("nav_id"))))
                    || (!wantedNodes.isEmpty() && wantedNodes.contains(value(node.get("id"))))) {
                Map<String, Object> copy = new LinkedHashMap<>(node);
                if (number(node.get("type")) == 1) {
                    copy.put("recommend_nodes", children.getOrDefault(value(node.get("id")), List.of()));
                }
                result.add(copy);
            }
        }
        if (!wantedNodes.isEmpty() && wantedNavs.isEmpty()) {
            result.sort((left, right) -> Integer.compare(
                    nodeIds.indexOf(value(left.get("id"))), nodeIds.indexOf(value(right.get("id")))));
        }
        return result;
    }

    public Map<String, Object> permission(String kbId, String id) {
        require(kbId);
        Map<String, Object> node = detail(kbId, id, "raw");
        List<Map<String, Object>> groups = store.query("""
                SELECT nag.node_id, nag.auth_group_id, nag.perm, ag.name, ag.kb_id, ag.auth_ids
                  FROM node_auth_groups nag JOIN auth_groups ag ON nag.auth_group_id = ag.id
                 WHERE nag.node_id = ?
                """, store.rowMapper(), id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("permissions", node.get("permissions"));
        result.put("answerable_groups", groups.stream().filter(item -> "answerable".equals(item.get("perm"))).toList());
        result.put("visitable_groups", groups.stream().filter(item -> "visitable".equals(item.get("perm"))).toList());
        result.put("visible_groups", groups.stream().filter(item -> "visible".equals(item.get("perm"))).toList());
        return result;
    }

    @Transactional
    public void editPermission(NodeDtos.PermissionEditRequest request) {
        require(request.kbId());
        for (String id : request.ids()) {
            if (request.permissions() != null) {
                store.update(
                        "UPDATE nodes SET permissions = ?::jsonb WHERE id = ? AND kb_id = ?",
                        jsonMaps.json(request.permissions()), id, request.kbId());
            }
            replaceGroups(id, "answerable", request.answerableGroups());
            replaceGroups(id, "visitable", request.visitableGroups());
            replaceGroups(id, "visible", request.visibleGroups());
            List<String> documentIds = store.query(
                    "SELECT doc_id FROM node_releases WHERE node_id = ? AND doc_id <> ''",
                    (rs, rowNum) -> rs.getString(1), id);
            documentIds.forEach(documentId -> vectorTasks.groupsAfterCommit(
                    request.kbId(), documentId, request.answerableGroups()));
        }
    }

    @Transactional
    public void restudy(NodeDtos.RestudyRequest request) {
        require(request.kbId());
        Integer releases = store.queryForObject(
                "SELECT count(*) FROM node_releases WHERE kb_id = ? AND node_id = ANY (?::text[])",
                Integer.class, request.kbId(), request.nodeIds().toArray(String[]::new));
        if (releases == null || releases == 0) {
            throw new ApiException("文档未首次发布，无法重新学习");
        }
        store.update(
                "UPDATE nodes SET rag_info = '{\"status\":\"PENDING\",\"message\":\"\"}'::jsonb "
                        + "WHERE kb_id = ? AND id = ANY (?::text[])",
                request.kbId(), request.nodeIds().toArray(String[]::new));
        for (String nodeId : request.nodeIds()) {
            store.query(
                    "SELECT id FROM node_releases WHERE kb_id = ? AND node_id = ? ORDER BY updated_at DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString(1), request.kbId(), nodeId)
                    .stream().findFirst()
                    .ifPresent(releaseId -> vectorTasks.upsertAfterCommit(request.kbId(), releaseId, nodeId));
        }
    }

    @Transactional
    public void summarize(NodeDtos.SummaryRequest request) {
        require(request.kbId());
        request.ids().forEach(id -> vectorTasks.summaryAfterCommit(request.kbId(), id));
    }

    public String streamSummary(NodeDtos.SummaryRequest request) {
        if (request.ids().size() != 1) {
            throw new ApiException("stream summary only supports single node");
        }
        require(request.kbId());
        Map<String, Object> node = detail(request.kbId(), request.ids().getFirst(), "raw");
        return modelGateway.completeText(
                promptService == null
                        ? PromptService.DEFAULT_SUMMARY_PROMPT
                        : String.valueOf(promptService.getInternal(request.kbId()).get("summary_content")),
                "标题：" + value(node.get("name")) + "\n\n正文：" + value(node.get("content")));
    }

    private List<Map<String, Object>> releasedNodes(String kbId) {
        return store.query("""
                SELECT nr.node_id AS id, links.nav_id, nav.name AS nav_name, nr.name, nr.type,
                       nr.meta->>'summary' AS summary, nr.parent_id, nr.position,
                       nr.meta->>'emoji' AS emoji, n.permissions
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                 ORDER BY nr.position
                """, store.rowMapper(), kbId);
    }

    private void replaceGroups(String nodeId, String permission, List<Integer> groupIds) {
        if (groupIds == null) {
            return;
        }
        store.update("DELETE FROM node_auth_groups WHERE node_id = ? AND perm = ?", nodeId, permission);
        for (Integer groupId : groupIds) {
            store.update(
                    "INSERT INTO node_auth_groups(node_id, auth_group_id, perm, created_at) VALUES (?, ?, ?, now())",
                    nodeId, groupId, permission);
        }
    }

    private void deleteTree(String kbId, String id) {
        List<String> documentIds = store.query("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM nodes WHERE id = ? AND kb_id = ?
                    UNION ALL
                    SELECT n.id FROM nodes n JOIN tree t ON n.parent_id = t.id WHERE n.kb_id = ?
                ) SELECT doc_id FROM node_releases WHERE node_id IN (SELECT id FROM tree) AND doc_id <> ''
                """, (rs, rowNum) -> rs.getString(1), id, kbId, kbId);
        store.update("""
                INSERT INTO node_release_backup(
                    id, kb_id, publisher_id, editor_id, node_id, doc_id, type, name, meta, content,
                    position, parent_id, deleted_at, created_at, updated_at)
                SELECT nr.id, nr.kb_id, nr.publisher_id, nr.editor_id, nr.node_id, nr.doc_id, nr.type,
                       nr.name, nr.meta, nr.content, nr.position, nr.parent_id, now(), nr.created_at, nr.updated_at
                  FROM node_releases nr
                 WHERE nr.node_id IN (
                    WITH RECURSIVE tree AS (
                        SELECT id FROM nodes WHERE id = ? AND kb_id = ?
                        UNION ALL
                        SELECT n.id FROM nodes n JOIN tree t ON n.parent_id = t.id WHERE n.kb_id = ?
                    ) SELECT id FROM tree
                 ) ON CONFLICT DO NOTHING
                """, id, kbId, kbId);
        store.update("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM nodes WHERE id = ? AND kb_id = ?
                    UNION ALL
                    SELECT n.id FROM nodes n JOIN tree t ON n.parent_id = t.id WHERE n.kb_id = ?
                ) DELETE FROM node_releases WHERE node_id IN (SELECT id FROM tree)
                """, id, kbId, kbId);
        store.update("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM nodes WHERE id = ? AND kb_id = ?
                    UNION ALL
                    SELECT n.id FROM nodes n JOIN tree t ON n.parent_id = t.id WHERE n.kb_id = ?
                ) DELETE FROM nodes WHERE id IN (SELECT id FROM tree)
                """, id, kbId, kbId);
        documentIds.forEach(documentId -> vectorTasks.deleteAfterCommit(kbId, documentId));
    }

    private void ensureNav(String kbId, String navId) {
        Integer count = store.queryForObject(
                "SELECT count(*) FROM navs WHERE id = ? AND kb_id = ?",
                Integer.class, navId, kbId);
        if (count == null || count == 0) {
            throw new ApiException("invalid nav_id");
        }
    }

    private double positionAfterLast(String kbId, String parentId) {
        Double max = store.queryForObject(
                "SELECT COALESCE(MAX(position), 0) FROM nodes WHERE kb_id = ? AND COALESCE(parent_id, '') = ?",
                Double.class, kbId, parentId);
        return max + (MAX_POSITION - max) / 2;
    }

    private double nodePosition(String kbId, String id, double defaultValue) {
        if (id == null || id.isBlank()) {
            return defaultValue;
        }
        return store.queryForObject(
                "SELECT position FROM nodes WHERE id = ? AND kb_id = ?",
                Double.class, id, kbId);
    }

    private void reorder(String kbId, String parentId) {
        List<String> ids = store.query(
                "SELECT id FROM nodes WHERE kb_id = ? AND COALESCE(parent_id, '') = ? ORDER BY position",
                (rs, rowNum) -> rs.getString(1), kbId, parentId);
        for (int index = 0; index < ids.size(); index++) {
            store.update("UPDATE nodes SET position = ? WHERE id = ?", 1000d + index * 1000d, ids.get(index));
        }
    }

    private boolean unstudied(Map<String, Object> node) {
        Map<String, Object> rag = jsonMaps.jsonMap(node.get("rag_info"));
        return !isLearnedStatus(value(rag.get("status")));
    }

    static boolean isLearnedStatus(String status) {
        String normalized = value(status).trim().toUpperCase(Locale.ROOT);
        return "SUCCESS".equals(normalized)
                || "SUCCEEDED".equals(normalized)
                || "COMPLETED".equals(normalized);
    }

    private void require(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
    }

    private void validatePosition(double position) {
        if (position < 0 || position > MAX_POSITION) {
            throw new ApiException("specified position is out of range");
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private static String parent(String value) {
        return value == null ? "" : value;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
