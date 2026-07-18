package com.chaitin.niuniuwiki.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import com.chaitin.niuniuwiki.rag.RagClient;
import com.chaitin.niuniuwiki.security.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证模型连接检查对供应商专有协议的兼容性。
 *
 * @author 程序员牛肉
 * @since 2026-07-16
 */
class ModelServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void checksBaiLianEmbeddingWithDashScopeNativePayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/services/embeddings/text-embedding/text-embedding", exchange -> {
            requestBody.set(readBody(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"output\":{\"embeddings\":[{\"embedding\":[0.1,0.2,0.3],"
                    + "\"text_index\":0}]},\"usage\":{\"total_tokens\":4},\"request_id\":\"test\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        ModelService service = new ModelService(
                mock(MyBatisStore.class),
                new JsonMaps(objectMapper),
                objectMapper,
                mock(AuthService.class),
                mock(RagClient.class));
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/v1/services/embeddings/text-embedding/text-embedding#";

        Map<String, Object> result = service.check(new ModelDtos.CreateRequest(
                "BaiLian",
                "text-embedding-v4",
                endpoint,
                "sk-test",
                "",
                "",
                "embedding",
                Map.of()));

        assertThat(result.get("error")).isEqualTo("");
        assertThat(result.get("content")).isEqualTo("dim is : 3");
        assertThat(authorization.get()).isEqualTo("Bearer sk-test");

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.get("model").asText()).isEqualTo("text-embedding-v4");
        assertThat(body.at("/input/texts/0").asText()).contains("模型连通性测试");
        assertThat(body.at("/parameters/text_type").asText()).isEqualTo("document");
        assertThat(body.at("/parameters/encoding_format").asText()).isEqualTo("float");
    }

    @Test
    void checksChatModelWithoutTokenParameterWhenGatewayRejectsBothNames() throws Exception {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = new ObjectMapper().readTree(readBody(exchange));
            requests.add(request);
            boolean unsupported = request.has("max_tokens") || request.has("max_completion_tokens");
            byte[] response = (unsupported
                    ? "{\"error\":{\"message\":\"Upstream request failed\"}}"
                    : "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(unsupported ? 400 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        ModelService service = new ModelService(
                mock(MyBatisStore.class),
                new JsonMaps(objectMapper),
                objectMapper,
                mock(AuthService.class),
                mock(RagClient.class));

        Map<String, Object> result = service.check(new ModelDtos.CreateRequest(
                "Other",
                "gpt-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "sk-test",
                "",
                "",
                "chat",
                Map.of()));

        assertThat(result.get("error")).isEqualTo("");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).has("max_tokens")).isTrue();
        assertThat(requests.get(1).has("max_completion_tokens")).isTrue();
        assertThat(requests.get(2).has("max_tokens")).isFalse();
        assertThat(requests.get(2).has("max_completion_tokens")).isFalse();
    }

    private static String readBody(HttpExchange exchange) throws java.io.IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
}
