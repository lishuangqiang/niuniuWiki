package com.chaitin.niuniuwiki.security;

import com.chaitin.niuniuwiki.common.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 封装安全认证相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-07-01
 */
@Service
public class AuthService {

    public static final String FULL_CONTROL = "full_control";
    public static final String DOC_MANAGE = "doc_manage";
    public static final String DATA_OPERATE = "data_operate";

    private final JdbcMaps store;
    private final JwtService jwtService;

    public AuthService(JdbcMaps store, JwtService jwtService) {
        this.store = store;
        this.jwtService = jwtService;
    }

    public AuthPrincipal authenticate(String bearerToken) {
        if (!bearerToken.contains(".")) {
            List<Map<String, Object>> tokens = store.queryForList(
                    "SELECT user_id, kb_id, permission FROM api_tokens WHERE token = ?",
                    bearerToken);
            if (tokens.isEmpty()) {
                throw unauthorized();
            }
            Map<String, Object> token = tokens.getFirst();
            return new AuthPrincipal(
                    String.valueOf(token.get("user_id")),
                    true,
                    String.valueOf(token.get("kb_id")),
                    String.valueOf(token.get("permission")),
                    "user");
        }

        String userId = jwtService.verifyAndGetUserId(bearerToken);
        List<Map<String, Object>> users = store.queryForList(
                "SELECT id, role FROM users WHERE id = ?",
                userId);
        if (users.isEmpty()) {
            throw unauthorized();
        }
        String role = String.valueOf(users.getFirst().get("role"));
        store.update("UPDATE users SET last_access = now() WHERE id = ?", userId);
        return new AuthPrincipal(userId, false, "", "", role);
    }

    public void requireAdmin() {
        AuthPrincipal principal = AuthContext.get();
        if (principal.apiToken() || !"admin".equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission Denied", 40003);
        }
    }

    public void requireKbPermission(String kbId, String permission) {
        AuthPrincipal principal = AuthContext.get();
        if (principal.apiToken()) {
            boolean allowed = principal.kbId().equals(kbId)
                    && (FULL_CONTROL.equals(principal.permission())
                    || permission.equals(principal.permission())
                    || "not null".equals(permission) && !principal.permission().isBlank());
            if (!allowed) {
                throw forbidden();
            }
            return;
        }
        if ("admin".equals(principal.role())) {
            return;
        }
        List<String> permissions = store.query(
                "SELECT perm FROM kb_users WHERE kb_id = ? AND user_id = ?",
                (rs, rowNum) -> rs.getString(1),
                kbId,
                principal.userId());
        boolean allowed = !permissions.isEmpty()
                && (FULL_CONTROL.equals(permissions.getFirst())
                || permission.equals(permissions.getFirst())
                || "not null".equals(permission) && !permissions.getFirst().isBlank());
        if (!allowed) {
            throw forbidden();
        }
    }

    public String permissionFor(String kbId) {
        AuthPrincipal principal = AuthContext.get();
        if (principal.apiToken()) {
            return principal.kbId().equals(kbId) ? principal.permission() : "";
        }
        if ("admin".equals(principal.role())) {
            return FULL_CONTROL;
        }
        return store.query(
                        "SELECT perm FROM kb_users WHERE kb_id = ? AND user_id = ?",
                        (rs, rowNum) -> rs.getString(1),
                        kbId,
                        principal.userId())
                .stream().findFirst().orElse("");
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "Permission Denied", 40003);
    }
}
