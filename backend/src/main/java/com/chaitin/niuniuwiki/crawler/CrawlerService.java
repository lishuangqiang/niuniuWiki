package com.chaitin.niuniuwiki.crawler;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.chaitin.niuniuwiki.security.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 封装内容抓取相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-04-13
 */
@Service
public class CrawlerService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NiuniuWikiProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CrawlerService(NiuniuWikiProperties properties, ObjectMapper objectMapper, AuthService authService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    public Map<String, Object> parse(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        String source = value(request.get("crawler_source"));
        String key = value(request.get("key"));
        String id = extractUuid(key);
        Map<String, Object> response;
        if (List.of("url", "rss", "sitemap").contains(source)) {
            response = get("/api/docs/" + source + "/list?url=" + encode(key) + "&uuid=" + encode(id));
        } else if ("feishu".equals(source)) {
            Map<String, Object> setting = map(request.get("feishu_setting"));
            String query = "?uuid=" + encode(id)
                    + "&app_id=" + encode(value(setting.get("app_id")))
                    + "&app_secret=" + encode(value(setting.get("app_secret")))
                    + "&access_token=" + encode(value(setting.get("user_access_token")))
                    + "&space_id=" + encode(value(setting.get("space_id")));
            response = get("/api/docs/feishu/list" + query);
        } else if ("dingtalk".equals(source)) {
            Map<String, Object> body = map(request.get("dingtalk_setting"));
            body.put("uuid", id);
            response = post("/api/docs/dingtalk/list", body);
        } else {
            String url = key;
            if (List.of("file", "epub", "yuque", "siyuan", "mindoc", "wikijs", "confluence").contains(source)) {
                url = "http://niuniu-wiki-minio:9000/static-file/" + key;
            }
            String endpointSource = "epub".equals(source) ? "epubp" : source;
            response = post("/api/docs/" + endpointSource + "/list", Map.of(
                    "url", url,
                    "filename", value(request.get("filename")),
                    "uuid", id,
                    "integration", key));
        }
        ensureSuccess(response);
        Map<String, Object> data = map(response.get("data"));
        return Map.of("id", id, "docs", data.getOrDefault("docs", Map.of()));
    }

    public Map<String, Object> export(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        boolean feishu = !value(request.get("space_id")).isBlank();
        String path = feishu ? "/api/docs/feishu/export" : "/api/docs/url/export";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("uuid", request.get("id"));
        body.put("doc_id", request.get("doc_id"));
        body.put("file_type", request.getOrDefault("file_type", ""));
        body.put("space_id", request.getOrDefault("space_id", ""));
        body.put("uploader", Map.of(
                "type", 1,
                "http", Map.of("url", properties.getCrawler().getUploaderUrl()),
                "dir", "/" + kbId));
        Map<String, Object> response = post(path, body);
        ensureSuccess(response);
        return Map.of("task_id", value(response.get("data")));
    }

    public Map<String, Object> result(String taskId) {
        Map<String, Object> response = post("/api/tasks/list", Map.of("ids", List.of(taskId)));
        ensureSuccess(response);
        List<Map<String, Object>> tasks = listOfMaps(response.get("data"));
        if (tasks.isEmpty()) {
            throw new ApiException("data list is empty");
        }
        return taskResult(tasks.getFirst());
    }

    public Map<String, Object> results(List<String> taskIds) {
        Map<String, Object> response = post("/api/tasks/list", Map.of("ids", taskIds));
        ensureSuccess(response);
        List<Map<String, Object>> items = new ArrayList<>();
        String overall = "completed";
        for (Map<String, Object> task : listOfMaps(response.get("data"))) {
            Map<String, Object> result = taskResult(task);
            if (!"completed".equals(result.get("status"))) {
                overall = "pending";
            }
            items.add(Map.of(
                    "task_id", value(task.get("task_id")),
                    "status", result.get("status"),
                    "content", result.getOrDefault("content", "")));
        }
        return Map.of("status", overall, "list", items);
    }

    private Map<String, Object> taskResult(Map<String, Object> task) {
        String status = value(task.get("status"));
        if ("pending".equals(status) || "in_process".equals(status)) {
            return Map.of("status", "pending", "content", "");
        }
        if ("failed".equals(status)) {
            throw new ApiException("file crawl failed: " + value(task.get("err")));
        }
        if (!"completed".equals(status)) {
            throw new ApiException("unsupported task status: " + status);
        }
        String markdown = value(task.get("markdown"));
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(base() + "/api/tasks/download" + markdown)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return Map.of("status", "completed", "content", response.body());
        } catch (Exception exception) {
            throw new ApiException("download crawler result failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build());
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return send(HttpRequest.newBuilder(URI.create(base() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build());
        } catch (Exception exception) {
            throw new ApiException("crawler request failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("crawler service returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("crawler request failed: " + exception.getMessage());
        }
    }

    private void ensureSuccess(Map<String, Object> response) {
        if (!Boolean.TRUE.equals(response.get("success"))) {
            throw new ApiException(value(response.getOrDefault("msg", response.get("err"))));
        }
    }

    private String extractUuid(String key) {
        String filename = key.substring(key.lastIndexOf('/') + 1);
        int dot = filename.indexOf('.');
        String candidate = dot < 0 ? filename : filename.substring(0, dot);
        try {
            return UUID.fromString(candidate).toString();
        } catch (Exception ignored) {
            return UUID.randomUUID().toString();
        }
    }

    private String base() {
        return properties.getCrawler().getBaseUrl().replaceAll("/+$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new java.util.LinkedHashMap<>();
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> map(item)).toList();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
