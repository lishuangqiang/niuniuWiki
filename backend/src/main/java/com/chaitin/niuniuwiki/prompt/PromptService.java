package com.chaitin.niuniuwiki.prompt;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 保存并解析知识库级问答和摘要提示词。
 *
 * @author 程序员牛肉
 * @since 2026-05-07
 */
@Service
public class PromptService {

    public static final String DEFAULT_CHAT_PROMPT =
            "你是牛牛 Wiki 知识库助手。请优先依据给定知识库文档准确回答问题；资料不足时应明确说明。";
    public static final String DEFAULT_SUMMARY_PROMPT =
            "你是文档摘要助手。请用不超过180个中文字符准确概括文档，只输出摘要。";

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public PromptService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public Map<String, Object> get(String kbId) {
        authService.requireKbPermission(kbId, "not null");
        return getInternal(kbId);
    }

    public Map<String, Object> getInternal(String kbId) {
        List<Map<String, Object>> rows = store.query(
                "SELECT value FROM settings WHERE kb_id = ? AND key = 'ai_prompt'",
                store.rowMapper(), kbId);
        Map<String, Object> result = defaults();
        if (!rows.isEmpty()) {
            result.putAll(jsonMaps.jsonMap(rows.getFirst().get("value")));
        }
        if (value(result.get("content")).isBlank()) {
            result.put("content", DEFAULT_CHAT_PROMPT);
        }
        if (value(result.get("summary_content")).isBlank()) {
            result.put("summary_content", DEFAULT_SUMMARY_PROMPT);
        }
        return result;
    }

    public Map<String, Object> update(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        Map<String, Object> setting = defaults();
        for (String key : List.of(
                "content", "summary_content", "enable_preset",
                "enable_preset_auto_language", "enable_preset_general_info", "enable_preset_reference")) {
            if (request.containsKey(key)) {
                setting.put(key, request.get(key));
            }
        }
        if (value(setting.get("content")).isBlank()) {
            setting.put("content", DEFAULT_CHAT_PROMPT);
        }
        if (value(setting.get("summary_content")).isBlank()) {
            setting.put("summary_content", DEFAULT_SUMMARY_PROMPT);
        }
        store.update(
                "INSERT INTO settings(kb_id, key, value, description, created_at, updated_at) "
                        + "VALUES (?, 'ai_prompt', ?::jsonb, '知识库 AI 提示词', now(), now()) "
                        + "ON CONFLICT (kb_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
                kbId, jsonMaps.json(setting));
        return setting;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("content", DEFAULT_CHAT_PROMPT);
        values.put("summary_content", DEFAULT_SUMMARY_PROMPT);
        values.put("enable_preset", false);
        values.put("enable_preset_auto_language", true);
        values.put("enable_preset_general_info", true);
        values.put("enable_preset_reference", true);
        return values;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
