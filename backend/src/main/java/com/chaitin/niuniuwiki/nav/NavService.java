package com.chaitin.niuniuwiki.nav;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封装导航相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-11
 */
@Service
public class NavService {

    private static final double MAX_POSITION = 1e38;
    private static final double MIN_GAP = 1e-5;

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public NavService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public List<Map<String, Object>> list(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        return store.query(
                "SELECT id, name, position, created_at, updated_at FROM navs WHERE kb_id = ? ORDER BY position",
                store.rowMapper(), kbId);
    }

    public void add(NavDtos.AddRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        double position = request.position() == null ? positionAfterLast(request.kbId()) : request.position();
        validatePosition(position);
        store.update(
                "INSERT INTO navs(id, name, kb_id, position, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                UUID.randomUUID().toString(), request.name(), request.kbId(), position);
    }

    public void update(NavDtos.UpdateRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        store.update(
                "UPDATE navs SET name = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                request.name(), request.id(), request.kbId());
    }

    @Transactional
    public void delete(String kbId, String id) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        List<String> nodeIds = store.query(
                "SELECT id FROM nodes WHERE kb_id = ? AND nav_id = ?",
                (rs, rowNum) -> rs.getString(1), kbId, id);
        if (!nodeIds.isEmpty()) {
            for (String nodeId : nodeIds) {
                deleteNodeTree(kbId, nodeId);
            }
        }
        store.update("DELETE FROM navs WHERE id = ? AND kb_id = ?", id, kbId);
    }

    @Transactional
    public void move(NavDtos.MoveRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        double previous = position(request.kbId(), request.prevId(), 0);
        double next = position(request.kbId(), request.nextId(), MAX_POSITION);
        if (next - previous < MIN_GAP) {
            reorder(request.kbId());
            previous = position(request.kbId(), request.prevId(), 0);
            next = position(request.kbId(), request.nextId(), MAX_POSITION);
        }
        store.update(
                "UPDATE navs SET position = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                previous + (next - previous) / 2, request.id(), request.kbId());
    }

    private double positionAfterLast(String kbId) {
        Double max = store.queryForObject(
                "SELECT COALESCE(MAX(position), 0) FROM navs WHERE kb_id = ?",
                Double.class, kbId);
        return max + (MAX_POSITION - max) / 2;
    }

    private double position(String kbId, String id, double defaultValue) {
        if (id == null || id.isBlank()) {
            return defaultValue;
        }
        return store.queryForObject(
                "SELECT position FROM navs WHERE id = ? AND kb_id = ?",
                Double.class, id, kbId);
    }

    private void reorder(String kbId) {
        List<String> ids = store.query(
                "SELECT id FROM navs WHERE kb_id = ? ORDER BY position",
                (rs, rowNum) -> rs.getString(1), kbId);
        for (int index = 0; index < ids.size(); index++) {
            store.update("UPDATE navs SET position = ? WHERE id = ?", 1000d + index * 1000d, ids.get(index));
        }
    }

    private void deleteNodeTree(String kbId, String id) {
        List<String> children = store.query(
                "SELECT id FROM nodes WHERE kb_id = ? AND parent_id = ?",
                (rs, rowNum) -> rs.getString(1), kbId, id);
        children.forEach(child -> deleteNodeTree(kbId, child));
        store.update("DELETE FROM node_releases WHERE kb_id = ? AND node_id = ?", kbId, id);
        store.update("DELETE FROM nodes WHERE kb_id = ? AND id = ?", kbId, id);
    }

    private void validatePosition(double position) {
        if (position < 0 || position > MAX_POSITION) {
            throw new ApiException("specified position is out of range");
        }
    }
}
