package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.retrieval.Evidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对多路、多轮检索证据执行内容去重、节点聚合与 Reciprocal Rank Fusion 排序。
 *
 * @author 程序员牛肉
 * @since 2026-05-31
 */
final class EvidenceAccumulator {

    private final Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
    private final Map<String, Double> fusionScores = new LinkedHashMap<>();
    private final Map<String, Double> relevanceScores = new LinkedHashMap<>();
    private final Map<String, Set<String>> nodeQueries = new LinkedHashMap<>();

    int addAll(List<Evidence> evidence) {
        int added = 0;
        for (int rank = 0; rank < evidence.size(); rank++) {
            Evidence item = evidence.get(rank);
            String contentKey = normalizedContentKey(item);
            if (evidenceByKey.values().stream().anyMatch(existing -> normalizedContentKey(existing).equals(contentKey))) {
                mergeScore(item, rank);
                continue;
            }
            Evidence previous = evidenceByKey.putIfAbsent(item.evidenceKey(), item);
            mergeScore(item, rank);
            if (previous == null) {
                added++;
            }
        }
        return added;
    }

    List<Evidence> rankedEvidence() {
        return evidenceByKey.values().stream()
                .sorted(Comparator.comparingDouble(this::combinedScore).reversed())
                .toList();
    }

    List<Map<String, Object>> references(int limit) {
        Map<String, List<Evidence>> grouped = new LinkedHashMap<>();
        for (Evidence item : rankedEvidence()) {
            String key = item.nodeId().isBlank() ? item.documentId() : item.nodeId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<Map.Entry<String, List<Evidence>>> rankedGroups = grouped.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Evidence>>>comparingDouble(
                        entry -> fusionScores.getOrDefault(entry.getKey(), 0d)).reversed())
                .toList();
        if (rankedGroups.isEmpty()) {
            return List.of();
        }
        double topScore = fusionScores.getOrDefault(rankedGroups.getFirst().getKey(), 0d);
        double threshold = Math.max(0.008d, topScore * 0.30d);
        double topRelevance = rankedGroups.stream()
                .mapToDouble(entry -> relevanceScores.getOrDefault(entry.getKey(), 0d))
                .max()
                .orElse(0d);
        double relevanceThreshold = Math.max(0d, topRelevance * 0.30d);
        return rankedGroups.stream()
                .filter(entry -> fusionScores.getOrDefault(entry.getKey(), 0d) >= threshold)
                .filter(entry -> relevanceScores.getOrDefault(entry.getKey(), 0d) >= relevanceThreshold)
                .limit(limit)
                .map(entry -> reference(entry.getValue()))
                .toList();
    }

    int size() {
        return evidenceByKey.size();
    }

    private void mergeScore(Evidence item, int rank) {
        String nodeKey = item.nodeId().isBlank() ? item.documentId() : item.nodeId();
        double contribution = 1d / (60d + rank + 1d) + Math.max(0d, item.score()) * 0.01d;
        fusionScores.merge(nodeKey, contribution, Double::sum);
        relevanceScores.merge(nodeKey, Math.max(0d, item.score()), Math::max);
        nodeQueries.computeIfAbsent(nodeKey, ignored -> new LinkedHashSet<>()).add(item.query());
    }

    private double combinedScore(Evidence evidence) {
        String nodeKey = evidence.nodeId().isBlank() ? evidence.documentId() : evidence.nodeId();
        return fusionScores.getOrDefault(nodeKey, 0d) + Math.max(0d, evidence.score()) * 0.001d;
    }

    private Map<String, Object> reference(List<Evidence> group) {
        Evidence first = group.getFirst();
        StringBuilder content = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (Evidence evidence : group) {
            String safe = value(evidence.content()).strip();
            if (safe.isBlank() || !seen.add(safe)) {
                continue;
            }
            int remaining = 9_000 - content.length();
            if (remaining <= 0) {
                break;
            }
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(safe, 0, Math.min(remaining, safe.length()));
        }
        String nodeKey = first.nodeId().isBlank() ? first.documentId() : first.nodeId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node_id", first.nodeId());
        result.put("document_id", first.documentId());
        result.put("name", first.title());
        result.put("summary", first.summary());
        result.put("content", content.toString());
        result.put("url", first.url());
        result.put("emoji", first.emoji());
        result.put("score", fusionScores.getOrDefault(nodeKey, first.score()));
        result.put("queries", new ArrayList<>(nodeQueries.getOrDefault(nodeKey, Set.of())));
        result.put("hops", group.stream().map(Evidence::hop).distinct().sorted().toList());
        result.put("node_release_id", metadata(group, "node_release_id"));
        result.put("source_version", metadata(group, "source_version"));
        result.put("knowledge_version_id", metadata(group, "knowledge_version_id"));
        result.put("knowledge_version", metadata(group, "knowledge_version"));
        return result;
    }

    private static Object metadata(List<Evidence> group, String key) {
        return group.stream()
                .map(Evidence::metadata)
                .filter(java.util.Objects::nonNull)
                .map(values -> values.get(key))
                .filter(java.util.Objects::nonNull)
                .filter(value -> !String.valueOf(value).isBlank())
                .findFirst()
                .orElse("");
    }

    private static String normalizedContentKey(Evidence evidence) {
        String content = value(evidence.content()).replaceAll("\\s+", " ").strip();
        return evidence.documentId() + ":" + content.substring(0, Math.min(240, content.length()));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
