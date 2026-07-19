package com.chaitin.niuniuwiki.auth;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 封装认证配置相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-18
 */
@Service
public class AuthConfigService {

    private static final Set<String> SETTING_KEYS = Set.of(
            "agent_id", "authorize_url", "avatar_field", "bind_dn", "bind_password",
            "cas_url", "cas_version", "client_id", "client_secret", "email_field",
            "id_field", "ldap_server_url", "name_field", "proxy", "scopes",
            "token_url", "user_base_dn", "user_filter", "user_info_url");

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public AuthConfigService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public Map<String, Object> get(String kbId, String sourceType) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        List<Map<String, Object>> configs = store.query(
                "SELECT auth_setting, source_type FROM auth_configs WHERE kb_id = ? AND source_type = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                store.rowMapper(), kbId, sourceType);
        if (configs.isEmpty()) {
            return null;
        }
        Map<String, Object> settings = jsonMaps.jsonMap(configs.getFirst().get("auth_setting"));
        List<Map<String, Object>> auths = store.query(
                "SELECT id, user_info, ip, source_type, last_login_time, created_at FROM auths "
                        + "WHERE kb_id = ? AND source_type = ? ORDER BY last_login_time DESC",
                store.rowMapper(), kbId, sourceType);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> auth : auths) {
            Map<String, Object> user = jsonMaps.jsonMap(auth.get("user_info"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", auth.get("id"));
            item.put("username", user.getOrDefault("username", ""));
            item.put("avatar_url", user.getOrDefault("avatar_url", ""));
            item.put("ip", auth.get("ip"));
            item.put("source_type", auth.get("source_type"));
            item.put("last_login_time", auth.get("last_login_time"));
            item.put("created_at", auth.get("created_at"));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>(settings);
        result.put("source_type", sourceType);
        result.put("auths", items);
        return result;
    }

    public void set(Map<String, Object> request) {
        String kbId = String.valueOf(request.getOrDefault("kb_id", ""));
        String sourceType = String.valueOf(request.getOrDefault("source_type", ""));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        Map<String, Object> setting = new LinkedHashMap<>();
        SETTING_KEYS.forEach(key -> {
            if (request.containsKey(key)) {
                setting.put(key, request.get(key));
            }
        });
        store.update(
                "INSERT INTO auth_configs(kb_id, auth_setting, source_type, created_at, updated_at) "
                        + "VALUES (?, ?::jsonb, ?, now(), now()) "
                        + "ON CONFLICT (source_type, kb_id) DO UPDATE SET auth_setting = EXCLUDED.auth_setting, updated_at = now()",
                kbId, jsonMaps.json(setting), sourceType);
    }

    public void delete(String kbId, long id) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        store.update("DELETE FROM auths WHERE kb_id = ? AND id = ?", kbId, id);
    }
}
