package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 封装公开访问相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-07-02
 */
@Service
public class PublicContentService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final ShareAccessService accessService;

    public PublicContentService(JdbcMaps store, JsonMaps jsonMaps, ShareAccessService accessService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.accessService = accessService;
    }

    public List<Map<String, Object>> navs(String kbId, HttpSession session) {
        accessService.authorize(kbId, session);
        return store.query("""
                SELECT nav_id AS id, name, position, created_at, created_at AS updated_at
                  FROM nav_releases
                 WHERE release_id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                 ORDER BY position
                """, store.rowMapper(), kbId);
    }

    public List<Map<String, Object>> nodeGroups(String kbId, HttpSession session) {
        accessService.authorize(kbId, session);
        List<Map<String, Object>> navs = navs(kbId, session);
        List<Map<String, Object>> nodes = releasedNodes(kbId);
        Long authId = authId(session);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> nav : navs) {
            String navId = value(nav.get("id"));
            List<Map<String, Object>> items = nodes.stream()
                    .filter(node -> navId.equals(value(node.get("nav_id"))))
                    .filter(node -> visible(node, authId, "visible"))
                    .map(this::listItem)
                    .toList();
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("nav_name", nav.get("name"));
            group.put("nav_id", navId);
            group.put("position", nav.get("position"));
            group.put("count", items.size());
            group.put("list", items);
            result.add(group);
        }
        return result;
    }

    public Map<String, Object> nodeDetail(String kbId, String id, HttpSession session) {
        accessService.authorize(kbId, session);
        Long authId = authId(session);
        Map<String, Object> node = releasedNodes(kbId).stream()
                .filter(item -> id.equals(value(item.get("id"))))
                .findFirst()
                .orElseThrow(() -> new ApiException("Not Found"));
        if (!visible(node, authId, "visitable")) {
            throw new ApiException(HttpStatus.OK, "Permission Denied", 40003);
        }
        Map<String, Object> result = new LinkedHashMap<>(node);
        result.put("list", number(node.get("type")) == 1 ? childrenTree(kbId, id, authId) : List.of());
        Long pv = store.query(
                "SELECT pv FROM node_stats WHERE node_id = ?",
                (rs, rowNum) -> rs.getLong(1), id).stream().findFirst().orElse(0L);
        result.put("pv", pv);
        return result;
    }

    /**
     * 读取问答发生时引用的原始发布版本，同时始终按照节点当前权限进行授权。
     * 这样既能复现历史证据，也不会因内容回滚而恢复已经撤销的访问权限。
     *
     * @author 程序员牛肉
     * @since 2026-07-18
     */
    public Map<String, Object> historicalNodeDetail(
            String kbId,
            String nodeId,
            String nodeReleaseId,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        Map<String, Object> current = store.query("""
                SELECT n.id, n.kb_id, n.type, n.name, n.meta, n.parent_id, n.position, n.permissions,
                       n.creator_id, n.editor_id, creator.account AS creator_account,
                       editor.account AS editor_account
                  FROM nodes n
                  LEFT JOIN users creator ON creator.id = n.creator_id
                  LEFT JOIN users editor ON editor.id = n.editor_id
                 WHERE n.id = ? AND n.kb_id = ?
                """, store.rowMapper(), nodeId, kbId).stream().findFirst()
                .orElseThrow(() -> new ApiException("Not Found"));
        if (!visible(current, authId(session), "visitable")) {
            throw new ApiException(HttpStatus.OK, "Permission Denied", 40003);
        }

        Map<String, Object> historical = store.query("""
                SELECT id AS node_release_id, node_id AS id, kb_id, type, name, meta, content,
                       parent_id, position, created_at, updated_at, publisher_id,
                       '' AS knowledge_version_id
                  FROM node_releases
                 WHERE id = ? AND kb_id = ? AND node_id = ?
                UNION ALL
                SELECT id AS node_release_id, node_id AS id, kb_id, type, name, meta, content,
                       parent_id, position, created_at, updated_at, publisher_id,
                       '' AS knowledge_version_id
                  FROM node_release_backup
                 WHERE id = ? AND kb_id = ? AND node_id = ?
                UNION ALL
                SELECT snapshot.node_release_id, snapshot.node_id AS id, snapshot.kb_id,
                       current_node.type, snapshot.name,
                       snapshot.meta, snapshot.content, current_node.parent_id, current_node.position,
                       snapshot.recorded_at AS created_at, snapshot.recorded_at AS updated_at,
                       NULL::text AS publisher_id, snapshot.knowledge_version_id
                  FROM conversation_reference_snapshots snapshot
                  JOIN nodes current_node ON current_node.id = snapshot.node_id
                 WHERE snapshot.node_release_id = ? AND snapshot.kb_id = ? AND snapshot.node_id = ?
                 ORDER BY created_at DESC
                 LIMIT 1
                """, store.rowMapper(), nodeReleaseId, kbId, nodeId,
                nodeReleaseId, kbId, nodeId, nodeReleaseId, kbId, nodeId).stream().findFirst()
                .orElseThrow(() -> new ApiException("Referenced knowledge version is no longer available"));

        Map<String, Object> result = new LinkedHashMap<>(historical);
        result.put("permissions", current.get("permissions"));
        result.put("creator_id", current.get("creator_id"));
        result.put("creator_account", current.get("creator_account"));
        result.put("editor_id", current.get("editor_id"));
        result.put("editor_account", current.get("editor_account"));
        result.put("status", 2);
        result.put("historical", true);
        result.put("source_version", nodeReleaseId);
        result.put("list", List.of());
        Long pv = store.query(
                "SELECT pv FROM node_stats WHERE node_id = ?",
                (rs, rowNum) -> rs.getLong(1), nodeId).stream().findFirst().orElse(0L);
        result.put("pv", pv);
        return result;
    }

    private List<Map<String, Object>> childrenTree(String kbId, String parentId, Long authId) {
        List<Map<String, Object>> nodes = releasedNodes(kbId);
        Map<String, List<Map<String, Object>>> byParent = new HashMap<>();
        nodes.stream().filter(node -> visible(node, authId, "visible"))
                .forEach(node -> byParent.computeIfAbsent(value(node.get("parent_id")), ignored -> new ArrayList<>()).add(node));
        return buildTree(parentId, byParent);
    }

    private List<Map<String, Object>> buildTree(String parentId, Map<String, List<Map<String, Object>>> byParent) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> child : byParent.getOrDefault(parentId, List.of())) {
            Map<String, Object> item = listItem(child);
            if (number(child.get("type")) == 1) {
                item.put("children", buildTree(value(child.get("id")), byParent));
            }
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> releasedNodes(String kbId) {
        return store.query("""
                SELECT nr.node_id AS id, nr.kb_id, links.nav_id, nr.type, 2 AS status, nr.name, nr.content,
                       nr.meta, nr.parent_id, nr.position, nr.created_at, nr.updated_at,
                       n.permissions, n.creator_id, n.editor_id, nr.publisher_id,
                       creator.account AS creator_account, editor.account AS editor_account,
                       publisher.account AS publisher_account
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                  LEFT JOIN users creator ON n.creator_id = creator.id
                  LEFT JOIN users editor ON n.editor_id = editor.id
                  LEFT JOIN users publisher ON nr.publisher_id = publisher.id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                 ORDER BY nr.position
                """, store.rowMapper(), kbId);
    }

    private Map<String, Object> listItem(Map<String, Object> node) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "type", "parent_id", "nav_id", "position", "meta", "updated_at", "permissions")) {
            item.put(key, node.get(key));
        }
        Map<String, Object> meta = jsonMaps.jsonMap(node.get("meta"));
        item.put("emoji", meta.getOrDefault("emoji", ""));
        return item;
    }

    private boolean visible(Map<String, Object> node, Long authId, String permission) {
        Map<String, Object> permissions = jsonMaps.jsonMap(node.get("permissions"));
        String value = String.valueOf(permissions.getOrDefault(permission, "open"));
        if ("open".equals(value)) {
            return true;
        }
        if (!"partial".equals(value) || authId == null) {
            return false;
        }
        Integer count = store.queryForObject("""
                SELECT count(*) FROM node_auth_groups nag
                  JOIN auth_groups ag ON nag.auth_group_id = ag.id
                 WHERE nag.node_id = ? AND nag.perm = ? AND ? = ANY(ag.auth_ids)
                """, Integer.class, node.get("id"), permission, authId);
        return count != null && count > 0;
    }

    private Long authId(HttpSession session) {
        Object id = session.getAttribute("user_id");
        return id instanceof Number number ? number.longValue() : null;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
