package com.chaitin.niuniuwiki.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 验证规划护栏对典型问题复杂度的稳定分类。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
class AdaptiveQueryPlannerTest {

    private final AdaptiveQueryPlanner planner = new AdaptiveQueryPlanner(null, new ObjectMapper());

    @Test
    void classifiesSingleFactQuestion() {
        assertThat(planner.guardedPlan("这个项目叫什么？").mode()).isEqualTo(RetrievalMode.SINGLE);
    }

    @Test
    void classifiesComparisonAsParallelQueries() {
        AgenticRagModels.RetrievalPlan plan = planner.guardedPlan("MySQL 和 PostgreSQL 有什么区别？");

        assertThat(plan.mode()).isEqualTo(RetrievalMode.PARALLEL);
        assertThat(plan.seedQueries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void classifiesDependencyImpactAsMultiHop() {
        assertThat(planner.guardedPlan("这个架构会影响哪些下游模块？").mode())
                .isEqualTo(RetrievalMode.MULTI_HOP);
    }

    @Test
    void asksForClarificationWhenQuestionIsVague() {
        AgenticRagModels.RetrievalPlan plan = planner.guardedPlan("它怎么样？");

        assertThat(plan.mode()).isEqualTo(RetrievalMode.CLARIFY);
        assertThat(plan.clarificationQuestion()).contains("成本", "性能");
    }
}
