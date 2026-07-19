package com.chaitin.niuniuwiki.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将模型声明的文档编号收敛为最终引用集合，避免把检索候选误展示为实际引用。
 *
 * @author 程序员牛肉
 * @since 2026-06-17
 */
final class CitationReconciler {

    private static final Pattern CITATION = Pattern.compile("\\[\\s*文档\\s*(\\d+)\\s*]");

    private CitationReconciler() {
    }

    static Result reconcile(String answer, List<Map<String, Object>> candidates) {
        String safeAnswer = answer == null ? "" : answer;
        List<Map<String, Object>> safeCandidates = candidates == null ? List.of() : candidates;
        Map<Integer, Integer> renumbering = new LinkedHashMap<>();
        Matcher matcher = CITATION.matcher(safeAnswer);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            int originalNumber = Integer.parseInt(matcher.group(1));
            if (originalNumber < 1 || originalNumber > safeCandidates.size()) {
                matcher.appendReplacement(rewritten, "");
                continue;
            }
            int displayNumber = renumbering.computeIfAbsent(originalNumber, ignored -> renumbering.size() + 1);
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement("[文档 " + displayNumber + "]"));
        }
        matcher.appendTail(rewritten);

        List<Map<String, Object>> references = new ArrayList<>(renumbering.size());
        renumbering.keySet().forEach(number -> references.add(safeCandidates.get(number - 1)));
        return new Result(rewritten.toString(), List.copyOf(references));
    }

    static String appendReferenceBlock(String answer, List<Map<String, Object>> references) {
        if (references == null || references.isEmpty()) {
            return answer == null ? "" : answer;
        }
        StringBuilder result = new StringBuilder(answer == null ? "" : answer).append("\n\n");
        for (int index = 0; index < references.size(); index++) {
            Map<String, Object> reference = references.get(index);
            result.append("> [").append(index + 1).append("]. [")
                    .append(value(reference.get("name"))).append("](")
                    .append(value(reference.get("url"))).append(")\n");
        }
        return result.toString();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record Result(String answer, List<Map<String, Object>> references) {
    }
}
