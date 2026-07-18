package com.chaitin.niuniuwiki.compiler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 提供知识编译结果解析、切片、稳定键与内容指纹等纯函数能力。
 *
 * @author 程序员牛肉
 * @since 2026-07-17
 */
final class KnowledgeCompilerSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final int CHUNK_SIZE = 32_000;

    private KnowledgeCompilerSupport() {
    }

    static List<String> chunks(String content) {
        String safe = value(content).strip();
        if (safe.isBlank()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < safe.length()) {
            int end = Math.min(safe.length(), offset + CHUNK_SIZE);
            if (end < safe.length()) {
                int paragraph = safe.lastIndexOf('\n', end);
                if (paragraph > offset + CHUNK_SIZE / 2) {
                    end = paragraph;
                }
            }
            chunks.add(safe.substring(offset, end).strip());
            offset = end;
        }
        return chunks;
    }

    static List<ArtifactDraft> parse(ObjectMapper objectMapper, String response) throws Exception {
        String json = extractJson(response);
        Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE);
        if (!(root.get("artifacts") instanceof List<?> artifacts) || artifacts.isEmpty()) {
            throw new IllegalArgumentException("模型未返回 artifacts");
        }
        List<ArtifactDraft> result = new ArrayList<>();
        for (Object item : artifacts) {
            if (!(item instanceof Map<?, ?> source)) {
                continue;
            }
            Map<String, Object> artifact = stringMap(source);
            String title = value(artifact.get("title")).strip();
            String content = value(artifact.get("content")).strip();
            if (title.isBlank() || content.isBlank()) {
                continue;
            }
            result.add(new ArtifactDraft(
                    stableKey(value(artifact.get("key")).isBlank() ? title : value(artifact.get("key"))),
                    allowedType(value(artifact.get("type"))),
                    title,
                    value(artifact.get("summary")).strip(),
                    content,
                    mapList(artifact.get("facts")),
                    mapList(artifact.get("entities")),
                    confidence(artifact.get("confidence"))));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("模型未返回有效知识页面");
        }
        return result;
    }

    static ArtifactDraft fallback(String title, String summary, String content) {
        String safeContent = value(content).strip();
        String safeSummary = value(summary).strip();
        if (safeSummary.isBlank()) {
            safeSummary = safeContent.substring(0, Math.min(180, safeContent.length()));
        }
        return new ArtifactDraft(
                stableKey(title),
                "reference",
                value(title).isBlank() ? "未命名知识" : title,
                safeSummary,
                safeContent,
                List.of(),
                List.of(),
                0.5d);
    }

    static String stableKey(String value) {
        String normalized = value(value).strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "knowledge" : normalized.substring(0, Math.min(120, normalized.length()));
    }

    static String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算知识指纹", exception);
        }
    }

    static String factKey(Map<String, Object> fact) {
        return normalize(fact.get("subject")) + "::" + normalize(fact.get("predicate"));
    }

    static String normalize(Object value) {
        return value(value).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String extractJson(String response) {
        String safe = value(response).strip();
        int start = safe.indexOf('{');
        int end = safe.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型响应中不存在 JSON 对象");
        }
        return safe.substring(start, end + 1);
    }

    private static String allowedType(String type) {
        return switch (value(type).toLowerCase(Locale.ROOT)) {
            case "concept", "process", "decision", "reference" -> value(type).toLowerCase(Locale.ROOT);
            default -> "reference";
        };
    }

    private static double confidence(Object value) {
        double result = value instanceof Number number ? number.doubleValue() : 0.75d;
        return Math.max(0d, Math.min(1d, result));
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(KnowledgeCompilerSupport::stringMap)
                .toList();
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    record ArtifactDraft(
            String key,
            String type,
            String title,
            String summary,
            String content,
            List<Map<String, Object>> facts,
            List<Map<String, Object>> entities,
            double confidence
    ) {
    }
}
