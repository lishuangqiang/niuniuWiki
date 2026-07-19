package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.agentic.AgentRunRegistry;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEvent;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEventSink;
import com.chaitin.niuniuwiki.agentic.AgenticRagService;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 在虚拟线程中执行问答，并把 Agent 计划、检索、反思和最终回答实时转换为兼容 SSE 事件。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
@Service
public class ChatStreamService {

    private static final long STREAM_TIMEOUT_MS = 240_000L;

    private final ChatService chatService;
    private final AgenticRagService agenticRagService;
    private final AgentRunRegistry runRegistry;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore concurrentStreams = new Semaphore(64, true);

    public ChatStreamService(
            ChatService chatService,
            AgenticRagService agenticRagService,
            AgentRunRegistry runRegistry,
            ObjectMapper objectMapper
    ) {
        this.chatService = chatService;
        this.agenticRagService = agenticRagService;
        this.runRegistry = runRegistry;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(
            String kbId,
            int appType,
            String message,
            String conversationId,
            String nonce,
            String remoteIp,
            List<String> imagePaths,
            List<ChatService.ChatAttachment> attachments,
            KnowledgeAccessScope accessScope
    ) {
        String runId = UUID.randomUUID().toString();
        return execute(runId, (signal, sink) -> chatService.ask(kbId, appType, message, conversationId, nonce,
                remoteIp, imagePaths, attachments, accessScope, runId, sink, signal));
    }

    public SseEmitter resume(
            String kbId,
            String runId,
            String nonce,
            String remoteIp,
            KnowledgeAccessScope accessScope
    ) {
        return execute(runId, (signal, sink) -> chatService.resume(
                kbId, runId, nonce, remoteIp, accessScope, sink, signal));
    }

    public boolean cancel(String kbId, String runId) {
        agenticRagService.context(runId, kbId);
        return runRegistry.cancel(runId);
    }

    private SseEmitter execute(String runId, EventAwareStreamOperation operation) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        CancellationSignal signal = runRegistry.register(runId);
        AtomicBoolean terminal = new AtomicBoolean(false);
        Runnable cancelIfActive = () -> {
            if (!terminal.get()) {
                runRegistry.cancel(runId);
            }
        };
        emitter.onCompletion(cancelIfActive);
        emitter.onTimeout(() -> {
            cancelIfActive.run();
            emitter.complete();
        });
        emitter.onError(error -> cancelIfActive.run());

        executor.submit(() -> {
            boolean acquired = false;
            try {
                acquired = concurrentStreams.tryAcquire();
                if (!acquired) {
                    throw new com.chaitin.niuniuwiki.common.ApiException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "当前问答请求过多，请稍后重试");
                }
                send(emitter, "run_id", runId, null);
                ChatService.ChatResult result = operation.execute(
                        signal, event -> sendAgentEvent(emitter, event));
                send(emitter, "conversation_id", result.conversationId(), null);
                send(emitter, "nonce", result.nonce(), null);
                send(emitter, "message_id", result.messageId(), null);
                for (Map<String, Object> reference : result.references()) {
                    sendChunk(emitter, reference);
                }
                send(emitter, "data", result.answer(), null);
                send(emitter, "done", "", null);
                terminal.set(true);
                runRegistry.complete(runId);
                emitter.complete();
            } catch (CancellationException exception) {
                terminal.set(true);
                runRegistry.complete(runId);
                safeSend(emitter, "cancelled", exception.getMessage());
                emitter.complete();
            } catch (Exception exception) {
                terminal.set(true);
                runRegistry.complete(runId);
                agenticRagService.fail(runId, exception.getMessage());
                safeSend(emitter, "error", exception.getMessage());
                emitter.complete();
            } finally {
                if (acquired) {
                    concurrentStreams.release();
                }
            }
        });
        return emitter;
    }

    private void sendAgentEvent(SseEmitter emitter, AgentEvent event) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("run_id", event.runId());
        detail.put("stage", event.stage());
        detail.put("status", event.status());
        detail.put("message", event.message());
        detail.put("iteration", event.iteration());
        detail.put("mode", event.mode() == null ? "" : event.mode().name());
        detail.put("queries", event.queries());
        detail.put("metrics", event.metrics());
        try {
            send(emitter, "agent_event", event.message(), detail);
        } catch (IOException exception) {
            throw new CancellationException("客户端已断开连接");
        }
    }

    private void send(SseEmitter emitter, String type, String content, Map<String, Object> agentEvent)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content == null ? "" : content);
        if (agentEvent != null) {
            payload.put("agent_event", agentEvent);
        }
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendChunk(SseEmitter emitter, Map<String, Object> reference) throws IOException {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("node_id", value(reference.get("node_id")));
        chunk.put("name", value(reference.get("name")));
        chunk.put("summary", value(reference.get("summary")));
        chunk.put("url", value(reference.get("url")));
        chunk.put("emoji", value(reference.get("emoji")));
        chunk.put("node_release_id", value(reference.get("node_release_id")));
        chunk.put("source_version", value(reference.get("source_version")));
        chunk.put("knowledge_version_id", value(reference.get("knowledge_version_id")));
        chunk.put("knowledge_version", reference.getOrDefault("knowledge_version", ""));
        chunk.put("queries", reference.getOrDefault("queries", List.of()));
        chunk.put("hops", reference.getOrDefault("hops", List.of()));
        Map<String, Object> payload = Map.of(
                "type", "chunk_result",
                "content", "",
                "chunk_result", chunk);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void safeSend(SseEmitter emitter, String type, String content) {
        try {
            send(emitter, type, content, null);
        } catch (Exception ignored) {
            // The client has already gone away; state has been persisted by the Agent service.
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    private interface EventAwareStreamOperation {
        ChatService.ChatResult execute(CancellationSignal signal, AgentEventSink sink);
    }
}
