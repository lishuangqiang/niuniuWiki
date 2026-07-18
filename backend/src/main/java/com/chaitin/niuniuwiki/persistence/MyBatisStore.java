package com.chaitin.niuniuwiki.persistence;

import com.chaitin.niuniuwiki.common.JsonMaps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;

/**
 * 为业务服务提供基于 MyBatis 的参数化查询和写入能力。
 *
 * <p>该适配层集中处理 PostgreSQL JSONB、数组和标量类型转换，业务服务不再
 * 依赖 Spring JDBC、{@code ResultSet} 或 JDBC 行映射器。</p>
 *
 * @author 程序员牛肉
 * @since 2026-07-10
 */
@Component
public class MyBatisStore {

    private final DatabaseMapper mapper;
    private final JsonMaps jsonMaps;

    public MyBatisStore(DatabaseMapper mapper, JsonMaps jsonMaps) {
        this.mapper = mapper;
        this.jsonMaps = jsonMaps;
    }

    public List<Map<String, Object>> queryForList(String statement, Object... arguments) {
        return rows(statement, arguments);
    }

    public Map<String, Object> queryForMap(String statement, Object... arguments) {
        return requiredSingle(rows(statement, arguments));
    }

    public <T> T queryForObject(String statement, Class<T> targetType, Object... arguments) {
        Map<String, Object> row = requiredSingle(rows(statement, arguments));
        Object value = row.values().stream().findFirst().orElse(null);
        return convert(value, targetType);
    }

    public <T> T queryForObject(String statement, RowMapper<T> rowMapper, Object... arguments) {
        List<T> results = query(statement, rowMapper, arguments);
        if (results.isEmpty()) {
            throw new EmptyResultDataAccessException(1);
        }
        if (results.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        }
        return results.getFirst();
    }

    public <T> List<T> query(String statement, RowMapper<T> rowMapper, Object... arguments) {
        List<Map<String, Object>> rows = rows(statement, arguments);
        List<T> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            result.add(rowMapper.map(new Row(rows.get(index)), index));
        }
        return result;
    }

    public int update(String statement, Object... arguments) {
        return mapper.mutate(statement, Arrays.asList(arguments));
    }

    public RowMapper<Map<String, Object>> rowMapper() {
        return (row, rowNumber) -> row.asMap();
    }

    private List<Map<String, Object>> rows(String statement, Object... arguments) {
        return mapper.select(statement, Arrays.asList(arguments)).stream()
                .map(jsonMaps::normalizeRow)
                .toList();
    }

    private static Map<String, Object> requiredSingle(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            throw new EmptyResultDataAccessException(1);
        }
        if (rows.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, rows.size());
        }
        return rows.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        if (targetType == String.class) {
            return (T) String.valueOf(value);
        }
        if (value instanceof Number number) {
            if (targetType == Integer.class) {
                return (T) Integer.valueOf(number.intValue());
            }
            if (targetType == Long.class) {
                return (T) Long.valueOf(number.longValue());
            }
            if (targetType == Double.class) {
                return (T) Double.valueOf(number.doubleValue());
            }
            if (targetType == Float.class) {
                return (T) Float.valueOf(number.floatValue());
            }
        }
        if (targetType == Boolean.class) {
            return (T) Boolean.valueOf(String.valueOf(value));
        }
        if (targetType == UUID.class) {
            return (T) UUID.fromString(String.valueOf(value));
        }
        throw new IllegalArgumentException(
                "Cannot convert database value " + value.getClass().getName() + " to " + targetType.getName());
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(Row row, int rowNumber);
    }

    /**
     * 为遗留的单列映射表达式提供与列顺序无关的轻量行视图。
     */
    public static final class Row {
        private final Map<String, Object> values;
        private final List<Object> orderedValues;

        private Row(Map<String, Object> values) {
            this.values = values;
            this.orderedValues = new ArrayList<>(values.values());
        }

        public Map<String, Object> asMap() {
            return values;
        }

        public Object getObject(int columnIndex) {
            return value(columnIndex);
        }

        public String getString(int columnIndex) {
            Object value = value(columnIndex);
            return value == null ? null : String.valueOf(value);
        }

        public int getInt(int columnIndex) {
            Object value = value(columnIndex);
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        }

        public long getLong(int columnIndex) {
            Object value = value(columnIndex);
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        }

        public double getDouble(int columnIndex) {
            Object value = value(columnIndex);
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        }

        private Object value(int columnIndex) {
            if (columnIndex < 1 || columnIndex > orderedValues.size()) {
                throw new IndexOutOfBoundsException("Database column index out of range: " + columnIndex);
            }
            return orderedValues.get(columnIndex - 1);
        }
    }
}
