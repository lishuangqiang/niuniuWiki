package com.chaitin.niuniuwiki.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 RAGLite 请求载荷与检索过滤语义。
 *
 * @author 程序员牛肉
 * @since 2026-06-26
 */
class RagClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void omitsEmptyGroupFilterFromRetrieval() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/search", exchange -> {
            requestBody.set(readBody(exchange));
            byte[] response = "{\"success\":true,\"data\":{\"results\":[]}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        NiuniuWikiProperties properties = new NiuniuWikiProperties();
        properties.getRag().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        ObjectMapper objectMapper = new ObjectMapper();
        RagClient client = new RagClient(properties, objectMapper);

        client.retrieve("dataset", "Agent", List.of(), List.of());

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.has("metadata")).isFalse();
        assertThat(body.get("dataset_id").asText()).isEqualTo("dataset");
    }

    private static String readBody(HttpExchange exchange) throws java.io.IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
}
