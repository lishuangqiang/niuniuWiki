package com.chaitin.niuniuwiki.user;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthPrincipal;
import com.chaitin.niuniuwiki.security.AuthService;
import com.chaitin.niuniuwiki.security.JwtService;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封装用户管理相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-04-15
 */
@Service
public class UserService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final NiuniuWikiProperties properties;
    private final JwtService jwtService;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            NiuniuWikiProperties properties,
            JwtService jwtService,
            AuthService authService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.properties = properties;
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @PostConstruct
    public void initializeAdmin() {
        String password = properties.getAdminPassword();
        if (password == null || password.isBlank()) {
            return;
        }
        String hash = passwordEncoder.encode(password);
        int updated = store.update("UPDATE users SET password = ?, role = 'admin' WHERE account = 'admin'", hash);
        if (updated == 0) {
            store.update(
                    "INSERT INTO users(id, account, password, role, created_at) VALUES (?, 'admin', ?, 'admin', now())",
                    UUID.randomUUID().toString(),
                    hash);
        }
    }

    public String login(String account, String password) {
        List<Map<String, Object>> rows = store.queryForList(
                "SELECT id, password FROM users WHERE account = ?",
                account);
        if (rows.isEmpty() || !passwordEncoder.matches(password, String.valueOf(rows.getFirst().get("password")))) {
            throw new ApiException("用户名或密码错误");
        }
        return jwtService.create(String.valueOf(rows.getFirst().get("id")));
    }

    public Map<String, Object> currentUser() {
        AuthPrincipal principal = AuthContext.get();
        Map<String, Object> user = store.queryForObject(
                "SELECT id, account, role, last_access, created_at FROM users WHERE id = ?",
                store.rowMapper(),
                principal.userId());
        Map<String, Object> result = new LinkedHashMap<>(user);
        result.put("is_token", principal.apiToken());
        return result;
    }

    public List<Map<String, Object>> list() {
        return store.query(
                "SELECT id, account, role, last_access, created_at FROM users ORDER BY created_at DESC",
                store.rowMapper());
    }

    public String create(UserDtos.CreateUserRequest request) {
        authService.requireAdmin();
        String id = UUID.randomUUID().toString();
        try {
            store.update(
                    "INSERT INTO users(id, account, password, role, created_at) VALUES (?, ?, ?, ?, now())",
                    id,
                    request.account(),
                    passwordEncoder.encode(request.password()),
                    request.role());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException("failed to create user: account already exists");
        }
        return id;
    }

    public void resetPassword(UserDtos.ResetPasswordRequest request) {
        authService.requireAdmin();
        AuthPrincipal principal = AuthContext.get();
        Map<String, Object> current = find(principal.userId());
        Map<String, Object> target = find(request.id());
        if ("admin".equals(current.get("account")) && principal.userId().equals(request.id())) {
            throw new ApiException("请修改安装目录下 .env 文件中的 ADMIN_PASSWORD，并重启服务使更改生效。");
        }
        if (!"admin".equals(current.get("account"))
                && "admin".equals(target.get("role"))
                && !principal.userId().equals(request.id())) {
            throw new ApiException("无法修改其他超级管理员密码");
        }
        store.update("UPDATE users SET password = ? WHERE id = ?", passwordEncoder.encode(request.newPassword()), request.id());
    }

    @Transactional
    public void delete(String userId) {
        authService.requireAdmin();
        AuthPrincipal principal = AuthContext.get();
        if (principal.userId().equals(userId)) {
            throw new ApiException("cannot delete yourself");
        }
        Map<String, Object> current = find(principal.userId());
        Map<String, Object> target = find(userId);
        if ("admin".equals(target.get("account"))) {
            throw new ApiException("cannot delete admin user");
        }
        if (!"admin".equals(current.get("account")) && !"user".equals(target.get("role"))) {
            throw new ApiException("cannot delete other admin users");
        }
        store.update("DELETE FROM kb_users WHERE user_id = ?", userId);
        store.update("DELETE FROM users WHERE id = ?", userId);
    }

    private Map<String, Object> find(String id) {
        List<Map<String, Object>> rows = store.queryForList(
                "SELECT id, account, role FROM users WHERE id = ?",
                id);
        if (rows.isEmpty()) {
            throw new ApiException("Not Found");
        }
        return rows.getFirst();
    }
}
