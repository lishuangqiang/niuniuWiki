package com.chaitin.niuniuwiki.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsNiuniuWikiEnvelopeContract() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(ApiResponse.ok(Map.of("id", "42"))));

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("data").get("id").asText()).isEqualTo("42");
    }

    @Test
    void omitsNullDataOnError() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(ApiResponse.error("Not Found", 40004)));

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("Not Found");
        assertThat(json.has("data")).isFalse();
    }
}
