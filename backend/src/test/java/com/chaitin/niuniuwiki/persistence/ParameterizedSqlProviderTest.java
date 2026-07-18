package com.chaitin.niuniuwiki.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 MyBatis 动态 SQL 的参数绑定边界。
 *
 * @author 程序员牛肉
 * @since 2026-07-12
 */
class ParameterizedSqlProviderTest {

    @Test
    void bindsOnlyPlaceholdersOutsideQuotedText() {
        String result = ParameterizedSqlProvider.provide(Map.of(
                "statement", "SELECT '?' AS marker FROM nodes WHERE id = ? AND meta = ?::jsonb",
                "arguments", List.of("node-id", "{}")));

        assertThat(result).isEqualTo(
                "SELECT '?' AS marker FROM nodes WHERE id = #{arguments[0]} AND meta = #{arguments[1]}::jsonb");
    }

    @Test
    void rejectsMismatchedArgumentCounts() {
        assertThatThrownBy(() -> ParameterizedSqlProvider.provide(Map.of(
                "statement", "SELECT * FROM nodes WHERE id = ?",
                "arguments", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
