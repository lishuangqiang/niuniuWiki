package com.chaitin.niuniuwiki.knowledgebase;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.common.PageResult;
import com.chaitin.niuniuwiki.compiler.KnowledgeCompilerService;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthPrincipal;
import com.chaitin.niuniuwiki.security.AuthService;
import com.chaitin.niuniuwiki.rag.RagClient;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 封装知识库相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-23
 */
@Service
public class KnowledgeBaseService {

    private static final double MAX_POSITION = 1e38;

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final RagClient ragClient;
    private final VectorTaskPublisher vectorTasks;
    private final KnowledgeCompilerService compilerService;

    @Autowired
    public KnowledgeBaseService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            AuthService authService,
            RagClient ragClient,
            VectorTaskPublisher vectorTasks,
            KnowledgeCompilerService compilerService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.ragClient = ragClient;
        this.vectorTasks = vectorTasks;
        this.compilerService = compilerService;
    }

    public KnowledgeBaseService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            AuthService authService,
            RagClient ragClient,
            VectorTaskPublisher vectorTasks
    ) {
        this(store, jsonMaps, authService, ragClient, vectorTasks, null);
    }

    @Transactional
    public String create(KnowledgeBaseDtos.CreateRequest request) {
        authService.requireAdmin();
        List<String> hosts = distinct(request.hosts());
        List<Integer> ports = distinct(request.ports());
        List<Integer> sslPorts = distinct(request.sslPorts());
        if (hosts.isEmpty()) {
            throw new ApiException("hosts is required");
        }
        if (ports.isEmpty() && sslPorts.isEmpty()) {
            throw new ApiException("ports is required");
        }
        ensureUniqueEndpoint(null, hosts, ports, sslPorts);

        String id = UUID.randomUUID().toString();
        String datasetId = ragClient.createDataset();
        Map<String, Object> accessSettings = new LinkedHashMap<>();
        accessSettings.put("ports", ports);
        accessSettings.put("ssl_ports", sslPorts);
        accessSettings.put("public_key", value(request.publicKey()));
        accessSettings.put("private_key", value(request.privateKey()));
        accessSettings.put("hosts", hosts);
        accessSettings.put("base_url", "");
        accessSettings.put("trusted_proxies", List.of());
        accessSettings.put("simple_auth", Map.of("enabled", false, "password", ""));
        accessSettings.put("enterprise_auth", Map.of("enabled", false));
        accessSettings.put("source_type", "");
        accessSettings.put("is_forbidden", false);

        store.update(
                "INSERT INTO knowledge_bases(id, name, dataset_id, access_settings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?::jsonb, now(), now())",
                id, request.name(), datasetId, jsonMaps.json(accessSettings));
        store.update(
                "INSERT INTO apps(id, kb_id, name, type, settings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, ?::jsonb, now(), now())",
                UUID.randomUUID().toString(),
                id,
                request.name(),
                jsonMaps.json(defaultWebSettings(request.name())));
        store.update(
                "INSERT INTO navs(id, name, kb_id, position, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                UUID.randomUUID().toString(),
                request.name(),
                id,
                MAX_POSITION / 2);
        return id;
    }

    public List<Map<String, Object>> list() {
        AuthPrincipal principal = AuthContext.get();
        String select = "SELECT id, name, dataset_id, access_settings, created_at, updated_at FROM knowledge_bases ";
        if (principal.apiToken()) {
            return store.query(select + "WHERE id = ? ORDER BY created_at", store.rowMapper(), principal.kbId());
        }
        if ("admin".equals(principal.role())) {
            return store.query(select + "ORDER BY created_at", store.rowMapper());
        }
        return store.query(
                select + "WHERE id IN (SELECT kb_id FROM kb_users WHERE user_id = ?) ORDER BY created_at",
                store.rowMapper(),
                principal.userId());
    }

    public Map<String, Object> detail(String id) {
        authService.requireKbPermission(id, "not null");
        Map<String, Object> row = store.queryForObject(
                "SELECT id, name, dataset_id, access_settings, created_at, updated_at "
                        + "FROM knowledge_bases WHERE id = ?",
                store.rowMapper(),
                id);
        String permission = authService.permissionFor(id);
        row.put("perm", permission);
        if (!AuthService.FULL_CONTROL.equals(permission)) {
            Map<String, Object> settings = jsonMaps.jsonMap(row.get("access_settings"));
            settings.put("private_key", "");
            settings.put("public_key", "");
            row.put("access_settings", settings);
        }
        return row;
    }

    @Transactional
    public void update(KnowledgeBaseDtos.UpdateRequest request) {
        authService.requireKbPermission(request.id(), AuthService.FULL_CONTROL);
        if (request.accessSettings() != null) {
            ensureUniqueEndpoint(
                    request.id(),
                    stringList(request.accessSettings().get("hosts")),
                    integerList(request.accessSettings().get("ports")),
                    integerList(request.accessSettings().get("ssl_ports")));
            store.update(
                    "UPDATE knowledge_bases SET access_settings = ?::jsonb, updated_at = now() WHERE id = ?",
                    jsonMaps.json(request.accessSettings()),
                    request.id());
        }
        if (request.name() != null) {
            store.update(
                    "UPDATE knowledge_bases SET name = ?, updated_at = now() WHERE id = ?",
                    request.name(),
                    request.id());
        }
    }

    @Transactional
    public void delete(String id) {
        authService.requireAdmin();
        String datasetId = store.queryForObject(
                "SELECT dataset_id FROM knowledge_bases WHERE id = ?", String.class, id);
        ragClient.deleteDataset(datasetId);
        // Association tables intentionally do not all carry kb_id. Delete them
        // through their owning rows before removing the knowledge base itself.
        store.update("DELETE FROM node_auth_groups WHERE node_id IN (SELECT id FROM nodes WHERE kb_id = ?)", id);
        store.update("DELETE FROM node_stats WHERE node_id IN (SELECT id FROM nodes WHERE kb_id = ?)", id);
        store.update("DELETE FROM conversation_references WHERE conversation_id IN "
                + "(SELECT id FROM conversations WHERE kb_id = ?)", id);

        for (String table : List.of(
                "kb_release_node_releases", "nav_releases", "node_release_backup", "node_releases",
                "comments", "document_feedbacks", "conversation_messages", "conversations",
                "stat_pages", "stat_page_hours", "auth_configs", "auth_groups", "auths", "settings",
                "api_tokens", "contributes", "mcp_calls", "kb_users", "apps", "kb_releases", "navs")) {
            store.update("DELETE FROM " + table + " WHERE kb_id = ?", id);
        }
        store.update("DELETE FROM nodes WHERE kb_id = ?", id);
        store.update("DELETE FROM knowledge_bases WHERE id = ?", id);
    }

    @Transactional
    public String createRelease(KnowledgeBaseDtos.ReleaseRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.DOC_MANAGE);
        if (request.nodeIds() != null && !request.nodeIds().isEmpty()) {
            publishNodes(request.kbId(), request.nodeIds());
        }
        String releaseId = UUID.randomUUID().toString();
        store.update(
                "INSERT INTO kb_releases(id, kb_id, tag, message, publisher_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, now())",
                releaseId,
                request.kbId(),
                request.tag(),
                request.message(),
                AuthContext.get().userId());
        snapshotRelease(request.kbId(), releaseId);
        if (compilerService != null) {
            compilerService.requestReleaseCompile(request.kbId(), releaseId, AuthContext.get().userId());
        }
        return releaseId;
    }

    public PageResult<List<Map<String, Object>>> releases(String kbId, int page, int perPage) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        long total = store.queryForObject(
                "SELECT count(*) FROM kb_releases WHERE kb_id = ?",
                Long.class,
                kbId);
        List<Map<String, Object>> list = store.query(
                "SELECT r.*, u.account AS publisher_account FROM kb_releases r "
                        + "LEFT JOIN users u ON r.publisher_id = u.id WHERE r.kb_id = ? "
                        + "ORDER BY r.created_at DESC OFFSET ? LIMIT ?",
                store.rowMapper(),
                kbId,
                Math.max(0, page - 1) * perPage,
                perPage);
        return new PageResult<>(total, list);
    }

    public List<Map<String, Object>> users(String kbId) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        List<Map<String, Object>> users = new ArrayList<>(store.query(
                "SELECT u.id, u.account, u.role, k.perm, k.created_at FROM users u "
                        + "JOIN kb_users k ON u.id = k.user_id WHERE k.kb_id = ? AND u.role = 'user' "
                        + "ORDER BY k.created_at DESC",
                store.rowMapper(),
                kbId));
        List<Map<String, Object>> admins = store.query(
                "SELECT id, account, role FROM users WHERE role = 'admin' ORDER BY id DESC",
                store.rowMapper());
        admins.forEach(admin -> admin.put("perm", AuthService.FULL_CONTROL));
        users.addAll(admins);
        return users;
    }

    public void invite(KnowledgeBaseDtos.UserPermissionRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.FULL_CONTROL);
        String role = store.queryForObject("SELECT role FROM users WHERE id = ?", String.class, request.userId());
        if ("admin".equals(role)) {
            throw new ApiException("knowledge base can not invite to admin user");
        }
        try {
            store.update(
                    "INSERT INTO kb_users(kb_id, user_id, perm, created_at) VALUES (?, ?, ?, now())",
                    request.kbId(), request.userId(), request.perm());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException("user already belongs to knowledge base");
        }
    }

    public void updateUser(KnowledgeBaseDtos.UserPermissionRequest request) {
        authService.requireKbPermission(request.kbId(), AuthService.FULL_CONTROL);
        store.update(
                "UPDATE kb_users SET perm = ? WHERE kb_id = ? AND user_id = ?",
                request.perm(), request.kbId(), request.userId());
    }

    public void deleteUser(String kbId, String userId) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        store.update("DELETE FROM kb_users WHERE kb_id = ? AND user_id = ?", kbId, userId);
    }

    private void publishNodes(String kbId, List<String> nodeIds) {
        for (String nodeId : nodeIds) {
            List<Map<String, Object>> nodes = store.query(
                    "SELECT * FROM nodes WHERE id = ? AND kb_id = ?",
                    store.rowMapper(), nodeId, kbId);
            if (nodes.isEmpty()) {
                continue;
            }
            Map<String, Object> node = nodes.getFirst();
            String releaseId = UUID.randomUUID().toString();
            store.update(
                    "INSERT INTO node_releases(id, kb_id, node_id, doc_id, type, name, meta, content, position, "
                            + "parent_id, publisher_id, editor_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now(), now())",
                    releaseId, kbId, nodeId, value(node.get("doc_id")), node.get("type"), node.get("name"),
                    jsonMaps.json(node.get("meta")), value(node.get("content")), node.get("position"),
                    value(node.get("parent_id")), AuthContext.get().userId(), value(node.get("editor_id")));
            store.update("UPDATE nodes SET status = 2 WHERE id = ? AND kb_id = ?", nodeId, kbId);
            vectorTasks.upsertAfterCommit(kbId, releaseId, nodeId);
        }
    }

    private void snapshotRelease(String kbId, String releaseId) {
        List<Map<String, Object>> releases = store.query(
                "SELECT DISTINCT ON (node_id) id, node_id FROM node_releases "
                        + "WHERE kb_id = ? ORDER BY node_id, updated_at DESC",
                store.rowMapper(), kbId);
        for (Map<String, Object> nodeRelease : releases) {
            String nodeId = String.valueOf(nodeRelease.get("node_id"));
            String navId = store.query(
                    "SELECT nav_id FROM nodes WHERE id = ?",
                    (rs, rowNum) -> rs.getString(1), nodeId).stream().findFirst().orElse("");
            store.update(
                    "INSERT INTO kb_release_node_releases(id, kb_id, release_id, node_id, node_release_id, nav_id, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, now())",
                    UUID.randomUUID().toString(), kbId, releaseId, nodeId, nodeRelease.get("id"), navId);
        }
        List<Map<String, Object>> navs = store.query(
                "SELECT id, name, position FROM navs WHERE kb_id = ? ORDER BY position",
                store.rowMapper(), kbId);
        for (Map<String, Object> nav : navs) {
            store.update(
                    "INSERT INTO nav_releases(id, nav_id, release_id, kb_id, name, position, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, now())",
                    UUID.randomUUID().toString(), nav.get("id"), releaseId, kbId, nav.get("name"), nav.get("position"));
        }
    }

    private void ensureUniqueEndpoint(String currentId, List<String> hosts, List<Integer> ports, List<Integer> sslPorts) {
        Set<String> requested = new HashSet<>();
        for (String host : hosts) {
            for (Integer port : ports) {
                requested.add(host + ":" + port);
            }
            for (Integer port : sslPorts) {
                requested.add(host + ":" + port);
            }
        }
        List<Map<String, Object>> existing = store.query(
                "SELECT id, access_settings FROM knowledge_bases",
                store.rowMapper());
        for (Map<String, Object> kb : existing) {
            if (String.valueOf(kb.get("id")).equals(currentId)) {
                continue;
            }
            Map<String, Object> settings = jsonMaps.jsonMap(kb.get("access_settings"));
            for (String host : stringList(settings.get("hosts"))) {
                for (Integer port : integerList(settings.get("ports"))) {
                    if (requested.contains(host + ":" + port)) {
                        throw new ApiException("端口或域名已被其他知识库占用");
                    }
                }
                for (Integer port : integerList(settings.get("ssl_ports"))) {
                    if (requested.contains(host + ":" + port)) {
                        throw new ApiException("端口或域名已被其他知识库占用");
                    }
                }
            }
        }
    }

    private Map<String, Object> defaultWebSettings(String name) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("title", name);
        settings.put("desc", name);
        settings.put("keyword", name);
        settings.put("welcome_str", "欢迎使用" + name);
        settings.put("recommend_questions", List.of());
        settings.put("recommend_node_ids", List.of());
        settings.put("btns", List.of());
        settings.put("home_page_setting", "custom");
        settings.put("web_app_landing_configs", List.of());
        return settings;
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> ((Number) item).intValue()).toList();
    }
}
