package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 封装公开访问相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-04-22
 */
@Service
public class ShareAuthService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final ShareAccessService accessService;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public ShareAuthService(MyBatisStore store, JsonMaps jsonMaps, ShareAccessService accessService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.accessService = accessService;
    }

    public Map<String, Object> info(String kbId) {
        Map<String, Object> settings = accessService.settings(kbId);
        Map<String, Object> simple = jsonMaps.jsonMap(settings.get("simple_auth"));
        Map<String, Object> enterprise = jsonMaps.jsonMap(settings.get("enterprise_auth"));
        String authType = Boolean.TRUE.equals(enterprise.get("enabled")) ? "enterprise"
                : Boolean.TRUE.equals(simple.get("enabled")) && !String.valueOf(simple.getOrDefault("password", "")).isBlank()
                ? "simple" : "";
        return Map.of(
                "auth_type", authType,
                "source_type", settings.getOrDefault("source_type", ""),
                "license_edition", 2);
    }

    public Map<String, Object> sessionInfo(String kbId, HttpSession session) {
        Object authId = session.getAttribute("user_id");
        if (authId == null || !kbId.equals(session.getAttribute("kb_id"))) {
            return null;
        }
        List<Map<String, Object>> rows = store.query(
                "SELECT id, user_info FROM auths WHERE kb_id = ? AND id = ?",
                store.rowMapper(), kbId, authId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> user = jsonMaps.jsonMap(rows.getFirst().get("user_info"));
        return Map.of(
                "id", rows.getFirst().get("id"),
                "username", user.getOrDefault("username", ""),
                "avatar_url", user.getOrDefault("avatar_url", ""),
                "email", user.getOrDefault("email", ""));
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public void loginSimple(String kbId, String password, HttpSession session) {
        Map<String, Object> settings = accessService.settings(kbId);
        Map<String, Object> simple = jsonMaps.jsonMap(settings.get("simple_auth"));
        if (!Boolean.TRUE.equals(simple.get("enabled"))) {
            throw new ApiException("simple auth is not enabled");
        }
        if (!String.valueOf(simple.getOrDefault("password", "")).equals(password)) {
            throw new ApiException("simple auth password is incorrect");
        }
        session.setMaxInactiveInterval(30 * 24 * 60 * 60);
        session.setAttribute("kb_id", kbId);
    }

    public String githubUrl(String kbId, String redirectUrl) {
        Map<String, Object> settings = accessService.settings(kbId);
        validateRedirect(settings, redirectUrl);
        List<Map<String, Object>> configs = store.query(
                "SELECT auth_setting FROM auth_configs WHERE kb_id = ? AND source_type = 'github'",
                store.rowMapper(), kbId);
        if (configs.isEmpty()) {
            throw new ApiException("github auth is not configured");
        }
        Map<String, Object> config = jsonMaps.jsonMap(configs.getFirst().get("auth_setting"));
        String clientId = String.valueOf(config.getOrDefault("client_id", ""));
        String state = UUID.randomUUID().toString();
        states.put(state, new State(kbId, redirectUrl, Instant.now().plusSeconds(900)));
        URI redirect = URI.create(redirectUrl);
        String callback = redirect.getScheme() + "://" + redirect.getAuthority() + "/share/v1/openapi/github/callback";
        return "https://github.com/login/oauth/authorize?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callback) + "&scope=user%3Aemail&state=" + encode(state);
    }

    public State consumeState(String state) {
        State value = states.remove(state);
        if (value == null || value.expiresAt().isBefore(Instant.now())) {
            throw new ApiException("state info not found");
        }
        return value;
    }

    private void validateRedirect(Map<String, Object> settings, String redirectUrl) {
        String host = URI.create(redirectUrl).getHost();
        String baseUrl = String.valueOf(settings.getOrDefault("base_url", ""));
        boolean valid;
        if (!baseUrl.isBlank()) {
            valid = URI.create(baseUrl).getHost().equalsIgnoreCase(host);
        } else if (settings.get("hosts") instanceof List<?> hosts) {
            valid = hosts.stream().map(String::valueOf).anyMatch(item -> item.equalsIgnoreCase(host));
        } else {
            valid = false;
        }
        if (!valid) {
            throw new ApiException("invalid redirect url");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record State(String kbId, String redirectUrl, Instant expiresAt) {
    }
}
