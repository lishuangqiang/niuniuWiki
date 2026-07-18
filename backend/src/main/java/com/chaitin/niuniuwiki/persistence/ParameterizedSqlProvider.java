package com.chaitin.niuniuwiki.persistence;

import java.util.List;
import java.util.Map;

/**
 * 将服务层使用的 JDBC 占位符转换为 MyBatis 参数绑定表达式。
 *
 * <p>SQL 文本只允许来自后端内部固定语句；业务值始终通过 MyBatis 绑定，
 * 不会拼接进 SQL，从而保留预编译参数的安全边界。</p>
 *
 * @author 程序员牛肉
 * @since 2026-07-08
 */
public final class ParameterizedSqlProvider {

    private ParameterizedSqlProvider() {
    }

    public static String provide(Map<String, Object> parameters) {
        String statement = String.valueOf(parameters.getOrDefault("statement", ""));
        @SuppressWarnings("unchecked")
        List<Object> arguments = (List<Object>) parameters.getOrDefault("arguments", List.of());
        StringBuilder bound = new StringBuilder(statement.length() + arguments.size() * 16);
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int argumentIndex = 0;

        for (int index = 0; index < statement.length(); index++) {
            char current = statement.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                if (singleQuoted && index + 1 < statement.length() && statement.charAt(index + 1) == '\'') {
                    bound.append("''");
                    index++;
                    continue;
                }
                singleQuoted = !singleQuoted;
                bound.append(current);
                continue;
            }
            if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                bound.append(current);
                continue;
            }
            if (current == '?' && !singleQuoted && !doubleQuoted) {
                if (argumentIndex >= arguments.size()) {
                    throw new IllegalArgumentException("SQL placeholder count exceeds argument count");
                }
                bound.append("#{arguments[").append(argumentIndex++).append("]}");
            } else {
                bound.append(current);
            }
        }
        if (singleQuoted || doubleQuoted) {
            throw new IllegalArgumentException("SQL contains an unterminated quoted value");
        }
        if (argumentIndex != arguments.size()) {
            throw new IllegalArgumentException("SQL argument count exceeds placeholder count");
        }
        return bound.toString();
    }
}
