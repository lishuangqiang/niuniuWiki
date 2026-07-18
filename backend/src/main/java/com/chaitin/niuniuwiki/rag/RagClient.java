package com.chaitin.niuniuwiki.rag;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 提供与原 Go 后端协议兼容的 RAGLite HTTP 访问能力。
 *
 * @author 程序员牛肉
 * @since 2026-04-01
 */
@Component
public class RagClient {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final NiuniuWikiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RagClient(NiuniuWikiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createDataset() {
        Map<String, Object> data = request("POST", "/api/v1/datasets", Map.of("name", UUID.randomUUID().toString()));
        return required(data, "id");
    }

    public void deleteDataset(String datasetId) {
        request("DELETE", "/api/v1/datasets/" + encode(datasetId), null);
    }

    public String uploadDocument(
            String datasetId,
            String releaseId,
            String documentId,
            String title,
            String content,
            List<Integer> groupIds
    ) {
        try {
            String boundary = "NiuniuWiki-" + UUID.randomUUID();
            byte[] body = multipart(boundary, releaseId + ".md", content, documentId, title, groupIds);
            HttpRequest request = baseRequest("/api/v1/datasets/" + encode(datasetId) + "/documents")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofMinutes(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            Map<String, Object> data = execute(request);
            return required(data, "document_id");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("RAG 文档上传失败: " + exception.getMessage());
        }
    }

    public void deleteDocuments(String datasetId, List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        request("POST", "/api/v1/datasets/" + encode(datasetId) + "/documents/batch-delete",
                Map.of("document_ids", documentIds));
    }

    public void updateDocumentGroups(String datasetId, String documentId, List<Integer> groupIds) {
        request("PATCH", "/api/v1/datasets/" + encode(datasetId) + "/documents/" + encode(documentId),
                Map.of("metadata", Map.of("group_ids", groupIds == null ? List.of() : groupIds)));
    }

    public List<Map<String, Object>> retrieve(
            String datasetId,
            String query,
            List<Integer> groupIds,
            List<Map<String, Object>> history
    ) {
        return retrieve(datasetId, query, groupIds, history, CancellationSignal.none());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> retrieve(
            String datasetId,
            String query,
            List<Integer> groupIds,
            List<Map<String, Object>> history,
            CancellationSignal cancellationSignal
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset_id", datasetId);
        payload.put("query", query);
        payload.put("top_k", 10);
        payload.put("max_chunks_per_doc", 3);
        if (groupIds != null && !groupIds.isEmpty()) {
            payload.put("metadata", Map.of("group_ids", groupIds));
        }
        payload.put("chat_history", history == null ? List.of() : history);
        Object results = request("POST", "/api/v1/search", payload, cancellationSignal).get("results");
        if (!(results instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> typed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                typed.add((Map<String, Object>) map);
            }
        }
        return typed;
    }

    public List<Map<String, Object>> listDocuments(String datasetId, List<String> documentIds) {
        String ids = String.join(",", documentIds == null ? List.of() : documentIds);
        String path = "/api/v1/datasets/" + encode(datasetId) + "/documents?page_size=0";
        if (!ids.isBlank()) {
            path += "&document_ids=" + encode(ids);
        }
        Object documents = request("GET", path, null).get("documents");
        if (!(documents instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> typed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) map;
                typed.add(row);
            }
        }
        return typed;
    }

    public void upsertModel(Map<String, Object> model) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("api_base", model.getOrDefault("base_url", ""));
        config.put("api_key", model.getOrDefault("api_key", ""));
        config.put("api_header", model.getOrDefault("api_header", ""));
        config.put("api_version", model.getOrDefault("api_version", ""));
        config.put("extra_parameters", model.getOrDefault("parameters", Map.of()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", model.get("model"));
        payload.put("provider", model.get("provider"));
        payload.put("model_name", model.get("model"));
        payload.put("model_type", model.get("type"));
        payload.put("config", config);
        payload.put("is_default", true);
        payload.put("is_active", model.getOrDefault("is_active", true));
        request("POST", "/api/v1/models/upsert", payload);
    }

    private Map<String, Object> request(String method, String path, Object body) {
        return request(method, path, body, CancellationSignal.none());
    }

    private Map<String, Object> request(
            String method,
            String path,
            Object body,
            CancellationSignal cancellationSignal
    ) {
        try {
            HttpRequest.Builder builder = baseRequest(path).timeout(Duration.ofMinutes(2));
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            return execute(builder.build(), cancellationSignal);
        } catch (CancellationException exception) {
            throw exception;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("RAG 请求失败: " + exception.getMessage());
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        String base = properties.getRag().getBaseUrl().replaceAll("/+$", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Accept", "application/json");
        if (!properties.getRag().getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getRag().getApiKey());
        }
        return builder;
    }

    private Map<String, Object> execute(HttpRequest request) throws Exception {
        return execute(request, CancellationSignal.none());
    }

    private Map<String, Object> execute(HttpRequest request, CancellationSignal cancellationSignal) throws Exception {
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            while (true) {
                cancellationSignal.check();
                try {
                    response = future.get(250, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // Poll cancellation so a closed SSE request stops the remote retrieval as well.
                }
            }
        } catch (RuntimeException | InterruptedException exception) {
            future.cancel(true);
            throw exception;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException("RAG 请求失败(" + response.statusCode() + "): " + response.body());
        }
        if (response.body() == null || response.body().isBlank()) {
            return Map.of();
        }
        Map<String, Object> envelope = objectMapper.readValue(response.body(), MAP);
        if (Boolean.FALSE.equals(envelope.get("success"))) {
            throw new ApiException("RAG 请求失败: " + envelope.getOrDefault("message", "unknown error"));
        }
        Object data = envelope.get("data");
        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return envelope;
    }

    private byte[] multipart(
            String boundary,
            String filename,
            String content,
            String documentId,
            String title,
            List<Integer> groupIds
    ) throws Exception {
        String line = "\r\n";
        StringBuilder head = new StringBuilder();
        field(head, boundary, "document_id", documentId);
        field(head, boundary, "title", title);
        field(head, boundary, "metadata", objectMapper.writeValueAsString(
                Map.of("group_ids", groupIds == null ? List.of() : groupIds)));
        head.append("--").append(boundary).append(line)
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(filename.replace("\"", "")).append("\"").append(line)
                .append("Content-Type: text/markdown; charset=UTF-8").append(line).append(line);
        byte[] prefix = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] file = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = (line + "--" + boundary + "--" + line).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + file.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(file, 0, body, prefix.length, file.length);
        System.arraycopy(suffix, 0, body, prefix.length + file.length, suffix.length);
        return body;
    }

    private static void field(StringBuilder body, String boundary, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String required(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (result == null || String.valueOf(result).isBlank()) {
            throw new ApiException("RAG 响应缺少字段: " + key);
        }
        return String.valueOf(result);
    }
}
