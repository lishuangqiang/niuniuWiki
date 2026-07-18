package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 实现 OAuth、钉钉、飞书、企业微信、CAS 与 LDAP 的完整登录会话流程。
 *
 * @author 程序员牛肉
 * @since 2026-07-03
 */
@Service
public class ExternalAuthService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Pattern CAS_USER = Pattern.compile(
            "<(?:\\w+:)?user>([^<]+)</(?:\\w+:)?user>", Pattern.CASE_INSENSITIVE);

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;
    private final ShareAccessService accessService;
    private final ConcurrentHashMap<String, LoginState> states = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ExternalAuthService(
            MyBatisStore store,
            JsonMaps jsonMaps,
            ObjectMapper objectMapper,
            ShareAccessService accessService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    public String authorizationUrl(String kbId, String sourceType, String redirectUrl, boolean app) {
        validateRedirect(kbId, redirectUrl);
        Map<String, Object> config = config(kbId, sourceType);
        String state = UUID.randomUUID().toString();
        states.put(state, new LoginState(kbId, sourceType, redirectUrl, Instant.now().plusSeconds(900)));
        String callback = callbackUrl(redirectUrl, sourceType);
        return switch (sourceType) {
            case "github" -> appendQuery("https://github.com/login/oauth/authorize", Map.of(
                    "client_id", required(config, "client_id"),
                    "redirect_uri", callback,
                    "scope", "user:email",
                    "state", state));
            case "oauth" -> appendQuery(required(config, "authorize_url"), Map.of(
                    "response_type", "code",
                    "client_id", required(config, "client_id"),
                    "redirect_uri", callback,
                    "scope", scopes(config),
                    "state", state));
            case "dingtalk" -> appendQuery("https://login.dingtalk.com/oauth2/auth", Map.of(
                    "redirect_uri", callback,
                    "response_type", "code",
                    "client_id", required(config, "client_id"),
                    "scope", "openid",
                    "state", state,
                    "prompt", "consent"));
            case "feishu" -> appendQuery("https://open.feishu.cn/open-apis/authen/v1/authorize", Map.of(
                    "app_id", required(config, "client_id"),
                    "redirect_uri", callback,
                    "state", state));
            case "wecom" -> wecomUrl(config, callback, state, app);
            case "cas" -> appendQuery(trimSlash(required(config, "cas_url")) + "/login", Map.of(
                    "service", appendQuery(callback, Map.of("state", state))));
            default -> throw new ApiException("不支持的认证类型: " + sourceType);
        };
    }

    public String callback(String sourceType, String code, String ticket, String state, HttpSession session) {
        LoginState login = consume(state, sourceType);
        Map<String, Object> config = config(login.kbId(), sourceType);
        Map<String, Object> user = switch (sourceType) {
            case "github" -> github(config, code, callbackUrl(login.redirectUrl(), sourceType));
            case "oauth" -> genericOAuth(config, code, callbackUrl(login.redirectUrl(), sourceType));
            case "dingtalk" -> dingtalk(config, code);
            case "feishu" -> feishu(config, code, callbackUrl(login.redirectUrl(), sourceType));
            case "wecom" -> wecom(config, code);
            case "cas" -> cas(config, ticket, callbackUrl(login.redirectUrl(), sourceType), state);
            default -> throw new ApiException("不支持的认证回调: " + sourceType);
        };
        long authId = upsertAuth(login.kbId(), sourceType, normalizeUser(config, user, sourceType));
        establishSession(login.kbId(), authId, session);
        return login.redirectUrl();
    }

    public void ldapLogin(String kbId, String username, String password, HttpSession session) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ApiException("LDAP 用户名和密码不能为空");
        }
        Map<String, Object> config = config(kbId, "ldap");
        String server = required(config, "ldap_server_url");
        String baseDn = required(config, "user_base_dn");
        String userDn;
        Map<String, Object> user = new LinkedHashMap<>();
        DirContext searchContext = null;
        try {
            String bindDn = value(config.get("bind_dn"));
            String bindPassword = value(config.get("bind_password"));
            searchContext = context(server, bindDn, bindPassword);
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(2);
            String filterTemplate = value(config.get("user_filter"));
            if (filterTemplate.isBlank()) {
                filterTemplate = "(uid={username})";
            }
            String filter = filterTemplate
                    .replace("{username}", escapeLdap(username))
                    .replace("{0}", escapeLdap(username));
            NamingEnumeration<SearchResult> results = searchContext.search(baseDn, filter, controls);
            if (!results.hasMore()) {
                throw new ApiException("LDAP 用户不存在");
            }
            SearchResult result = results.next();
            userDn = result.getNameInNamespace();
            Attributes attributes = result.getAttributes();
            user.put("id", attribute(attributes, value(config.get("id_field")), userDn));
            user.put("username", attribute(attributes, value(config.get("name_field")), username));
            user.put("email", attribute(attributes, value(config.get("email_field")), ""));
            user.put("avatar_url", attribute(attributes, value(config.get("avatar_field")), ""));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("LDAP 查询失败: " + exception.getMessage());
        } finally {
            close(searchContext);
        }
        DirContext userContext = null;
        try {
            userContext = context(server, userDn, password);
        } catch (Exception exception) {
            throw new ApiException("LDAP 用户名或密码错误");
        } finally {
            close(userContext);
        }
        long authId = upsertAuth(kbId, "ldap", user);
        establishSession(kbId, authId, session);
    }

    private Map<String, Object> genericOAuth(Map<String, Object> config, String code, String callback) {
        String form = form(Map.of(
                "grant_type", "authorization_code",
                "client_id", required(config, "client_id"),
                "client_secret", required(config, "client_secret"),
                "code", value(code),
                "redirect_uri", callback));
        Map<String, Object> token = requestJson(HttpRequest.newBuilder(URI.create(required(config, "token_url")))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String accessToken = required(token, "access_token");
        return requestJson(HttpRequest.newBuilder(URI.create(required(config, "user_info_url")))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken).GET().build());
    }

    private Map<String, Object> github(Map<String, Object> config, String code, String callback) {
        String body = form(Map.of(
                "client_id", required(config, "client_id"),
                "client_secret", required(config, "client_secret"),
                "code", value(code),
                "redirect_uri", callback));
        Map<String, Object> token = requestJson(HttpRequest.newBuilder(
                        URI.create("https://github.com/login/oauth/access_token"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        return requestJson(HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + required(token, "access_token"))
                .header("X-GitHub-Api-Version", "2022-11-28").GET().build());
    }

    private Map<String, Object> dingtalk(Map<String, Object> config, String code) {
        Map<String, Object> token = requestJson(jsonPost(
                "https://api.dingtalk.com/v1.0/oauth2/userAccessToken",
                Map.of("clientId", required(config, "client_id"),
                        "clientSecret", required(config, "client_secret"),
                        "code", value(code), "grantType", "authorization_code")));
        return requestJson(HttpRequest.newBuilder(URI.create("https://api.dingtalk.com/v1.0/contact/users/me"))
                .header("x-acs-dingtalk-access-token", required(token, "accessToken")).GET().build());
    }

    private Map<String, Object> feishu(Map<String, Object> config, String code, String callback) {
        Map<String, Object> token = requestJson(jsonPost(
                "https://open.feishu.cn/open-apis/authen/v2/oauth/token",
                Map.of("client_id", required(config, "client_id"),
                        "client_secret", required(config, "client_secret"),
                        "code", value(code), "grant_type", "authorization_code", "redirect_uri", callback)));
        return requestJson(HttpRequest.newBuilder(URI.create("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .header("Authorization", "Bearer " + required(token, "access_token")).GET().build());
    }

    private Map<String, Object> wecom(Map<String, Object> config, String code) {
        Map<String, Object> token = requestJson(HttpRequest.newBuilder(URI.create(appendQuery(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken",
                Map.of("corpid", required(config, "client_id"),
                        "corpsecret", required(config, "client_secret"))))).GET().build());
        return requestJson(HttpRequest.newBuilder(URI.create(appendQuery(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo",
                Map.of("access_token", required(token, "access_token"), "code", value(code))))).GET().build());
    }

    private Map<String, Object> cas(Map<String, Object> config, String ticket, String callback, String state) {
        String service = appendQuery(callback, Map.of("state", state));
        String url = appendQuery(trimSlash(required(config, "cas_url")) + "/serviceValidate",
                Map.of("service", service, "ticket", value(ticket)));
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            Matcher matcher = CAS_USER.matcher(response.body());
            if (response.statusCode() / 100 != 2 || !matcher.find()) {
                throw new ApiException("CAS Ticket 校验失败");
            }
            String username = matcher.group(1);
            return Map.of("id", username, "username", username, "email", "", "avatar_url", "");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("CAS Ticket 校验失败: " + exception.getMessage());
        }
    }

    private Map<String, Object> normalizeUser(
            Map<String, Object> config,
            Map<String, Object> raw,
            String sourceType
    ) {
        if (raw.get("data") instanceof Map<?, ?> data) {
            raw = map(data);
        }
        String defaultId = switch (sourceType) {
            case "github" -> first(raw, "id");
            case "dingtalk" -> first(raw, "unionId", "openId");
            case "feishu" -> first(raw, "union_id", "open_id", "user_id");
            case "wecom" -> first(raw, "userid", "UserId", "openid", "OpenId");
            default -> first(raw, "id", "sub", "user_id");
        };
        String id = field(raw, config, "id_field", defaultId);
        if (id.isBlank()) {
            throw new ApiException("认证服务未返回用户唯一标识");
        }
        return Map.of(
                "id", id,
                "username", field(raw, config, "name_field", first(raw, "name", "login", "username", "nick", "nickName", "userid")),
                "email", field(raw, config, "email_field", first(raw, "email")),
                "avatar_url", field(raw, config, "avatar_field", first(raw, "avatar_url", "avatar", "picture")));
    }

    private long upsertAuth(String kbId, String sourceType, Map<String, Object> user) {
        String unionId = value(user.get("id"));
        Map<String, Object> info = Map.of(
                "username", value(user.get("username")),
                "avatar_url", value(user.get("avatar_url")),
                "email", value(user.get("email")));
        List<Long> existing = store.query(
                "SELECT id FROM auths WHERE kb_id = ? AND source_type = ? AND union_id = ?",
                (rs, rowNum) -> rs.getLong(1), kbId, sourceType, unionId);
        if (!existing.isEmpty()) {
            store.update("UPDATE auths SET user_info = ?::jsonb, last_login_time = now(), updated_at = now() WHERE id = ?",
                    jsonMaps.json(info), existing.getFirst());
            return existing.getFirst();
        }
        return store.queryForObject(
                "INSERT INTO auths(user_info, union_id, ip, kb_id, source_type, last_login_time, created_at, updated_at) "
                        + "VALUES (?::jsonb, ?, '', ?, ?, now(), now(), now()) RETURNING id",
                Long.class, jsonMaps.json(info), unionId, kbId, sourceType);
    }

    private void establishSession(String kbId, long authId, HttpSession session) {
        session.setMaxInactiveInterval(30 * 24 * 60 * 60);
        session.setAttribute("kb_id", kbId);
        session.setAttribute("user_id", authId);
    }

    private LoginState consume(String state, String sourceType) {
        LoginState login = states.remove(state);
        if (login == null || login.expiresAt().isBefore(Instant.now()) || !sourceType.equals(login.sourceType())) {
            throw new ApiException("登录状态已过期，请重新发起认证");
        }
        return login;
    }

    private Map<String, Object> config(String kbId, String sourceType) {
        List<Map<String, Object>> rows = store.query(
                "SELECT auth_setting FROM auth_configs WHERE kb_id = ? AND source_type = ?",
                store.rowMapper(), kbId, sourceType);
        if (rows.isEmpty()) {
            throw new ApiException(sourceType + " 认证尚未配置");
        }
        return jsonMaps.jsonMap(rows.getFirst().get("auth_setting"));
    }

    private void validateRedirect(String kbId, String redirectUrl) {
        URI redirect;
        try {
            redirect = URI.create(redirectUrl);
        } catch (Exception exception) {
            throw new ApiException("无效的登录回跳地址");
        }
        String host = redirect.getHost();
        Map<String, Object> settings = accessService.settings(kbId);
        String baseUrl = value(settings.get("base_url"));
        boolean valid = host != null && ((!baseUrl.isBlank() && host.equalsIgnoreCase(URI.create(baseUrl).getHost()))
                || settings.get("hosts") instanceof List<?> hosts
                && hosts.stream().map(String::valueOf).anyMatch(host::equalsIgnoreCase));
        if (!valid) {
            throw new ApiException("登录回跳地址不属于当前知识库");
        }
    }

    private String wecomUrl(Map<String, Object> config, String callback, String state, boolean app) {
        if (app) {
            return appendQuery("https://open.weixin.qq.com/connect/oauth2/authorize", Map.of(
                    "appid", required(config, "client_id"), "redirect_uri", callback,
                    "response_type", "code", "scope", "snsapi_base",
                    "agentid", value(config.get("agent_id")), "state", state)) + "#wechat_redirect";
        }
        return appendQuery("https://open.work.weixin.qq.com/wwopen/sso/qrConnect", Map.of(
                "appid", required(config, "client_id"), "agentid", value(config.get("agent_id")),
                "redirect_uri", callback, "state", state));
    }

    private HttpRequest jsonPost(String url, Map<String, Object> body) {
        try {
            return HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        } catch (Exception exception) {
            throw new ApiException("认证请求构建失败");
        }
    }

    private Map<String, Object> requestJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ApiException("认证服务返回 HTTP " + response.statusCode());
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
            if (body.get("errcode") instanceof Number error && error.intValue() != 0) {
                throw new ApiException("认证服务拒绝了请求: " + value(body.get("errmsg")));
            }
            return body;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("认证服务请求失败: " + exception.getMessage());
        }
    }

    private DirContext context(String server, String principal, String credential) throws Exception {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, server);
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "10000");
        if (principal != null && !principal.isBlank()) {
            environment.put(Context.SECURITY_AUTHENTICATION, "simple");
            environment.put(Context.SECURITY_PRINCIPAL, principal);
            environment.put(Context.SECURITY_CREDENTIALS, credential == null ? "" : credential);
        }
        return new InitialDirContext(environment);
    }

    private static String callbackUrl(String redirectUrl, String sourceType) {
        URI redirect = URI.create(redirectUrl);
        return redirect.getScheme() + "://" + redirect.getAuthority()
                + "/share/pro/v1/openapi/" + sourceType + "/callback";
    }

    private static String appendQuery(String url, Map<String, String> values) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + form(values);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String scopes(Map<String, Object> config) {
        Object value = config.get("scopes");
        return value instanceof List<?> list ? String.join(" ", list.stream().map(String::valueOf).toList()) : "openid profile email";
    }

    private static String field(Map<String, Object> raw, Map<String, Object> config, String configKey, String fallback) {
        String field = value(config.get(configKey));
        return field.isBlank() ? value(fallback) : value(raw.get(field));
    }

    private static String first(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = value(source.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String attribute(Attributes attributes, String field, String fallback) throws Exception {
        if (field == null || field.isBlank()) {
            return fallback;
        }
        Attribute attribute = attributes.get(field);
        return attribute == null || attribute.get() == null ? fallback : String.valueOf(attribute.get());
    }

    private static Map<String, Object> map(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String required(Map<String, Object> values, String key) {
        String value = value(values.get(key));
        if (value.isBlank()) {
            throw new ApiException("认证配置缺少 " + key);
        }
        return value;
    }

    private static String trimSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private static String escapeLdap(String value) {
        return value.replace("\\", "\\5c").replace("*", "\\2a")
                .replace("(", "\\28").replace(")", "\\29").replace("\0", "\\00");
    }

    private static void close(DirContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (Exception ignored) {
                // No action is needed after a best-effort LDAP close.
            }
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record LoginState(String kbId, String sourceType, String redirectUrl, Instant expiresAt) {
    }
}
