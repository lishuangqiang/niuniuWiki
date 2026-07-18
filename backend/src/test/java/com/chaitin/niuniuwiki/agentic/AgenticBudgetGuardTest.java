package com.chaitin.niuniuwiki.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalBudget;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 验证 Agent 三重预算中的检索次数与 Token 边界。
 *
 * @author 程序员牛肉
 * @since 2026-05-11
 */
class AgenticBudgetGuardTest {

    @Test
    void stopsSchedulingAfterRetrievalBudget() {
        AgenticBudgetGuard guard = new AgenticBudgetGuard(
                new RetrievalBudget(2, 2, 1_000, 60_000, 2),
                CancellationSignal.none(), 0, 0, 0, Instant.now());

        assertThat(guard.reserveRetrieval()).isTrue();
        assertThat(guard.reserveRetrieval()).isTrue();
        assertThat(guard.reserveRetrieval()).isFalse();
        assertThat(guard.snapshot().stopReason()).isEqualTo("RETRIEVAL_BUDGET");
    }

    @Test
    void stopsLoopAfterTokenBudget() {
        AgenticBudgetGuard guard = new AgenticBudgetGuard(
                new RetrievalBudget(5, 3, 100, 60_000, 2),
                CancellationSignal.none(), 0, 0, 0, Instant.now());

        guard.consumeTokens(100);

        assertThat(guard.canContinue()).isFalse();
        assertThat(guard.snapshot().stopReason()).isEqualTo("TOKEN_BUDGET");
    }
}
