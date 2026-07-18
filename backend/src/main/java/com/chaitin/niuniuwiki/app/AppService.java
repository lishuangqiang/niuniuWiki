package com.chaitin.niuniuwiki.app;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 封装应用配置相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-14
 */
@Service
public class AppService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public AppService(MyBatisStore store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public Map<String, Object> detail(String kbId, int type) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        return getOrCreate(kbId, type);
    }

    public Map<String, Object> publicInfo(String kbId, int type) {
        Map<String, Object> app = getOrCreate(kbId, type);
        Map<String, Object> kb = store.queryForObject(
                "SELECT access_settings FROM knowledge_bases WHERE id = ?",
                store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(app.get("settings"));
        Map<String, Object> access = jsonMaps.jsonMap(kb.get("access_settings"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", app.get("name"));
        result.put("settings", settings);
        result.put("base_url", baseUrl(access));
        List<String> recommendNodeIds = settings.get("recommend_node_ids") instanceof List<?> ids
                ? ids.stream().map(String::valueOf).toList() : List.of();
        result.put("recommend_nodes", recommendNodes(kbId, recommendNodeIds));
        return result;
    }

    public void update(String id, Map<String, Object> request) {
        String kbId = String.valueOf(request.getOrDefault("kb_id", ""));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        if (request.containsKey("name")) {
            store.update("UPDATE apps SET name = ?, updated_at = now() WHERE id = ? AND kb_id = ?",
                    request.get("name"), id, kbId);
        }
        if (request.containsKey("settings")) {
            store.update("UPDATE apps SET settings = ?::jsonb, updated_at = now() WHERE id = ? AND kb_id = ?",
                    jsonMaps.json(request.get("settings")), id, kbId);
        }
    }

    public void delete(String kbId, String id) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        store.update("DELETE FROM apps WHERE id = ? AND kb_id = ?", id, kbId);
    }

    private Map<String, Object> getOrCreate(String kbId, int type) {
        List<Map<String, Object>> apps = store.query(
                "SELECT id, kb_id, name, type, settings FROM apps WHERE kb_id = ? AND type = ?",
                store.rowMapper(), kbId, type);
        if (!apps.isEmpty()) {
            return apps.getFirst();
        }
        Integer exists = store.queryForObject(
                "SELECT count(*) FROM knowledge_bases WHERE id = ?",
                Integer.class, kbId);
        if (exists == null || exists == 0) {
            throw new ApiException("Not Found");
        }
        String id = UUID.randomUUID().toString();
        store.update(
                "INSERT INTO apps(id, kb_id, name, type, settings, created_at, updated_at) "
                        + "VALUES (?, ?, '', ?, '{}'::jsonb, now(), now())",
                id, kbId, type);
        return store.queryForObject(
                "SELECT id, kb_id, name, type, settings FROM apps WHERE id = ?",
                store.rowMapper(), id);
    }

    private List<Map<String, Object>> recommendNodes(String kbId, List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> nodes = store.query("""
                SELECT nr.node_id AS id, links.nav_id, nav.name AS nav_name, nr.name, nr.type,
                       nr.meta->>'summary' AS summary, nr.parent_id, nr.position,
                       nr.meta->>'emoji' AS emoji, n.permissions
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                """, store.rowMapper(), kbId);
        return ids.stream()
                .flatMap(id -> nodes.stream().filter(node -> id.equals(String.valueOf(node.get("id")))).limit(1))
                .toList();
    }

    private String baseUrl(Map<String, Object> access) {
        String explicit = String.valueOf(access.getOrDefault("base_url", ""));
        if (!explicit.isBlank()) {
            return explicit;
        }
        List<?> hosts = access.get("hosts") instanceof List<?> list ? list : List.of();
        List<?> sslPorts = access.get("ssl_ports") instanceof List<?> list ? list : List.of();
        List<?> ports = access.get("ports") instanceof List<?> list ? list : List.of();
        if (hosts.isEmpty()) {
            return "";
        }
        String host = String.valueOf(hosts.getFirst());
        if (!sslPorts.isEmpty()) {
            int port = ((Number) sslPorts.getFirst()).intValue();
            return "https://" + host + (port == 443 ? "" : ":" + port);
        }
        if (!ports.isEmpty()) {
            int port = ((Number) ports.getFirst()).intValue();
            return "http://" + host + (port == 80 ? "" : ":" + port);
        }
        return "";
    }
}
