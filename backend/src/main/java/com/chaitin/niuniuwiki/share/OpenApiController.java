package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.chat.ChatService;
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
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理公开访问相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-06
 */
@RestController
@RequestMapping("/share/v1/openapi")
public class OpenApiController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiController.class);

    private final ShareAuthService authService;
    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final Set<String> processedLarkEvents = ConcurrentHashMap.newKeySet();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenApiController(
            ShareAuthService authService,
            MyBatisStore store,
            JsonMaps jsonMaps,
            ObjectMapper objectMapper,
            ChatService chatService
    ) {
        this.authService = authService;
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    @GetMapping("/github/callback")
    public ResponseEntity<?> githubCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session
    ) {
        try {
            ShareAuthService.State stateInfo = authService.consumeState(state);
            Map<String, Object> config = githubConfig(stateInfo.kbId());
            String callback = callbackUrl(stateInfo.redirectUrl());
            String form = "client_id=" + encode(value(config.get("client_id")))
                    + "&client_secret=" + encode(value(config.get("client_secret")))
                    + "&code=" + encode(code) + "&redirect_uri=" + encode(callback);
            HttpResponse<String> tokenResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token"))
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenBody = objectMapper.readValue(tokenResponse.body(), MAP_TYPE);
            String accessToken = value(tokenBody.get("access_token"));
            if (accessToken.isBlank()) {
                throw new ApiException("GitHub token exchange failed");
            }
            HttpResponse<String> userResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                            .header("Accept", "application/vnd.github+json")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> user = objectMapper.readValue(userResponse.body(), MAP_TYPE);
            long authId = upsertAuth(stateInfo.kbId(), user);
            session.setMaxInactiveInterval(30 * 24 * 60 * 60);
            session.setAttribute("kb_id", stateInfo.kbId());
            session.setAttribute("user_id", authId);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, stateInfo.redirectUrl())
                    .build();
        } catch (Exception exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException("handle callback failed: " + exception.getMessage());
        }
    }

    @PostMapping("/lark/bot/{kb_id}")
    public Object larkBot(@PathVariable("kb_id") String kbId, @RequestBody Map<String, Object> request) {
        Map<String, Object> settings = larkSettings(kbId);
        if (request.get("challenge") != null) {
            verifyLarkToken(settings, request);
            return Map.of("challenge", request.get("challenge"));
        }
        if (!Boolean.TRUE.equals(settings.get("is_enabled"))) {
            throw new ApiException("lark bot is not enabled");
        }
        verifyLarkToken(settings, request);
        Map<String, Object> header = map(request.get("header"));
        Map<String, Object> event = map(request.get("event"));
        Map<String, Object> message = map(event.get("message"));
        if (!"im.message.receive_v1".equals(value(header.get("event_type")))
                || !"text".equals(value(message.get("message_type")))) {
            return Map.of("code", 0, "msg", "success");
        }
        String eventId = value(header.get("event_id"));
        if (!eventId.isBlank() && !processedLarkEvents.add(eventId)) {
            return Map.of("code", 0, "msg", "success");
        }
        String chatId = value(message.get("chat_id"));
        String text = textContent(value(message.get("content")));
        if (!chatId.isBlank() && !text.isBlank()) {
            CompletableFuture.runAsync(() -> {
                try {
                    ChatService.ChatResult result = chatService.ask(
                            kbId, 11, text, null, null, "", List.of(), List.of());
                    sendLarkMessage(settings, chatId, result.answer());
                } catch (Exception exception) {
                    // Lark retries the webhook; synchronous acknowledgement must remain fast.
                    LOGGER.error("Failed to answer Lark event {}", eventId, exception);
                }
            });
        }
        return Map.of("code", 0, "msg", "success");
    }

    private Map<String, Object> larkSettings(String kbId) {
        List<Map<String, Object>> apps = store.query(
                "SELECT settings FROM apps WHERE kb_id = ? AND type = 11",
                store.rowMapper(), kbId);
        if (apps.isEmpty()) {
            throw new ApiException("lark bot is not configured");
        }
        return jsonMaps.jsonMap(jsonMaps.jsonMap(apps.getFirst().get("settings")).get("lark_bot_settings"));
    }

    private void verifyLarkToken(Map<String, Object> settings, Map<String, Object> request) {
        String expected = value(settings.get("verify_token"));
        String actual = value(map(request.get("header")).get("token"));
        if (actual.isBlank()) {
            actual = value(request.get("token"));
        }
        if (!expected.isBlank() && !expected.equals(actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid lark verification token");
        }
    }

    private String textContent(String content) {
        try {
            return value(objectMapper.readValue(content, MAP_TYPE).get("text")).strip();
        } catch (Exception exception) {
            return content.strip();
        }
    }

    private void sendLarkMessage(Map<String, Object> settings, String chatId, String answer) throws Exception {
        Map<String, Object> tokenRequest = Map.of(
                "app_id", value(settings.get("app_id")),
                "app_secret", value(settings.get("app_secret")));
        HttpResponse<String> tokenResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create("https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(tokenRequest)))
                        .build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> tokenBody = objectMapper.readValue(tokenResponse.body(), MAP_TYPE);
        String accessToken = value(tokenBody.get("tenant_access_token"));
        if (accessToken.isBlank()) {
            throw new ApiException("failed to obtain lark tenant token");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receive_id", chatId);
        payload.put("msg_type", "text");
        payload.put("content", objectMapper.writeValueAsString(Map.of("text", answer)));
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(
                                "https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=chat_id"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                        .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException("lark message failed: " + response.body());
        }
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> githubConfig(String kbId) {
        Map<String, Object> row = store.queryForObject(
                "SELECT auth_setting FROM auth_configs WHERE kb_id = ? AND source_type = 'github'",
                store.rowMapper(), kbId);
        return jsonMaps.jsonMap(row.get("auth_setting"));
    }

    private long upsertAuth(String kbId, Map<String, Object> user) {
        String unionId = value(user.get("id"));
        Map<String, Object> info = Map.of(
                "username", value(user.get("login")),
                "avatar_url", value(user.get("avatar_url")),
                "email", value(user.get("email")));
        List<Long> existing = store.query(
                "SELECT id FROM auths WHERE kb_id = ? AND source_type = 'github' AND union_id = ?",
                (rs, rowNum) -> rs.getLong(1), kbId, unionId);
        if (!existing.isEmpty()) {
            store.update("UPDATE auths SET user_info = ?::jsonb, last_login_time = now(), updated_at = now() WHERE id = ?",
                    jsonMaps.json(info), existing.getFirst());
            return existing.getFirst();
        }
        return store.queryForObject(
                "INSERT INTO auths(user_info, union_id, ip, kb_id, source_type, last_login_time, created_at, updated_at) "
                        + "VALUES (?::jsonb, ?, '', ?, 'github', now(), now(), now()) RETURNING id",
                Long.class, jsonMaps.json(info), unionId, kbId);
    }

    private String callbackUrl(String redirectUrl) {
        URI redirect = URI.create(redirectUrl);
        return redirect.getScheme() + "://" + redirect.getAuthority() + "/share/v1/openapi/github/callback";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
