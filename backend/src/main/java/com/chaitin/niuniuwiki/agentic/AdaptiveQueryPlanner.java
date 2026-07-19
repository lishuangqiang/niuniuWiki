package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalMode;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalPlan;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.TokenUsage;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 结合规则护栏和大模型结构化判断，为问题选择无需检索、单检索、并行、多跳或澄清模式。
 *
 * @author 程序员牛肉
 * @since 2026-06-12
 */
@Component
public class AdaptiveQueryPlanner {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ModelGateway modelClient;
    private final ObjectMapper objectMapper;

    public AdaptiveQueryPlanner(ModelGateway modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public PlanningResult plan(
            String question,
            List<Map<String, Object>> history,
            CancellationSignal cancellationSignal
    ) {
        RetrievalPlan guarded = guardedPlan(question);
        if (guarded.mode() == RetrievalMode.NONE || guarded.mode() == RetrievalMode.CLARIFY) {
            return new PlanningResult(guarded, new TokenUsage(0, 0));
        }
        try {
            ModelGateway.Completion completion = modelClient.complete(
                    systemPrompt(), planningInput(question, history), cancellationSignal, 650, Duration.ofSeconds(18));
            RetrievalPlan modelPlan = parse(completion.content(), question);
            return new PlanningResult(modelPlan,
                    new TokenUsage(completion.promptTokens(), completion.completionTokens()));
        } catch (RuntimeException exception) {
            return new PlanningResult(guarded, new TokenUsage(0, 0));
        }
    }

    RetrievalPlan guardedPlan(String question) {
        String safe = value(question).strip();
        String normalized = safe.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？,.!?]", "");
        if (normalized.matches("(你好|您好|hi|hello|谢谢|感谢|再见|在吗)+")) {
            return new RetrievalPlan(RetrievalMode.NONE, "属于寒暄或无需知识库证据的问题", List.of(), "");
        }
        if (looksVague(normalized)) {
            return new RetrievalPlan(RetrievalMode.CLARIFY, "指代或评价维度不明确", List.of(),
                    "你希望重点了解哪一方面？可以选择成本、性能、可靠性、使用体验，或补充你所指的具体对象。");
        }
        if (containsAny(normalized, "下游", "影响哪些", "依赖链", "调用链", "根因", "为什么导致", "传播路径", "追溯")) {
            return new RetrievalPlan(RetrievalMode.MULTI_HOP, "需要沿依赖或因果关系继续发现证据",
                    deduplicate(List.of(safe, safe + " 直接依赖和下游模块", safe + " 影响路径与约束")), "");
        }
        if (containsAny(normalized, "区别", "对比", "比较", "异同", "vs", "versus", "优劣")) {
            return new RetrievalPlan(RetrievalMode.PARALLEL, "问题包含多个比较对象，需要分别取证后合并",
                    comparisonQueries(safe), "");
        }
        return new RetrievalPlan(RetrievalMode.SINGLE, "单一事实或主题可由一次检索回答", List.of(safe), "");
    }

    private RetrievalPlan parse(String response, String originalQuestion) {
        String json = extractJson(response);
        try {
            Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE);
            RetrievalMode mode = RetrievalMode.valueOf(value(root.get("mode")).strip().toUpperCase(Locale.ROOT));
            String rationale = value(root.get("rationale")).strip();
            String clarification = value(root.get("clarification_question")).strip();
            List<String> queries = stringList(root.get("seed_queries"));
            if (mode == RetrievalMode.NONE || mode == RetrievalMode.CLARIFY) {
                queries = List.of();
            } else if (queries.isEmpty()) {
                queries = List.of(originalQuestion);
            }
            if (mode == RetrievalMode.CLARIFY && clarification.isBlank()) {
                clarification = "请补充你所指的对象，以及更关注成本、性能、可靠性还是使用体验。";
            }
            return new RetrievalPlan(mode,
                    rationale.isBlank() ? "模型根据问题结构选择检索策略" : rationale,
                    queries.stream().limit(4).toList(), clarification);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析检索计划", exception);
        }
    }

    private static String systemPrompt() {
        return """
                你是知识库检索规划器。只输出 JSON，不输出思维过程或 Markdown。
                mode 只能是 NONE、SINGLE、PARALLEL、MULTI_HOP、CLARIFY。
                - NONE：寒暄、纯创作或无需知识库证据。
                - SINGLE：单一实体或单一事实查询。
                - PARALLEL：比较、归纳多个并列对象，生成 2-4 个可独立并发的查询。
                - MULTI_HOP：依赖、因果、影响范围、上下游、跨文档推导；先给 1-3 个种子查询。
                - CLARIFY：指代对象或评价维度缺失，无法形成可靠查询。
                rationale 必须是可展示给用户的一句短说明，不能包含隐式思维过程。
                返回结构：
                {"mode":"SINGLE","rationale":"...","seed_queries":["..."],"clarification_question":""}
                """;
    }

    private static String planningInput(String question, List<Map<String, Object>> history) {
        StringBuilder result = new StringBuilder("当前问题：").append(value(question));
        if (history != null && !history.isEmpty()) {
            result.append("\n最近对话：");
            int start = Math.max(0, history.size() - 6);
            for (Map<String, Object> message : history.subList(start, history.size())) {
                result.append("\n").append(value(message.get("role"))).append("：")
                        .append(truncate(value(message.get("content")), 800));
            }
        }
        return result.toString();
    }

    private static boolean looksVague(String normalized) {
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.length() <= 14 && (normalized.matches("(它|这个|那个|这东西|该方案|该项目)?(怎么样|如何|好吗|行不行|有什么问题)")
                || normalized.matches("(说说|介绍一下|评价一下)(它|这个|那个)?"));
    }

    private static List<String> comparisonQueries(String question) {
        Set<String> result = new LinkedHashSet<>();
        result.add(question);
        String[] parts = question.split("(?:和|与|及|以及|vs\\.?|VS\\.?|对比|比较)");
        for (String part : parts) {
            String safe = part.replaceAll("(有什么)?(区别|异同|优劣).*$", "").strip();
            if (safe.length() >= 2) {
                result.add(safe + " 的定义、能力、限制与适用场景");
            }
        }
        return result.stream().limit(4).toList();
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String query = value(item).strip();
            if (!query.isBlank() && !result.contains(query)) {
                result.add(query);
            }
        }
        return result;
    }

    private static List<String> deduplicate(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String extractJson(String response) {
        String safe = value(response);
        int start = safe.indexOf('{');
        int end = safe.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("规划模型未返回 JSON");
        }
        return safe.substring(start, end + 1);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record PlanningResult(RetrievalPlan plan, TokenUsage tokenUsage) {
    }
}
