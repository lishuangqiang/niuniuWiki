package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 读取持久化会话并输出微信兼容的事件流。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class WechatAnswerService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final ObjectMapper objectMapper;

    public WechatAnswerService(JdbcMaps store, JsonMaps jsonMaps, ObjectMapper objectMapper) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.objectMapper = objectMapper;
    }

    public SseEmitter answer(String id) {
        List<Map<String, Object>> messages = store.query(
                "SELECT id, role, content, info FROM conversation_messages WHERE conversation_id = ? ORDER BY created_at",
                store.rowMapper(), id);
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            if (!messages.isEmpty()) {
                send(emitter, "question", String.valueOf(messages.getFirst().get("content")));
            }
            if (messages.size() > 1) {
                Map<String, Object> answer = messages.get(1);
                Map<String, Object> feedback = jsonMaps.jsonMap(answer.get("info"));
                send(emitter, "feedback_score", String.valueOf(feedback.getOrDefault("score", 0)));
                send(emitter, "message_id", String.valueOf(answer.get("id")));
                send(emitter, "answer", String.valueOf(answer.get("content")));
            }
            send(emitter, "done", "");
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String type, String content) throws IOException {
        emitter.send(SseEmitter.event().data(json(Map.of("type", type, "content", content))));
    }

    private String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }
}
