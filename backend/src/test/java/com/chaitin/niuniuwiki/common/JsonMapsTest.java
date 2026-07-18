package com.chaitin.niuniuwiki.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 Agent 运行记录中的 JSONB 字段会被转换为结构化对象。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
class JsonMapsTest {

    @Test
    void normalizesAgenticJsonColumnsReturnedAsStrings() {
        JsonMaps jsonMaps = new JsonMaps(new ObjectMapper());

        Map<String, Object> row = jsonMaps.normalizeRow(Map.of(
                "usage", "{\"retrievals\":6,\"evidence_count\":1}",
                "plan", "{\"mode\":\"MULTI_HOP\"}",
                "metrics", "{\"confidence\":0.7}"));

        assertThat(row.get("usage")).isEqualTo(Map.of("retrievals", 6, "evidence_count", 1));
        assertThat(row.get("plan")).isEqualTo(Map.of("mode", "MULTI_HOP"));
        assertThat(row.get("metrics")).isEqualTo(Map.of("confidence", 0.7));
    }
}
