package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.agentic.AdaptiveQueryPlanner.PlanningResult;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEvent;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEventSink;
import com.chaitin.niuniuwiki.retrieval.Evidence;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Reflection;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalBudget;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalMode;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalPlan;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RunRequest;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RunResult;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.UsageSnapshot;
import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

/**
 * 执行可收敛的 Adaptive Agentic RAG 循环，覆盖规划、并发检索、证据审查、多跳追问和状态恢复。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
@Service
public class AgenticRagService {

    private final AdaptiveQueryPlanner planner;
    private final AgenticKnowledgeRetriever retriever;
    private final EvidenceSufficiencyEvaluator evaluator;
    private final AgenticRagStore store;
    private final ExecutorService retrievalExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore concurrentRetrievals = new Semaphore(24, true);

    public AgenticRagService(
            AdaptiveQueryPlanner planner,
            AgenticKnowledgeRetriever retriever,
            EvidenceSufficiencyEvaluator evaluator,
            AgenticRagStore store
    ) {
        this.planner = planner;
        this.retriever = retriever;
        this.evaluator = evaluator;
        this.store = store;
    }

    public RunResult execute(
            RunRequest request,
            AgentEventSink sink,
            CancellationSignal cancellationSignal
    ) {
        store.createRun(request);
        try {
            store.heartbeat(request.runId());
            emit(sink, AgentEvent.of(request.runId(), "understand", "RUNNING",
                    "正在理解问题并选择检索策略", 0, null, List.of(), Map.of()), Map.of(), Map.of());
            PlanningResult planning = planner.plan(request.question(), request.history(), cancellationSignal);
            RetrievalPlan plan = normalizePlan(planning.plan(), request.question());
            RetrievalBudget budget = RetrievalBudget.forMode(plan.mode());
            AgenticBudgetGuard guard = new AgenticBudgetGuard(
                    budget, cancellationSignal, 0, 0, planning.tokenUsage().totalTokens(), Instant.now());
            store.savePlan(request.runId(), plan, budget, guard.snapshot());
            emit(sink, AgentEvent.of(request.runId(), "plan", "COMPLETED",
                    planMessage(plan), 0, plan.mode(), plan.seedQueries(),
                    Map.of("max_retrievals", budget.maxRetrievals(),
                            "max_iterations", budget.maxIterations(),
                            "max_tokens", budget.maxTokens(),
                            "max_duration_ms", budget.maxDurationMs())),
                    Map.of("question", request.question()), Map.of("plan", plan));

            if (plan.mode() == RetrievalMode.CLARIFY) {
                guard.stop("NEEDS_CLARIFICATION");
                store.markGenerating(request.runId(), guard.snapshot(), false, 0d);
                emit(sink, AgentEvent.of(request.runId(), "clarify", "COMPLETED",
                        plan.clarificationQuestion(), 0, plan.mode(), List.of(), Map.of()), Map.of(), Map.of());
                return new RunResult(request.runId(), plan, budget, guard.snapshot(), List.of(),
                        plan.clarificationQuestion(), guard.remainingTokens());
            }
            if (plan.mode() == RetrievalMode.NONE) {
                guard.stop("RETRIEVAL_NOT_REQUIRED");
                store.markGenerating(request.runId(), guard.snapshot(), true, 1d);
                emit(sink, AgentEvent.of(request.runId(), "retrieve", "SKIPPED",
                        "该问题无需检索知识库，直接生成回答", 0, plan.mode(), List.of(), Map.of()),
                        Map.of(), Map.of());
                return new RunResult(request.runId(), plan, budget, guard.snapshot(), List.of(), "",
                        guard.remainingTokens());
            }
            return executeLoop(request, plan, budget, guard, new EvidenceAccumulator(),
                    plan.seedQueries(), new LinkedHashSet<>(), sink, cancellationSignal);
        } catch (CancellationException exception) {
            store.cancel(request.runId(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            store.fail(request.runId(), safeError(exception));
            throw exception;
        }
    }

    public RunResult resume(
            String runId,
            String kbId,
            KnowledgeAccessScope accessScope,
            AgentEventSink sink,
            CancellationSignal cancellationSignal
    ) {
        Map<String, Object> run = store.run(runId);
        if (!kbId.equals(value(run.get("kb_id")))) {
            throw new ApiException("Agent 运行不属于当前知识库");
        }
        String status = value(run.get("status"));
        if ("COMPLETED".equals(status)) {
            throw new ApiException("Agent 运行已经完成");
        }
        if (!List.of("PAUSED", "FAILED", "CANCELLED", "RUNNING", "GENERATING").contains(status)) {
            throw new ApiException("当前 Agent 运行不可恢复");
        }
        if (!store.resume(runId)) {
            throw new ApiException("Agent 运行正在其他实例执行或租约尚未过期");
        }
        RetrievalPlan plan = plan(run);
        RetrievalBudget budget = budget(run, plan.mode());
        UsageSnapshot previousUsage = usage(run);
        AgenticBudgetGuard guard = new AgenticBudgetGuard(
                budget, cancellationSignal, previousUsage.retrievals(), previousUsage.iterations(),
                previousUsage.tokens(), instant(run.get("started_at")));
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        evidence.addAll(store.evidence(runId).stream().map(this::evidence).toList());
        guard.evidenceCount(evidence.size());
        emit(sink, AgentEvent.of(runId, "resume", "COMPLETED",
                "已恢复上次中断的检索状态", previousUsage.iterations(), plan.mode(), List.of(),
                Map.of("retrievals", previousUsage.retrievals(), "evidence_count", evidence.size())),
                Map.of(), Map.of());
        if (plan.mode() == RetrievalMode.CLARIFY) {
            guard.stop("NEEDS_CLARIFICATION");
            store.markGenerating(runId, guard.snapshot(), false, 0d);
            return new RunResult(runId, plan, budget, guard.snapshot(), List.of(),
                    plan.clarificationQuestion(), guard.remainingTokens());
        }
        if (plan.mode() == RetrievalMode.NONE) {
            guard.stop("RETRIEVAL_NOT_REQUIRED");
            store.markGenerating(runId, guard.snapshot(), true, 1d);
            return new RunResult(runId, plan, budget, guard.snapshot(), List.of(), "", guard.remainingTokens());
        }
        RunRequest request = new RunRequest(runId, kbId, value(run.get("conversation_id")),
                value(run.get("user_message_id")), value(run.get("question")), List.of(), accessScope);
        List<String> nextQueries = new ArrayList<>(plan.seedQueries());
        if (previousUsage.iterations() > 0) {
            nextQueries.add(request.question() + " 尚未覆盖的关系、边界条件与下游影响");
        }
        try {
            return executeLoop(request, plan, budget, guard, evidence, nextQueries,
                    new LinkedHashSet<>(), sink, cancellationSignal);
        } catch (CancellationException exception) {
            store.cancel(runId, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            store.fail(runId, safeError(exception));
            throw exception;
        }
    }

    private RunResult executeLoop(
            RunRequest request,
            RetrievalPlan plan,
            RetrievalBudget budget,
            AgenticBudgetGuard guard,
            EvidenceAccumulator evidence,
            List<String> initialQueries,
            Set<String> seenQueries,
            AgentEventSink sink,
            CancellationSignal cancellationSignal
    ) {
        List<String> queries = deduplicate(initialQueries, seenQueries);
        boolean sufficient = false;
        double confidence = 0d;
        int stagnantIterations = 0;
        try {
            while (!queries.isEmpty() && guard.beginIteration()) {
                store.heartbeat(request.runId());
                int iteration = guard.snapshot().iterations();
                List<String> scheduled = new ArrayList<>();
                for (String query : queries) {
                    if (scheduled.size() >= budget.maxParallelism() || !guard.reserveRetrieval()) {
                        break;
                    }
                    scheduled.add(query);
                    seenQueries.add(normalizeQuery(query));
                }
                if (scheduled.isEmpty()) {
                    break;
                }
                emit(sink, AgentEvent.of(request.runId(), "retrieve", "RUNNING",
                        retrievalStartMessage(plan.mode(), iteration, scheduled.size()), iteration,
                        plan.mode(), scheduled, Map.of("retrievals_used", guard.snapshot().retrievals(),
                                "retrievals_limit", budget.maxRetrievals())),
                        Map.of("queries", scheduled), Map.of());
                List<QueryFuture> futures = new ArrayList<>();
                for (String query : scheduled) {
                    Future<List<Evidence>> future = retrievalExecutor.submit(
                            () -> {
                                if (!concurrentRetrievals.tryAcquire(2, TimeUnit.SECONDS)) {
                                    throw new ApiException("检索服务繁忙，请稍后重试");
                                }
                                try {
                                    return retriever.retrieve(request.kbId(), query, iteration,
                                            request.accessScope(), cancellationSignal);
                                } finally {
                                    concurrentRetrievals.release();
                                }
                            });
                    futures.add(new QueryFuture(query, future));
                }
                int before = evidence.size();
                for (QueryFuture queryFuture : futures) {
                    List<Evidence> found = await(queryFuture.future(), guard, cancellationSignal, futures);
                    for (Evidence item : found) {
                        store.saveEvidence(request.runId(), item);
                    }
                    int added = evidence.addAll(found);
                    emit(sink, AgentEvent.of(request.runId(), "retrieve", "PROGRESS",
                            "查询“" + truncate(queryFuture.query(), 36) + "”返回 " + found.size()
                                    + " 条证据，新增 " + added + " 条", iteration, plan.mode(),
                            List.of(queryFuture.query()), Map.of("found", found.size(), "added", added)),
                            Map.of("query", queryFuture.query()), Map.of("found", found.size(), "added", added));
                }
                guard.evidenceCount(evidence.size());
                stagnantIterations = evidence.size() == before ? stagnantIterations + 1 : 0;
                List<Evidence> ranked = evidence.rankedEvidence();
                Reflection reflection = evaluator.evaluate(request.question(), plan.mode(), ranked,
                        iteration, cancellationSignal, guard.remainingTokens());
                guard.consumeTokens(reflection.tokenUsage().totalTokens());
                sufficient = reflection.sufficient();
                confidence = reflection.confidence();
                if (plan.mode() == RetrievalMode.MULTI_HOP && iteration < 2 && guard.canContinue()) {
                    sufficient = false;
                }
                if (sufficient) {
                    guard.stop("EVIDENCE_SUFFICIENT");
                } else if (stagnantIterations >= 2) {
                    guard.stop("NO_NEW_EVIDENCE");
                }
                Reflection persistedReflection = new Reflection(sufficient, confidence, reflection.reason(),
                        reflection.followUpQueries(), reflection.tokenUsage());
                store.checkpoint(request.runId(), iteration, guard.snapshot(), persistedReflection);
                emit(sink, AgentEvent.of(request.runId(), "reflect", sufficient ? "COMPLETED" : "CONTINUE",
                        reflectionMessage(persistedReflection), iteration, plan.mode(),
                        persistedReflection.followUpQueries(), Map.of(
                                "sufficient", sufficient,
                                "confidence", confidence,
                                "evidence_count", evidence.size(),
                                "tokens_used", guard.snapshot().tokens())),
                        Map.of("evidence_count", evidence.size()), Map.of(
                                "sufficient", sufficient,
                                "confidence", confidence,
                                "follow_up_queries", persistedReflection.followUpQueries()));
                if (sufficient || stagnantIterations >= 2 || !guard.canContinue()) {
                    break;
                }
                queries = deduplicate(persistedReflection.followUpQueries(), seenQueries);
                if (queries.isEmpty() && plan.mode() == RetrievalMode.MULTI_HOP) {
                    queries = deduplicate(List.of(request.question() + " 依赖关系和下游影响"), seenQueries);
                }
            }
        } catch (AgenticBudgetExceededException exception) {
            guard.stop("TIME_BUDGET");
        }
        if (!sufficient && guard.snapshot().stopReason().isBlank()) {
            guard.stop(evidence.size() > 0 ? "BEST_EFFORT" : "NO_EVIDENCE");
        }
        guard.evidenceCount(evidence.size());
        store.markGenerating(request.runId(), guard.snapshot(), sufficient, confidence);
        emit(sink, AgentEvent.of(request.runId(), "generate", "RUNNING",
                generationMessage(evidence.size(), sufficient), guard.snapshot().iterations(),
                plan.mode(), List.of(), Map.of("evidence_count", evidence.size(),
                        "sufficient", sufficient, "confidence", confidence)), Map.of(), Map.of());
        return new RunResult(request.runId(), plan, budget, guard.snapshot(),
                evidence.references(8), "", guard.remainingTokens());
    }

    public void complete(String runId, String answerMessageId, UsageSnapshot usage, int answerTokens) {
        UsageSnapshot completed = new UsageSnapshot(usage.retrievals(), usage.iterations(),
                usage.tokens() + Math.max(0, answerTokens), usage.elapsedMs(), usage.evidenceCount(), usage.stopReason());
        store.complete(runId, answerMessageId, completed, usage.stopReason());
    }

    public void cancel(String runId, String reason) {
        store.cancel(runId, reason);
    }

    public void fail(String runId, String message) {
        store.fail(runId, message);
    }

    public RunContext context(String runId, String kbId) {
        Map<String, Object> run = store.run(runId);
        if (!kbId.equals(value(run.get("kb_id")))) {
            throw new ApiException("Agent 运行不属于当前知识库");
        }
        return new RunContext(runId, kbId, value(run.get("conversation_id")),
                value(run.get("user_message_id")), value(run.get("question")), value(run.get("status")));
    }

    public List<Map<String, Object>> list(String kbId, int limit) {
        return store.list(kbId, limit);
    }

    public Map<String, Object> detail(String kbId, String runId) {
        Map<String, Object> run = new LinkedHashMap<>(store.run(runId));
        if (!kbId.equals(value(run.get("kb_id")))) {
            throw new ApiException("Agent 运行不属于当前知识库");
        }
        run.put("steps", store.steps(runId));
        run.put("evidence", store.evidence(runId));
        return run;
    }

    public List<Map<String, Object>> trace(String runId) {
        return store.steps(runId).stream().map(step -> Map.<String, Object>of(
                "stage", value(step.get("stage")),
                "status", value(step.get("status")),
                "message", value(step.get("message")),
                "iteration", number(step.get("iteration")),
                "metrics", store.jsonMap(step.get("metrics")))).toList();
    }

    private void emit(
            AgentEventSink sink,
            AgentEvent event,
            Map<String, Object> input,
            Map<String, Object> output
    ) {
        store.appendEvent(event, input, output);
        sink.emit(event);
    }

    private static List<Evidence> await(
            Future<List<Evidence>> future,
            AgenticBudgetGuard guard,
            CancellationSignal cancellationSignal,
            List<QueryFuture> allFutures
    ) {
        while (true) {
            cancellationSignal.check();
            try {
                return future.get(Math.min(250L, guard.remainingMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                if (guard.remainingMillis() <= 1L) {
                    allFutures.forEach(item -> item.future().cancel(true));
                    throw new AgenticBudgetExceededException("并行检索达到时间预算");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                allFutures.forEach(item -> item.future().cancel(true));
                throw new CancellationException("Agent 检索被中断");
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof CancellationException cancellationException) {
                    throw cancellationException;
                }
                return List.of();
            }
        }
    }

    private RetrievalPlan plan(Map<String, Object> run) {
        Map<String, Object> value = store.jsonMap(run.get("plan"));
        RetrievalMode mode = mode(value.get("mode"), run.get("mode"));
        return new RetrievalPlan(mode, value(value.get("rationale")),
                stringList(value.get("seedQueries"), value.get("seed_queries")),
                firstNonBlank(value.get("clarificationQuestion"), value.get("clarification_question"),
                        run.get("clarification_question")));
    }

    private RetrievalBudget budget(Map<String, Object> run, RetrievalMode mode) {
        Map<String, Object> value = store.jsonMap(run.get("budget"));
        RetrievalBudget fallback = RetrievalBudget.forMode(mode);
        return new RetrievalBudget(
                integer(value, "maxRetrievals", "max_retrievals", fallback.maxRetrievals()),
                integer(value, "maxIterations", "max_iterations", fallback.maxIterations()),
                integer(value, "maxTokens", "max_tokens", fallback.maxTokens()),
                longValue(value, "maxDurationMs", "max_duration_ms", fallback.maxDurationMs()),
                integer(value, "maxParallelism", "max_parallelism", fallback.maxParallelism()));
    }

    private UsageSnapshot usage(Map<String, Object> run) {
        Map<String, Object> value = store.jsonMap(run.get("usage"));
        return new UsageSnapshot(
                integer(value, "retrievals", "retrievals", 0),
                integer(value, "iterations", "iterations", number(run.get("current_iteration"))),
                integer(value, "tokens", "tokens", 0),
                longValue(value, "elapsedMs", "elapsed_ms", 0L),
                integer(value, "evidenceCount", "evidence_count", store.evidence(value(run.get("id"))).size()),
                firstNonBlank(value.get("stopReason"), value.get("stop_reason"), run.get("stop_reason")));
    }

    private Evidence evidence(Map<String, Object> value) {
        return new Evidence(value(value.get("evidence_key")), value(value.get("node_id")),
                value(value.get("document_id")), value(value.get("title")), value(value.get("summary")),
                value(value.get("content")), value(value.get("url")), value(value.get("emoji")),
                decimal(value.get("score")), value(value.get("query")), number(value.get("hop")),
                store.jsonMap(value.get("metadata")));
    }

    private static RetrievalPlan normalizePlan(RetrievalPlan plan, String question) {
        List<String> queries = plan.seedQueries() == null ? List.of() : plan.seedQueries().stream()
                .map(AgenticRagService::value).map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
        if (plan.mode() != RetrievalMode.NONE && plan.mode() != RetrievalMode.CLARIFY && queries.isEmpty()) {
            queries = List.of(question);
        }
        if (plan.mode() == RetrievalMode.PARALLEL && queries.size() == 1) {
            queries = List.of(queries.getFirst(), question + " 各比较对象的能力、限制与适用场景");
        }
        return new RetrievalPlan(plan.mode(), value(plan.rationale()), queries,
                value(plan.clarificationQuestion()));
    }

    private static List<String> deduplicate(List<String> queries, Set<String> seen) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (queries != null) {
            for (String query : queries) {
                String safe = value(query).strip();
                String normalized = normalizeQuery(safe);
                if (!safe.isBlank() && !seen.contains(normalized)) {
                    result.putIfAbsent(normalized, safe);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private static String normalizeQuery(String query) {
        return value(query).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s，。！？、]+", "");
    }

    private static String planMessage(RetrievalPlan plan) {
        return switch (plan.mode()) {
            case NONE -> "计划完成：无需检索";
            case SINGLE -> "计划完成：执行一次精准检索";
            case PARALLEL -> "计划完成：并行执行 " + plan.seedQueries().size() + " 条查询";
            case MULTI_HOP -> "计划完成：执行链式多跳检索，逐轮检查证据";
            case CLARIFY -> "计划完成：问题信息不足，需要先澄清";
        };
    }

    private static String retrievalStartMessage(RetrievalMode mode, int iteration, int count) {
        return (mode == RetrievalMode.MULTI_HOP ? "第 " + iteration + " 跳" : "第 " + iteration + " 轮")
                + "检索已开始，共 " + count + " 条查询";
    }

    private static String reflectionMessage(Reflection reflection) {
        int confidence = (int) Math.round(reflection.confidence() * 100d);
        return (reflection.sufficient() ? "证据已充分" : "证据仍需补充")
                + "（置信度 " + confidence + "%）：" + reflection.reason();
    }

    private static String generationMessage(int count, boolean sufficient) {
        if (count == 0) {
            return "未检索到知识库证据，将明确说明证据缺口";
        }
        return "已合并去重 " + count + " 条证据，" + (sufficient ? "开始生成可引用答案" : "按当前最佳证据生成回答");
    }

    private static RetrievalMode mode(Object preferred, Object fallback) {
        try {
            return RetrievalMode.valueOf(firstNonBlank(preferred, fallback).toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return RetrievalMode.SINGLE;
        }
    }

    private static List<String> stringList(Object... values) {
        for (Object value : values) {
            if (value instanceof List<?> list) {
                return list.stream().map(AgenticRagService::value).filter(item -> !item.isBlank()).toList();
            }
        }
        return List.of();
    }

    private static int integer(Map<String, Object> map, String camel, String snake, int fallback) {
        Object value = map.containsKey(camel) ? map.get(camel) : map.get(snake);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longValue(Map<String, Object> map, String camel, String snake, long fallback) {
        Object value = map.containsKey(camel) ? map.get(camel) : map.get(snake);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception exception) {
            return Instant.now();
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String safe = value(value);
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String safeError(Throwable exception) {
        String message = value(exception.getMessage());
        return message.substring(0, Math.min(1_000, message.length()));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @PreDestroy
    public void shutdown() {
        retrievalExecutor.shutdownNow();
    }

    private record QueryFuture(String query, Future<List<Evidence>> future) {
    }

    public record RunContext(
            String runId,
            String kbId,
            String conversationId,
            String userMessageId,
            String question,
            String status
    ) {
    }
}
