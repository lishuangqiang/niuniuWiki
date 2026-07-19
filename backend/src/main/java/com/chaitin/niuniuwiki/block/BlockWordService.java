package com.chaitin.niuniuwiki.block;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 管理问答敏感词，并在模型调用前执行大小写不敏感匹配。
 *
 * @author 程序员牛肉
 * @since 2026-04-24
 */
@Service
public class BlockWordService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public BlockWordService(JdbcMaps store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public Map<String, Object> get(String kbId) {
        authService.requireKbPermission(kbId, "not null");
        return Map.of("words", words(kbId));
    }

    public void update(Map<String, Object> request) {
        String kbId = value(request.get("kb_id"));
        authService.requireKbPermission(kbId, AuthService.FULL_CONTROL);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (request.get("block_words") instanceof List<?> values) {
            values.stream().map(String::valueOf).map(String::strip).filter(item -> !item.isBlank())
                    .limit(500).forEach(normalized::add);
        }
        store.update(
                "INSERT INTO settings(kb_id, key, value, description, created_at, updated_at) "
                        + "VALUES (?, 'block_words', ?::jsonb, 'AI 问答屏蔽词', now(), now()) "
                        + "ON CONFLICT (kb_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
                kbId, jsonMaps.json(Map.of("words", normalized)));
    }

    public void validate(String kbId, String input) {
        String normalized = value(input).toLowerCase(Locale.ROOT);
        boolean blocked = words(kbId).stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
        if (blocked) {
            throw new ApiException("问题包含已配置的屏蔽关键词");
        }
    }

    private List<String> words(String kbId) {
        List<Map<String, Object>> rows = store.query(
                "SELECT value FROM settings WHERE kb_id = ? AND key = 'block_words'",
                store.rowMapper(), kbId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Object values = jsonMaps.jsonMap(rows.getFirst().get("value")).get("words");
        return values instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
