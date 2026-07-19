package com.chaitin.niuniuwiki.persistence;

import com.chaitin.niuniuwiki.common.JsonMaps;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * 为遗留 JSON 协议提供 JDBC 行适配；复杂领域应在其上继续封装类型化 Repository。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Component
public class JdbcMaps {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMaps jsonMaps;

    public JdbcMaps(JdbcTemplate jdbcTemplate, JsonMaps jsonMaps) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMaps = jsonMaps;
    }

    public List<Map<String, Object>> queryForList(String statement, Object... arguments) {
        return query(statement, rowMapper(), arguments);
    }

    public Map<String, Object> queryForMap(String statement, Object... arguments) {
        return jdbcTemplate.queryForObject(statement, rowMapper(), arguments);
    }

    public <T> T queryForObject(String statement, Class<T> targetType, Object... arguments) {
        return jdbcTemplate.queryForObject(statement, targetType, arguments);
    }

    public <T> T queryForObject(String statement, RowMapper<T> rowMapper, Object... arguments) {
        return jdbcTemplate.queryForObject(statement, rowMapper, arguments);
    }

    public <T> List<T> query(String statement, RowMapper<T> rowMapper, Object... arguments) {
        return jdbcTemplate.query(statement, rowMapper, arguments);
    }

    public int update(String statement, Object... arguments) {
        return jdbcTemplate.update(statement, arguments);
    }

    public RowMapper<Map<String, Object>> rowMapper() {
        return (resultSet, rowNumber) -> jsonMaps.normalizeRow(row(resultSet));
    }

    private static Map<String, Object> row(java.sql.ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
        }
        return row;
    }
}
