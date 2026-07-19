package com.chaitin.niuniuwiki.retrieval;

import java.util.Map;

/**
 * 表示与具体 Agent 编排无关的检索证据，是编译索引和问答运行时之间的稳定契约。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
public record Evidence(
        String evidenceKey,
        String nodeId,
        String documentId,
        String title,
        String summary,
        String content,
        String url,
        String emoji,
        double score,
        String query,
        int hop,
        Map<String, Object> metadata
) {
}
