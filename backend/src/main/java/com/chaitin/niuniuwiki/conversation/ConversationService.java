package com.chaitin.niuniuwiki.conversation;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.common.PageResult;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 封装会话相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-06
 */
@Service
public class ConversationService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public ConversationService(MyBatisStore store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public PageResult<List<Map<String, Object>>> list(
            String kbId,
            String appId,
            String subject,
            String remoteIp,
            int page,
            int perPage
    ) {
        require(kbId);
        StringBuilder where = new StringBuilder(" WHERE c.kb_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        if (appId != null && !appId.isBlank()) {
            where.append(" AND c.app_id = ?");
            args.add(appId);
        }
        if (subject != null && !subject.isBlank()) {
            where.append(" AND c.subject ILIKE ?");
            args.add("%" + subject + "%");
        }
        if (remoteIp != null && !remoteIp.isBlank()) {
            where.append(" AND c.remote_ip ILIKE ?");
            args.add("%" + remoteIp + "%");
        }
        long total = store.queryForObject(
                "SELECT count(*) FROM conversations c" + where,
                Long.class,
                args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(Math.max(0, page - 1) * perPage);
        listArgs.add(perPage);
        List<Map<String, Object>> data = store.query("""
                SELECT c.*, a.name AS app_name, a.type AS app_type,
                       feedback.info AS feedback_info
                  FROM conversations c
                  LEFT JOIN apps a ON c.app_id = a.id
                  LEFT JOIN LATERAL (
                       SELECT info FROM conversation_messages cm
                        WHERE cm.conversation_id = c.id AND cm.role = 'assistant'
                          AND cm.info IS NOT NULL AND cm.info->>'score' <> '0'
                        ORDER BY cm.created_at DESC LIMIT 1
                  ) feedback ON true
                """ + where + " ORDER BY c.created_at DESC OFFSET ? LIMIT ?",
                store.rowMapper(),
                listArgs.toArray());
        data.forEach(row -> row.put("ip_address", ipAddress(row.get("remote_ip"))));
        return new PageResult<>(total, data);
    }

    public Map<String, Object> detail(String kbId, String id, boolean share) {
        if (!share) {
            require(kbId);
        }
        Map<String, Object> conversation = store.queryForObject(
                "SELECT id, app_id, subject, remote_ip, created_at FROM conversations WHERE id = ? AND kb_id = ?",
                store.rowMapper(), id, kbId);
        String messageColumns = share
                ? "role, content, image_paths, info, created_at"
                : "id, conversation_id, app_id, kb_id, role, content, image_paths, provider, model, "
                    + "prompt_tokens, completion_tokens, total_tokens, remote_ip, created_at, info, parent_id";
        List<Map<String, Object>> messages = store.query(
                "SELECT " + messageColumns + " FROM conversation_messages WHERE conversation_id = ? ORDER BY created_at",
                store.rowMapper(), id);
        Map<String, Object> result = new LinkedHashMap<>(conversation);
        result.put("ip_address", ipAddress(conversation.get("remote_ip")));
        result.put("messages", messages);
        result.put("references", store.query("""
                SELECT DISTINCT ON (node_id) conversation_id, app_id, node_id, name, url, favicon,
                       node_release_id, knowledge_version_id, recorded_at
                  FROM conversation_references
                 WHERE conversation_id = ?
                 ORDER BY node_id, recorded_at DESC
                """, store.rowMapper(), id));
        if (share) {
            result.remove("app_id");
            result.remove("remote_ip");
        }
        return result;
    }

    public PageResult<List<Map<String, Object>>> feedbackMessages(String kbId, int page, int perPage) {
        require(kbId);
        String base = " FROM conversation_messages cm JOIN conversations c ON c.id = cm.conversation_id "
                + "JOIN apps a ON cm.app_id = a.id WHERE c.kb_id = ? AND cm.role = 'assistant' "
                + "AND cm.info IS NOT NULL AND cm.info->>'score' <> '0'";
        long total = store.queryForObject("SELECT count(*)" + base, Long.class, kbId);
        List<Map<String, Object>> messages = store.query("""
                SELECT cm.id, cm.conversation_id, cm.app_id, a.type AS app_type,
                       question.content AS question, cm.content AS answer, c.info AS conversation_info,
                       cm.remote_ip, cm.info, cm.created_at
                  FROM conversation_messages cm
                  JOIN conversations c ON c.id = cm.conversation_id
                  JOIN apps a ON cm.app_id = a.id
                  LEFT JOIN LATERAL (
                       SELECT content FROM conversation_messages previous
                        WHERE previous.conversation_id = cm.conversation_id AND previous.role = 'user'
                          AND previous.created_at < cm.created_at
                        ORDER BY previous.created_at DESC LIMIT 1
                  ) question ON true
                 WHERE c.kb_id = ? AND cm.role = 'assistant'
                   AND cm.info IS NOT NULL AND cm.info->>'score' <> '0'
                 ORDER BY cm.created_at DESC OFFSET ? LIMIT ?
                """, store.rowMapper(), kbId, Math.max(0, page - 1) * perPage, perPage);
        return new PageResult<>(total, messages);
    }

    public Map<String, Object> message(String kbId, String id) {
        require(kbId);
        return store.queryForObject(
                "SELECT * FROM conversation_messages WHERE id = ? AND kb_id = ?",
                store.rowMapper(), id, kbId);
    }

    public void feedback(Map<String, Object> request) {
        String messageId = String.valueOf(request.getOrDefault("message_id", ""));
        Map<String, Object> existing = store.queryForObject(
                "SELECT info FROM conversation_messages WHERE id = ?",
                store.rowMapper(), messageId);
        Map<String, Object> info = jsonMaps.jsonMap(existing.get("info"));
        if (number(info.get("score")) != 0) {
            throw new com.chaitin.niuniuwiki.common.ApiException("already voted for this message, please do not vote again");
        }
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("score", request.getOrDefault("score", 0));
        feedback.put("feedback_type", request.getOrDefault("type", ""));
        feedback.put("feedback_content", request.getOrDefault("feedback_content", ""));
        store.update("UPDATE conversation_messages SET info = ?::jsonb WHERE id = ?", jsonMaps.json(feedback), messageId);
    }

    private void require(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
    }

    private static int number(Object value) {
        if (value == null) {
            return 0;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static Map<String, Object> ipAddress(Object remoteIp) {
        return Map.of(
                "ip", remoteIp == null ? "" : String.valueOf(remoteIp),
                "country", "未知",
                "province", "",
                "city", "");
    }
}
