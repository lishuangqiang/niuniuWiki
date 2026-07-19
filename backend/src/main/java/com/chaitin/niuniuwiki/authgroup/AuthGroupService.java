package com.chaitin.niuniuwiki.authgroup;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现访客用户组的树形管理、成员绑定和节点授权数据维护。
 *
 * @author 程序员牛肉
 * @since 2026-06-11
 */
@Service
public class AuthGroupService {

    private static final double MAX_POSITION = 1e38;

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public AuthGroupService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        String name = validateName(request.get("name"));
        Integer parentId = integer(request.get("parent_id"));
        ensureParent(kbId, parentId, null);
        double position = decimal(request.get("position"), positionAfterLast(kbId, parentId));
        int id = store.queryForObject(
                "INSERT INTO auth_groups(kb_id, name, auth_ids, parent_id, position, created_at, updated_at) "
                        + "VALUES (?, ?, '{}'::integer[], ?, ?, now(), now()) RETURNING id",
                Integer.class, kbId, name, parentId, position);
        replaceMembers(kbId, id, integerList(request.get("ids")));
        return Map.of("id", id);
    }

    public Map<String, Object> list(String kbId, int page, int perPage) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        long total = store.queryForObject("SELECT count(*) FROM auth_groups WHERE kb_id = ?", Long.class, kbId);
        List<Map<String, Object>> rows = store.query(
                "SELECT id, name, auth_ids, parent_id, position, created_at, sync_id FROM auth_groups "
                        + "WHERE kb_id = ? ORDER BY position, id OFFSET ? LIMIT ?",
                store.rowMapper(), kbId, Math.max(0, page - 1) * perPage, perPage);
        Map<Integer, String> names = groupNames(kbId);
        rows.forEach(row -> decorate(row, names));
        return Map.of("list", rows, "total", total);
    }

    public Map<String, Object> detail(String kbId, int id) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        Map<String, Object> group = group(kbId, id);
        Map<Integer, String> names = groupNames(kbId);
        decorate(group, names);
        List<Integer> authIds = integerList(group.get("auth_ids"));
        group.put("auths", auths(kbId, authIds));
        Integer parentId = integer(group.get("parent_id"));
        group.put("parent", parentId == null ? null : group(kbId, parentId));
        List<Map<String, Object>> children = store.query(
                "SELECT id, name, auth_ids, parent_id, position, created_at FROM auth_groups "
                        + "WHERE kb_id = ? AND parent_id = ? ORDER BY position, id",
                store.rowMapper(), kbId, id);
        children.forEach(item -> decorate(item, names));
        group.put("children", children);
        return group;
    }

    public Map<String, Object> tree(String kbId) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        List<Map<String, Object>> rows = store.query(
                "SELECT id, name, auth_ids, parent_id, position, created_at, sync_id FROM auth_groups "
                        + "WHERE kb_id = ? ORDER BY position, id",
                store.rowMapper(), kbId);
        Map<Integer, List<Map<String, Object>>> children = new HashMap<>();
        for (Map<String, Object> row : rows) {
            row.put("count", integerList(row.get("auth_ids")).size());
            Integer parent = integer(row.get("parent_id"));
            children.computeIfAbsent(parent == null ? 0 : parent, ignored -> new ArrayList<>()).add(row);
        }
        return Map.of("list", buildTree(0, 0, children));
    }

    @Transactional
    public void update(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        int id = requiredInteger(request.get("id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        group(kbId, id);
        String name = request.containsKey("name") ? validateName(request.get("name")) : null;
        Integer parentId = request.containsKey("parent_id") ? integer(request.get("parent_id")) : null;
        if (request.containsKey("parent_id")) {
            ensureParent(kbId, parentId, id);
        }
        Double position = request.containsKey("position") ? decimal(request.get("position"), 0) : null;
        store.update(
                "UPDATE auth_groups SET name = COALESCE(?, name), "
                        + "parent_id = CASE WHEN ? THEN ? ELSE parent_id END, position = COALESCE(?, position), "
                        + "updated_at = now() WHERE kb_id = ? AND id = ?",
                name, request.containsKey("parent_id"), parentId, position, kbId, id);
        if (request.containsKey("auth_ids")) {
            replaceMembers(kbId, id, integerList(request.get("auth_ids")));
        }
    }

    @Transactional
    public void move(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        int id = requiredInteger(request.get("id"));
        Integer parentId = integer(request.get("parent_id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        ensureParent(kbId, parentId, id);
        double previous = position(kbId, integer(request.get("prev_id")), 0);
        double next = position(kbId, integer(request.get("next_id")), MAX_POSITION);
        if (next <= previous) {
            next = previous + 2000;
        }
        store.update("UPDATE auth_groups SET parent_id = ?, position = ?, updated_at = now() WHERE kb_id = ? AND id = ?",
                parentId, previous + (next - previous) / 2, kbId, id);
    }

    @Transactional
    public void delete(String kbId, int id) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        group(kbId, id);
        List<Integer> ids = store.query("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM auth_groups WHERE kb_id = ? AND id = ?
                    UNION ALL
                    SELECT child.id FROM auth_groups child JOIN tree parent ON child.parent_id = parent.id
                    WHERE child.kb_id = ?
                ) SELECT id FROM tree
                """, (rs, rowNum) -> rs.getInt(1), kbId, id, kbId);
        if (!ids.isEmpty()) {
            Integer[] values = ids.toArray(Integer[]::new);
            store.update("DELETE FROM node_auth_groups WHERE auth_group_id = ANY (?::integer[])", (Object) values);
            store.update("DELETE FROM auth_groups WHERE kb_id = ? AND id = ANY (?::integer[])", kbId, values);
        }
    }

    @Transactional
    public Map<String, Object> sync(String kbId, String sourceType) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        if (!List.of("dingtalk", "wecom").contains(sourceType)) {
            throw new ApiException("仅支持同步钉钉或企业微信用户");
        }
        List<Integer> authIds = store.query(
                "SELECT id FROM auths WHERE kb_id = ? AND source_type = ? ORDER BY id",
                (rs, rowNum) -> rs.getInt(1), kbId, sourceType);
        String syncId = "synced:" + sourceType;
        List<Integer> existing = store.query(
                "SELECT id FROM auth_groups WHERE kb_id = ? AND sync_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getInt(1), kbId, syncId);
        int id;
        if (existing.isEmpty()) {
            id = store.queryForObject(
                    "INSERT INTO auth_groups(kb_id, name, auth_ids, parent_id, position, sync_id, source_type, "
                            + "created_at, updated_at) VALUES (?, ?, '{}'::integer[], NULL, ?, ?, ?, now(), now()) RETURNING id",
                    Integer.class, kbId, "dingtalk".equals(sourceType) ? "钉钉用户" : "企业微信用户",
                    positionAfterLast(kbId, null), syncId, sourceType);
        } else {
            id = existing.getFirst();
        }
        replaceMembers(kbId, id, authIds);
        return Map.of("group_id", id, "synced", authIds.size());
    }

    private List<Map<String, Object>> buildTree(
            int parentId,
            int level,
            Map<Integer, List<Map<String, Object>>> children
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : children.getOrDefault(parentId, List.of())) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("level", level);
            item.put("children", buildTree(((Number) row.get("id")).intValue(), level + 1, children));
            item.putIfAbsent("sync_id", "");
            result.add(item);
        }
        return result;
    }

    private void replaceMembers(String kbId, int id, List<Integer> authIds) {
        if (!authIds.isEmpty()) {
            Integer count = store.queryForObject(
                    "SELECT count(*) FROM auths WHERE kb_id = ? AND id = ANY (?::integer[])",
                    Integer.class, kbId, authIds.toArray(Integer[]::new));
            if (count == null || count != authIds.stream().distinct().count()) {
                throw new ApiException("用户组包含不属于当前知识库的用户");
            }
        }
        store.update(
                "UPDATE auth_groups SET auth_ids = ?::integer[], updated_at = now() WHERE kb_id = ? AND id = ?",
                authIds.toArray(Integer[]::new), kbId, id);
    }

    private List<Map<String, Object>> auths(String kbId, List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = store.query(
                "SELECT id, user_info, ip, source_type, last_login_time, created_at FROM auths "
                        + "WHERE kb_id = ? AND id = ANY (?::integer[]) ORDER BY id",
                store.rowMapper(), kbId, ids.toArray(Integer[]::new));
        rows.forEach(row -> {
            Map<String, Object> user = jsonMaps.jsonMap(row.remove("user_info"));
            row.put("username", user.getOrDefault("username", ""));
            row.put("avatar_url", user.getOrDefault("avatar_url", ""));
        });
        return rows;
    }

    private Map<String, Object> group(String kbId, int id) {
        List<Map<String, Object>> rows = store.query(
                "SELECT id, name, auth_ids, parent_id, position, created_at, sync_id FROM auth_groups "
                        + "WHERE kb_id = ? AND id = ?",
                store.rowMapper(), kbId, id);
        if (rows.isEmpty()) {
            throw new ApiException("用户组不存在");
        }
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void decorate(Map<String, Object> row, Map<Integer, String> names) {
        List<Integer> ids = integerList(row.get("auth_ids"));
        row.put("auth_ids", ids);
        row.put("count", ids.size());
        Integer parentId = integer(row.get("parent_id"));
        List<String> path = new ArrayList<>();
        while (parentId != null && names.containsKey(parentId)) {
            path.add(names.get(parentId));
            Map<String, Object> parent = store.queryForMap("SELECT parent_id FROM auth_groups WHERE id = ?", parentId);
            parentId = integer(parent.get("parent_id"));
        }
        Collections.reverse(path);
        row.put("path", String.join(" / ", path));
    }

    private Map<Integer, String> groupNames(String kbId) {
        Map<Integer, String> result = new HashMap<>();
        store.queryForList("SELECT id, name FROM auth_groups WHERE kb_id = ?", kbId)
                .forEach(row -> result.put(
                        ((Number) row.get("id")).intValue(),
                        String.valueOf(row.get("name"))));
        return result;
    }

    private void ensureParent(String kbId, Integer parentId, Integer movingId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(parentId, movingId)) {
            throw new ApiException("用户组不能移动到自身下面");
        }
        group(kbId, parentId);
        if (movingId != null) {
            Integer descendants = store.queryForObject("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM auth_groups WHERE kb_id = ? AND parent_id = ?
                        UNION ALL
                        SELECT child.id FROM auth_groups child JOIN tree parent ON child.parent_id = parent.id
                        WHERE child.kb_id = ?
                    ) SELECT count(*) FROM tree WHERE id = ?
                    """, Integer.class, kbId, movingId, kbId, parentId);
            if (descendants != null && descendants > 0) {
                throw new ApiException("用户组不能移动到自己的子组下面");
            }
        }
    }

    private double positionAfterLast(String kbId, Integer parentId) {
        List<Double> values = store.query(
                "SELECT position FROM auth_groups WHERE kb_id = ? AND parent_id IS NOT DISTINCT FROM ? "
                        + "ORDER BY position DESC LIMIT 1",
                (rs, rowNum) -> rs.getDouble(1), kbId, parentId);
        return values.isEmpty() ? 1000 : values.getFirst() + 1000;
    }

    private double position(String kbId, Integer id, double fallback) {
        if (id == null) {
            return fallback;
        }
        return store.query(
                "SELECT position FROM auth_groups WHERE kb_id = ? AND id = ?",
                (rs, rowNum) -> rs.getDouble(1), kbId, id).stream().findFirst().orElse(fallback);
    }

    private String validateName(Object raw) {
        String name = value(raw).strip();
        if (name.isBlank() || name.length() > 100) {
            throw new ApiException("用户组名称长度必须在 1 到 100 个字符之间");
        }
        return name;
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(AuthGroupService::integer).filter(Objects::nonNull).distinct().toList();
    }

    private static Integer integer(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private static int requiredInteger(Object value) {
        Integer result = integer(value);
        if (result == null) {
            throw new ApiException("id 不能为空");
        }
        return result;
    }

    private static double decimal(Object value, double fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
