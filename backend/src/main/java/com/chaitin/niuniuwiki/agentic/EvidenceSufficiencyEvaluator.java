package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.retrieval.Evidence;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.Reflection;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RetrievalMode;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.TokenUsage;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 对每轮检索结果做证据充分度判断，并为下一轮多跳检索生成受预算约束的查询。
 *
 * @author 程序员牛肉
 * @since 2026-04-14
 */
@Component
public class EvidenceSufficiencyEvaluator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ModelGateway modelClient;
    private final ObjectMapper objectMapper;

    public EvidenceSufficiencyEvaluator(ModelGateway modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public Reflection evaluate(
            String question,
            RetrievalMode mode,
            List<Evidence> evidence,
            int iteration,
            CancellationSignal cancellationSignal,
            int remainingTokens
    ) {
        if (mode == RetrievalMode.SINGLE) {
            boolean sufficient = !evidence.isEmpty();
            return new Reflection(sufficient, sufficient ? 0.76d : 0.1d,
                    sufficient ? "已找到可支撑该事实问题的直接文档" : "尚未找到直接证据",
                    List.of(), new TokenUsage(0, 0));
        }
        if (evidence.isEmpty()) {
            return new Reflection(false, 0.05d, "本轮没有返回可用证据",
                    List.of(question + " 相关定义与上下文"), new TokenUsage(0, 0));
        }
        if (remainingTokens < 256) {
            return fallback(question, mode, evidence, iteration);
        }
        try {
            ModelGateway.Completion completion = modelClient.complete(
                    evaluatorPrompt(), evidenceInput(question, mode, evidence, iteration), cancellationSignal,
                    Math.min(700, Math.max(256, remainingTokens / 5)), Duration.ofSeconds(20));
            Map<String, Object> root = objectMapper.readValue(extractJson(completion.content()), MAP_TYPE);
            boolean sufficient = Boolean.TRUE.equals(root.get("sufficient"));
            double confidence = number(root.get("confidence"), sufficient ? 0.75d : 0.45d);
            String reason = value(root.get("reason")).strip();
            List<String> followUps = stringList(root.get("follow_up_queries")).stream().limit(3).toList();
            return new Reflection(sufficient, Math.max(0d, Math.min(1d, confidence)),
                    reason.isBlank() ? "已完成证据覆盖度检查" : reason, followUps,
                    new TokenUsage(completion.promptTokens(), completion.completionTokens()));
        } catch (RuntimeException exception) {
            return fallback(question, mode, evidence, iteration);
        } catch (Exception exception) {
            return fallback(question, mode, evidence, iteration);
        }
    }

    private static Reflection fallback(
            String question,
            RetrievalMode mode,
            List<Evidence> evidence,
            int iteration
    ) {
        long distinctNodes = evidence.stream().map(Evidence::nodeId).filter(value -> !value.isBlank()).distinct().count();
        boolean sufficient = mode == RetrievalMode.PARALLEL
                ? distinctNodes >= 2
                : distinctNodes >= 2 && iteration >= 2;
        List<String> followUps = sufficient ? List.of() : List.of(
                question + " 关键依赖与直接依据",
                question + " 下游影响、边界条件与例外");
        return new Reflection(sufficient, sufficient ? 0.72d : 0.4d,
                sufficient ? "多个独立文档已覆盖主要问题维度" : "证据来源或关系链仍不完整",
                followUps, new TokenUsage(0, 0));
    }

    private static String evaluatorPrompt() {
        return """
                你是 RAG 证据审查器。只输出 JSON，不输出思维过程。
                判断现有证据是否足以回答用户原问题；要求结论可由证据直接支持，比较问题应覆盖各对象，
                多跳问题应覆盖起点、关系和下游结果。若不充分，生成最多 3 条补充检索查询，查询不得重复已有问题。
                reason 只写一句可展示的审查结论，不展开隐式推理。
                返回：{"sufficient":false,"confidence":0.4,"reason":"...","follow_up_queries":["..."]}
                """;
    }

    private static String evidenceInput(
            String question,
            RetrievalMode mode,
            List<Evidence> evidence,
            int iteration
    ) {
        StringBuilder result = new StringBuilder()
                .append("问题：").append(question)
                .append("\n模式：").append(mode)
                .append("\n轮次：").append(iteration)
                .append("\n证据：");
        int remaining = 10_000;
        for (int index = 0; index < evidence.size() && remaining > 0; index++) {
            Evidence item = evidence.get(index);
            String block = "\n[" + (index + 1) + "] " + item.title() + "\n"
                    + (item.summary().isBlank() ? item.content() : item.summary());
            int length = Math.min(block.length(), remaining);
            result.append(block, 0, length);
            remaining -= length;
        }
        return result.toString();
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

    private static String extractJson(String response) {
        int start = value(response).indexOf('{');
        int end = value(response).lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("证据审查模型未返回 JSON");
        }
        return value(response).substring(start, end + 1);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
