package com.chaitin.niuniuwiki.security;

import java.util.List;

/**
 * 描述一次知识检索允许使用的身份与授权组，避免 HTTP 会话信息在检索链路中丢失。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
public record KnowledgeAccessScope(String subjectId, List<Integer> groupIds) {

    public KnowledgeAccessScope {
        subjectId = subjectId == null ? "" : subjectId;
        groupIds = groupIds == null ? List.of() : groupIds.stream().distinct().sorted().toList();
    }

    public static KnowledgeAccessScope publicAccess() {
        return new KnowledgeAccessScope("anonymous", List.of());
    }

    public String postgresGroupArray() {
        return "{" + groupIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "}";
    }
}
