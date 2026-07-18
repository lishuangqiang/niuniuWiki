package com.chaitin.niuniuwiki.contribute;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.node.NodeDtos;
import com.chaitin.niuniuwiki.node.NodeService;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理访客文档贡献的提交、检索、详情和审核落库流程。
 *
 * @author 程序员牛肉
 * @since 2026-06-25
 */
@Service
public class ContributeService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final NodeService nodeService;

    public ContributeService(
            MyBatisStore store,
            JsonMaps jsonMaps,
            AuthService authService,
            NodeService nodeService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.nodeService = nodeService;
    }

    public Map<String, Object> submit(
            String kbId,
            Map<String, Object> request,
            String remoteIp,
            HttpSession session
    ) {
        ensureEnabled(kbId);
        String type = value(request.get("type"));
        if (!List.of("add", "edit").contains(type)) {
            throw new ApiException("贡献类型必须是 add 或 edit");
        }
        String nodeId = value(request.get("node_id"));
        String name = value(request.get("name")).strip();
        if ("edit".equals(type)) {
            List<String> names = store.query(
                    "SELECT name FROM nodes WHERE kb_id = ? AND id = ?",
                    (rs, rowNum) -> rs.getString(1), kbId, nodeId);
            if (names.isEmpty()) {
                throw new ApiException("待修改文档不存在");
            }
            if (name.isBlank()) {
                name = names.getFirst();
            }
        } else if (name.isBlank()) {
            throw new ApiException("新增文档必须填写标题");
        }
        String content = value(request.get("content"));
        String reason = value(request.get("reason")).strip();
        if (reason.isBlank()) {
            throw new ApiException("请填写提交说明");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("emoji", value(request.get("emoji")));
        meta.put("content_type", value(request.get("content_type")));
        Object authValue = session.getAttribute("user_id");
        Long authId = authValue instanceof Number number ? number.longValue() : null;
        String id = UUID.randomUUID().toString();
        store.update(
                "INSERT INTO contributes(id, auth_id, kb_id, status, type, node_id, name, content, reason, "
                        + "audit_user_id, meta, remote_ip, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'pending', ?, ?, ?, ?, ?, '', ?::jsonb, ?, now(), now())",
                id, authId, kbId, type, nodeId.isBlank() ? null : nodeId, name, content, reason,
                jsonMaps.json(meta), value(remoteIp));
        return Map.of("id", id);
    }

    public Map<String, Object> list(
            String kbId,
            String status,
            String nodeName,
            String authName,
            int page,
            int perPage
    ) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        StringBuilder where = new StringBuilder(" WHERE c.kb_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status = ?");
            args.add(status);
        }
        if (nodeName != null && !nodeName.isBlank()) {
            where.append(" AND COALESCE(c.name, n.name, '') ILIKE ?");
            args.add("%" + nodeName + "%");
        }
        if (authName != null && !authName.isBlank()) {
            where.append(" AND COALESCE(a.user_info->>'username', '') ILIKE ?");
            args.add("%" + authName + "%");
        }
        long total = store.queryForObject(
                "SELECT count(*) FROM contributes c LEFT JOIN nodes n ON n.id = c.node_id "
                        + "LEFT JOIN auths a ON a.id = c.auth_id" + where,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(Math.max(0, page - 1) * perPage);
        listArgs.add(Math.min(100, perPage));
        List<Map<String, Object>> rows = store.query(
                "SELECT c.id, c.auth_id, COALESCE(a.user_info->>'username', '') AS auth_name, c.kb_id, "
                        + "c.status, c.type, c.node_id, COALESCE(c.name, n.name, '') AS node_name, "
                        + "c.name AS contribute_name, c.reason, c.audit_user_id, c.audit_time, c.meta, "
                        + "c.remote_ip, c.created_at, c.updated_at FROM contributes c "
                        + "LEFT JOIN nodes n ON n.id = c.node_id LEFT JOIN auths a ON a.id = c.auth_id"
                        + where + " ORDER BY c.created_at DESC OFFSET ? LIMIT ?",
                store.rowMapper(), listArgs.toArray());
        rows.forEach(row -> row.put("ip_address", Map.of(
                "ip", row.getOrDefault("remote_ip", ""),
                "country", "", "province", "", "city", "")));
        return Map.of("list", rows, "total", total);
    }

    public Map<String, Object> detail(String kbId, String id) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        List<Map<String, Object>> rows = store.query(
                "SELECT c.*, COALESCE(a.user_info->>'username', '') AS auth_name, "
                        + "COALESCE(c.name, n.name, '') AS node_name FROM contributes c "
                        + "LEFT JOIN auths a ON a.id = c.auth_id LEFT JOIN nodes n ON n.id = c.node_id "
                        + "WHERE c.kb_id = ? AND c.id = ?",
                store.rowMapper(), kbId, id);
        if (rows.isEmpty()) {
            throw new ApiException("文档贡献不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        String nodeId = value(result.get("node_id"));
        if (!nodeId.isBlank()) {
            store.query(
                    "SELECT id, name, content, meta FROM nodes WHERE kb_id = ? AND id = ?",
                    store.rowMapper(), kbId, nodeId).stream().findFirst()
                    .ifPresent(node -> result.put("original_node", node));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> audit(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        String id = value(request.get("id"));
        String status = value(request.get("status"));
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        if (!List.of("approved", "rejected").contains(status)) {
            throw new ApiException("审核状态必须是 approved 或 rejected");
        }
        List<Map<String, Object>> rows = store.query(
                "SELECT * FROM contributes WHERE kb_id = ? AND id = ? FOR UPDATE",
                store.rowMapper(), kbId, id);
        if (rows.isEmpty()) {
            throw new ApiException("文档贡献不存在");
        }
        Map<String, Object> contribution = rows.getFirst();
        if (!"pending".equals(value(contribution.get("status")))) {
            throw new ApiException("该贡献已经审核");
        }
        String targetNodeId = value(contribution.get("node_id"));
        if ("approved".equals(status)) {
            Map<String, Object> meta = jsonMaps.jsonMap(contribution.get("meta"));
            if ("add".equals(value(contribution.get("type")))) {
                String navId = value(request.get("nav_id"));
                targetNodeId = nodeService.create(new NodeDtos.CreateRequest(
                        kbId,
                        navId,
                        value(request.get("parent_id")),
                        2,
                        value(contribution.get("name")),
                        value(contribution.get("content")),
                        value(meta.get("emoji")),
                        "",
                        value(meta.get("content_type")),
                        request.get("position") instanceof Number number ? number.doubleValue() : null));
            } else {
                nodeService.update(new NodeDtos.UpdateRequest(
                        targetNodeId,
                        kbId,
                        value(contribution.get("name")),
                        value(contribution.get("content")),
                        value(meta.get("emoji")),
                        null,
                        null,
                        value(meta.get("content_type")),
                        null));
            }
        }
        store.update(
                "UPDATE contributes SET status = ?, node_id = NULLIF(?, ''), audit_user_id = ?, "
                        + "audit_time = now(), updated_at = now() WHERE kb_id = ? AND id = ?",
                status, targetNodeId, AuthContext.get().userId(), kbId, id);
        return Map.of("message", "approved".equals(status) ? "贡献已采纳" : "贡献已拒绝");
    }

    private void ensureEnabled(String kbId) {
        List<Map<String, Object>> apps = store.query(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 1",
                store.rowMapper(), kbId);
        if (apps.isEmpty()) {
            throw new ApiException("知识库应用不存在");
        }
        Map<String, Object> settings = jsonMaps.jsonMap(apps.getFirst().get("settings"));
        Map<String, Object> contribute = jsonMaps.jsonMap(settings.get("contribute_settings"));
        if (!Boolean.TRUE.equals(contribute.get("is_enable"))) {
            throw new ApiException("文档贡献功能尚未启用");
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
