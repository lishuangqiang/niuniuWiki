package com.chaitin.niuniuwiki.comment;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.common.PageResult;
import com.chaitin.niuniuwiki.security.AuthService;
import java.sql.Array;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 封装评论相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-02
 */
@Service
public class CommentService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public CommentService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public PageResult<List<Map<String, Object>>> adminList(String kbId, Integer status, int page, int perPage) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        String statusWhere = status == null ? "" : " AND c.status = " + status;
        long total = store.queryForObject(
                "SELECT count(*) FROM comments c WHERE c.kb_id = ?" + statusWhere,
                Long.class, kbId);
        List<Map<String, Object>> comments = store.query(
                "SELECT c.*, n.name AS node_name, n.type AS node_type FROM comments c "
                        + "LEFT JOIN nodes n ON c.node_id = n.id WHERE c.kb_id = ?" + statusWhere
                        + " ORDER BY c.created_at DESC OFFSET ? LIMIT ?",
                store.rowMapper(), kbId, Math.max(0, page - 1) * perPage, perPage);
        return new PageResult<>(total, comments);
    }

    public void delete(String kbId, List<String> ids) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        if (ids == null || ids.isEmpty()) {
            throw new ApiException("len comment id is zero");
        }
        store.update(
                "DELETE FROM comments WHERE kb_id = ? AND id = ANY (?::text[])",
                kbId, ids.toArray(String[]::new));
    }

    public void moderate(List<String> ids, int status) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException("评论 ID 不能为空");
        }
        if (status != -1 && status != 0 && status != 1) {
            throw new ApiException("无效的评论审核状态");
        }
        List<String> kbIds = store.query(
                "SELECT DISTINCT kb_id FROM comments WHERE id = ANY (?::text[])",
                (rs, rowNum) -> rs.getString(1), (Object) ids.toArray(String[]::new));
        if (kbIds.isEmpty()) {
            throw new ApiException("评论不存在");
        }
        kbIds.forEach(kbId -> authService.requireKbPermission(kbId, AuthService.DATA_OPERATE));
        store.update("UPDATE comments SET status = ? WHERE id = ANY (?::text[])",
                status, ids.toArray(String[]::new));
    }

    public String create(String kbId, String remoteIp, Map<String, Object> request) {
        String nodeId = String.valueOf(request.getOrDefault("node_id", ""));
        String content = String.valueOf(request.getOrDefault("content", ""));
        if (nodeId.isBlank() || content.isBlank()) {
            throw new ApiException("validate req failed");
        }
        Integer nodeExists = store.queryForObject(
                "SELECT count(*) FROM nodes WHERE id = ? AND kb_id = ?", Integer.class, nodeId, kbId);
        if (nodeExists == null || nodeExists == 0) {
            throw new ApiException("Not Found");
        }
        Map<String, Object> app = store.queryForObject(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 1",
                store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(app.get("settings"));
        Map<String, Object> commentSettings = jsonMaps.jsonMap(settings.get("web_app_comment_settings"));
        if (!Boolean.TRUE.equals(commentSettings.get("is_enable"))) {
            throw new ApiException("please check comment is open");
        }
        int status = Boolean.TRUE.equals(commentSettings.get("moderation_enable")) ? 0 : 1;
        List<String> pictures = request.get("pic_urls") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        if (pictures.stream().anyMatch(url -> !url.startsWith("/static-file/"))) {
            throw new ApiException("validate param pic_urls failed");
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("auth_user_id", 0);
        info.put("user_name", request.getOrDefault("user_name", ""));
        info.put("email", "");
        info.put("avatar", "");
        info.put("remote_ip", remoteIp);
        store.update(
                "INSERT INTO comments(id, kb_id, node_id, info, parent_id, root_id, content, status, pic_urls, created_at) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::text[], now())",
                id, kbId, nodeId, jsonMaps.json(info),
                String.valueOf(request.getOrDefault("parent_id", "")),
                String.valueOf(request.getOrDefault("root_id", "")),
                content, status, pictures.toArray(String[]::new));
        return id;
    }

    public PageResult<List<Map<String, Object>>> publicList(String kbId, String nodeId) {
        Map<String, Object> app = store.queryForObject(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 1",
                store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(app.get("settings"));
        Map<String, Object> commentSettings = jsonMaps.jsonMap(settings.get("web_app_comment_settings"));
        if (!Boolean.TRUE.equals(commentSettings.get("is_enable"))) {
            throw new ApiException("please check comment is open");
        }
        String moderation = Boolean.TRUE.equals(commentSettings.get("moderation_enable")) ? " AND status = 1" : "";
        long total = store.queryForObject(
                "SELECT count(*) FROM comments WHERE kb_id = ? AND node_id = ?" + moderation,
                Long.class, kbId, nodeId);
        List<Map<String, Object>> comments = store.query(
                "SELECT id, kb_id, node_id, info, parent_id, root_id, content, pic_urls, created_at "
                        + "FROM comments WHERE kb_id = ? AND node_id = ?" + moderation + " ORDER BY created_at DESC",
                store.rowMapper(), kbId, nodeId);
        comments.forEach(this::maskRemoteIp);
        return new PageResult<>(total, comments);
    }

    private void maskRemoteIp(Map<String, Object> comment) {
        Map<String, Object> info = jsonMaps.jsonMap(comment.get("info"));
        String ip = String.valueOf(info.getOrDefault("remote_ip", ""));
        String[] parts = ip.split("\\.");
        info.put("remote_ip", parts.length == 4 ? parts[0] + ".*.*." + parts[3] : "");
        comment.put("info", info);
    }
}
