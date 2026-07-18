package com.chaitin.niuniuwiki.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

/**
 * 负责数据库 JSONB、数组和普通 Map 之间的安全转换。
 *
 * @author 程序员牛肉
 * @since 2026-07-11
 */
@Component
public class JsonMaps {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> JSON_COLUMNS = Set.of(
            "access_settings",
            "auth_setting",
            "after_snapshot",
            "before_snapshot",
            "budget",
            "conversation_distribution",
            "conversation_info",
            "claim_a",
            "claim_b",
            "details",
            "entities",
            "facts",
            "feedback_info",
            "geo_count",
            "hot_browser",
            "hot_os",
            "hot_page",
            "hot_referer_host",
            "info",
            "initialize_req",
            "initialize_resp",
            "input",
            "meta",
            "metadata",
            "metrics",
            "output",
            "parameters",
            "plan",
            "payload",
            "permissions",
            "report",
            "rag_info",
            "settings",
            "source_permissions",
            "stats",
            "tool_call_req",
            "usage",
            "user_info",
            "validation_report",
            "value");

    private final ObjectMapper objectMapper;

    public JsonMaps(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ApiException("invalid json value: " + exception.getOriginalMessage());
        }
    }

    public Map<String, Object> jsonMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), item));
            return copy;
        }
        String json = value instanceof PGobject object ? object.getValue() : String.valueOf(value);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed.isTextual()) {
                parsed = objectMapper.readTree(parsed.asText());
            }
            if (!parsed.isObject()) {
                throw new ApiException("database JSON value is not an object");
            }
            return objectMapper.convertValue(parsed, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new ApiException("invalid json stored in database");
        }
    }

    public Map<String, Object> normalizeRow(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            String normalizedName = name.toLowerCase(Locale.ROOT);
            Object normalizedValue = normalize(value);
            if (normalizedValue != null
                    && JSON_COLUMNS.contains(normalizedName)
                    && !(normalizedValue instanceof Map<?, ?>)) {
                normalizedValue = jsonMap(normalizedValue);
            }
            row.put(normalizedName, normalizedValue);
        });
        return row;
    }

    private Object normalize(Object value) {
        if (value instanceof PGobject object && ("jsonb".equals(object.getType()) || "json".equals(object.getType()))) {
            return jsonMap(object);
        }
        if (value instanceof Array array) {
            try {
                Object raw = array.getArray();
                if (raw instanceof Object[] values) {
                    return new ArrayList<>(List.of(values));
                }
                return raw;
            } catch (SQLException exception) {
                throw new ApiException("invalid SQL array value");
            }
        }
        if (value instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        return value;
    }
}
