package com.chaitin.niuniuwiki.model;

import com.chaitin.niuniuwiki.common.CancellationSignal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 隔离业务编排与具体模型供应商协议。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
public interface ModelGateway {

    Completion complete(
            String systemPrompt,
            String userPrompt,
            CancellationSignal cancellationSignal,
            int maxTokens,
            Duration timeout
    );

    Completion complete(
            List<Map<String, Object>> messages,
            CancellationSignal cancellationSignal,
            int maxTokens,
            Duration timeout
    );

    default String completeText(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, CancellationSignal.none(), 8_000, Duration.ofMinutes(2)).content();
    }

    record Completion(
            String content,
            int promptTokens,
            int completionTokens,
            String provider,
            String model
    ) {
        public int totalTokens() {
            return Math.max(0, promptTokens) + Math.max(0, completionTokens);
        }
    }
}
