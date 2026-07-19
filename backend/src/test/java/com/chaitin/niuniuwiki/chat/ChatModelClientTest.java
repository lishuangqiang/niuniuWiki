package com.chaitin.niuniuwiki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 验证推理客户端能够探测并缓存 OpenAI 兼容网关的输出 Token 参数能力。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
class ChatModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToNoTokenParameterAndCachesCapability() throws Exception {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = objectMapper.readTree(readBody(exchange));
            requests.add(request);
            if (request.has("max_tokens") || request.has("max_completion_tokens")) {
                respond(exchange, 400, "{\"error\":{\"message\":\"Upstream request failed\"}}");
                return;
            }
            respond(exchange, 200, successfulCompletion("OK"));
        });
        server.start();

        ChatModelClient client = client(Map.of("max_tokens", 8192));

        ModelGateway.Completion first = client.complete(
                "Reply OK", "test", CancellationSignal.none(), 64, Duration.ofSeconds(5));
        ModelGateway.Completion second = client.complete(
                "Reply OK", "test again", CancellationSignal.none(), 64, Duration.ofSeconds(5));

        assertThat(first.content()).isEqualTo("OK");
        assertThat(second.content()).isEqualTo("OK");
        assertThat(requests).hasSize(4);
        assertThat(requests.get(0).path("max_tokens").asInt()).isEqualTo(64);
        assertThat(requests.get(1).path("max_completion_tokens").asInt()).isEqualTo(64);
        assertThat(requests.get(2).has("max_tokens")).isFalse();
        assertThat(requests.get(2).has("max_completion_tokens")).isFalse();
        assertThat(requests.get(3).has("max_tokens")).isFalse();
        assertThat(requests.get(3).has("max_completion_tokens")).isFalse();
    }

    @Test
    void honorsExplicitCompletionTokenParameter() throws Exception {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(objectMapper.readTree(readBody(exchange)));
            respond(exchange, 200, successfulCompletion("configured"));
        });
        server.start();

        ChatModelClient client = client(Map.of(
                "output_token_parameter", "max_completion_tokens",
                "max_completion_tokens", 48));

        ModelGateway.Completion completion = client.complete(
                "Reply OK", "test", CancellationSignal.none(), 100, Duration.ofSeconds(5));

        assertThat(completion.content()).isEqualTo("configured");
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().has("max_tokens")).isFalse();
        assertThat(requests.getFirst().path("max_completion_tokens").asInt()).isEqualTo(48);
    }

    private ChatModelClient client(Map<String, Object> parameters) {
        JsonMaps jsonMaps = new JsonMaps(objectMapper);
        JdbcMaps store = new JdbcMaps(mock(JdbcTemplate.class), jsonMaps) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> query(String statement, RowMapper<T> rowMapper, Object... arguments) {
                if (statement.contains("system_settings")) {
                    return (List<T>) List.of(Map.of("value", Map.of("mode", "manual")));
                }
                if (statement.contains("FROM models")) {
                    return (List<T>) List.of(Map.of(
                            "provider", "Test",
                            "id", "test-model-id",
                            "model", "test-model",
                            "base_url", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                            "api_key", "test-key",
                            "api_header", "",
                            "parameters", parameters));
                }
                return List.of();
            }
        };
        return new ChatModelClient(store, jsonMaps, objectMapper);
    }

    private static String readBody(HttpExchange exchange) throws java.io.IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static String successfulCompletion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"}}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":1}}";
    }
}
