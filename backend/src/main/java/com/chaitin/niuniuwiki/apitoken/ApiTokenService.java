package com.chaitin.niuniuwiki.apitoken;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 管理知识库 API Token，并对权限值做固定白名单校验。
 *
 * @author 程序员牛肉
 * @since 2026-06-18
 */
@Service
public class ApiTokenService {

    private static final Set<String> PERMISSIONS = Set.of(
            AuthService.FULL_CONTROL,
            AuthService.DOC_MANAGE,
            AuthService.DATA_OPERATE);

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(MyBatisStore store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public Map<String, Object> create(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        String name = value(request.get("name")).strip();
        String permission = value(request.get("permission"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        if (name.isBlank() || name.length() > 100) {
            throw new ApiException("Token 名称长度必须在 1 到 100 个字符之间");
        }
        validatePermission(permission);
        String id = UUID.randomUUID().toString();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String token = "nw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        store.update(
                "INSERT INTO api_tokens(id, kb_id, name, user_id, token, permission, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, kbId, name, AuthContext.get().userId(), token, permission);
        return Map.of("id", id, "token", token);
    }

    public List<Map<String, Object>> list(String kbId) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        return store.query(
                "SELECT id, name, token, permission, created_at, updated_at FROM api_tokens "
                        + "WHERE kb_id = ? ORDER BY created_at DESC",
                store.rowMapper(), kbId);
    }

    public void update(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        String id = value(request.get("id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        String name = request.containsKey("name") ? value(request.get("name")).strip() : null;
        String permission = request.containsKey("permission") ? value(request.get("permission")) : null;
        if (name != null && (name.isBlank() || name.length() > 100)) {
            throw new ApiException("Token 名称长度必须在 1 到 100 个字符之间");
        }
        if (permission != null) {
            validatePermission(permission);
        }
        int changed = store.update(
                "UPDATE api_tokens SET name = COALESCE(?, name), permission = COALESCE(?, permission), "
                        + "updated_at = now() WHERE id = ? AND kb_id = ?",
                name, permission, id, kbId);
        if (changed == 0) {
            throw new ApiException("API Token 不存在");
        }
    }

    public void delete(String kbId, String id) {
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        if (store.update("DELETE FROM api_tokens WHERE id = ? AND kb_id = ?", id, kbId) == 0) {
            throw new ApiException("API Token 不存在");
        }
    }

    private void validatePermission(String permission) {
        if (!PERMISSIONS.contains(permission)) {
            throw new ApiException("无效的 API Token 权限");
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
