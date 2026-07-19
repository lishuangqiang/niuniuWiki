package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import java.util.List;
import java.util.Map;

/**
 * 定义 Adaptive Agentic RAG 在规划、执行、证据评估和事件推送阶段使用的领域对象。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
public final class AgenticRagModels {

    private AgenticRagModels() {
    }

    public enum RetrievalMode {
        NONE,
        SINGLE,
        PARALLEL,
        MULTI_HOP,
        CLARIFY
    }

    public record RetrievalBudget(
            int maxRetrievals,
            int maxIterations,
            int maxTokens,
            long maxDurationMs,
            int maxParallelism
    ) {
        public static RetrievalBudget forMode(RetrievalMode mode) {
            return switch (mode) {
                case NONE -> new RetrievalBudget(0, 0, 8_000, 30_000, 1);
                case SINGLE -> new RetrievalBudget(1, 1, 14_000, 40_000, 1);
                case PARALLEL -> new RetrievalBudget(4, 2, 20_000, 55_000, 3);
                case MULTI_HOP -> new RetrievalBudget(7, 3, 28_000, 70_000, 3);
                case CLARIFY -> new RetrievalBudget(0, 0, 6_000, 20_000, 1);
            };
        }
    }

    public record RetrievalPlan(
            RetrievalMode mode,
            String rationale,
            List<String> seedQueries,
            String clarificationQuestion
    ) {
    }

    public record TokenUsage(int promptTokens, int completionTokens) {
        public int totalTokens() {
            return Math.max(0, promptTokens) + Math.max(0, completionTokens);
        }
    }

    public record UsageSnapshot(
            int retrievals,
            int iterations,
            int tokens,
            long elapsedMs,
            int evidenceCount,
            String stopReason
    ) {
    }

    public record Reflection(
            boolean sufficient,
            double confidence,
            String reason,
            List<String> followUpQueries,
            TokenUsage tokenUsage
    ) {
    }

    public record AgentEvent(
            String runId,
            String stage,
            String status,
            String message,
            int iteration,
            RetrievalMode mode,
            List<String> queries,
            Map<String, Object> metrics
    ) {
        public static AgentEvent of(
                String runId,
                String stage,
                String status,
                String message,
                int iteration,
                RetrievalMode mode,
                List<String> queries,
                Map<String, Object> metrics
        ) {
            return new AgentEvent(runId, stage, status, message, iteration, mode,
                    queries == null ? List.of() : List.copyOf(queries),
                    metrics == null ? Map.of() : Map.copyOf(metrics));
        }
    }

    @FunctionalInterface
    public interface AgentEventSink {
        void emit(AgentEvent event);

        static AgentEventSink noop() {
            return event -> { };
        }
    }

    public record RunRequest(
            String runId,
            String kbId,
            String conversationId,
            String userMessageId,
            String question,
            List<Map<String, Object>> history,
            KnowledgeAccessScope accessScope
    ) {
        public RunRequest {
            accessScope = accessScope == null ? KnowledgeAccessScope.publicAccess() : accessScope;
        }
    }

    public record RunResult(
            String runId,
            RetrievalPlan plan,
            RetrievalBudget budget,
            UsageSnapshot usage,
            List<Map<String, Object>> references,
            String directAnswer,
            int remainingTokens
    ) {
        public boolean needsClarification() {
            return plan.mode() == RetrievalMode.CLARIFY;
        }
    }
}
