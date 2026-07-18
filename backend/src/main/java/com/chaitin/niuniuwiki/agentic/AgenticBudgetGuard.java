 package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalBudget;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.UsageSnapshot;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import java.time.Duration;
import java.time.Instant;

/**
 * 在一次 Agent 运行中统一执行检索次数、Token 与墙钟时间三重预算。
 *
 * @author 程序员牛肉
 * @since 2026-04-27
 */
final class AgenticBudgetGuard {

    private final RetrievalBudget budget;
    private final CancellationSignal cancellationSignal;
    private final Instant startedAt;
    private int retrievals;
    private int iterations;
    private int tokens;
    private int evidenceCount;
    private String stopReason = "";

    AgenticBudgetGuard(
            RetrievalBudget budget,
            CancellationSignal cancellationSignal,
            int retrievals,
            int iterations,
            int tokens,
            Instant startedAt
    ) {
        this.budget = budget;
        this.cancellationSignal = cancellationSignal;
        this.retrievals = Math.max(0, retrievals);
        this.iterations = Math.max(0, iterations);
        this.tokens = Math.max(0, tokens);
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    synchronized boolean reserveRetrieval() {
        checkActive();
        if (retrievals >= budget.maxRetrievals()) {
            stopReason = "RETRIEVAL_BUDGET";
            return false;
        }
        retrievals++;
        return true;
    }

    synchronized boolean beginIteration() {
        checkActive();
        if (iterations >= budget.maxIterations()) {
            stopReason = "ITERATION_BUDGET";
            return false;
        }
        iterations++;
        return true;
    }

    synchronized void consumeTokens(int count) {
        tokens += Math.max(0, count);
        if (tokens >= budget.maxTokens()) {
            stopReason = "TOKEN_BUDGET";
        }
    }

    synchronized boolean canContinue() {
        checkActive();
        return retrievals < budget.maxRetrievals()
                && iterations < budget.maxIterations()
                && tokens < budget.maxTokens();
    }

    synchronized int remainingTokens() {
        return Math.max(0, budget.maxTokens() - tokens);
    }

    synchronized long remainingMillis() {
        return Math.max(1L, budget.maxDurationMs() - elapsedMillis());
    }

    synchronized void evidenceCount(int count) {
        evidenceCount = Math.max(0, count);
    }

    synchronized void stop(String reason) {
        if (stopReason.isBlank()) {
            stopReason = reason == null ? "" : reason;
        }
    }

    synchronized UsageSnapshot snapshot() {
        return new UsageSnapshot(retrievals, iterations, tokens, elapsedMillis(), evidenceCount, stopReason);
    }

    private void checkActive() {
        cancellationSignal.check();
        if (elapsedMillis() >= budget.maxDurationMs()) {
            stopReason = "TIME_BUDGET";
            throw new AgenticBudgetExceededException("Agent 已达到时间预算");
        }
    }

    private long elapsedMillis() {
        return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
    }
}
