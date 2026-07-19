package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.agentic.AgenticRagModels;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.AgentEventSink;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RunRequest;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.RunResult;
import com.chaitin.niuniuwiki.agentic.AgenticRagModels.UsageSnapshot;
import com.chaitin.niuniuwiki.agentic.AgenticRagService;
import com.chaitin.niuniuwiki.block.BlockWordService;
import com.chaitin.niuniuwiki.compiler.CompiledKnowledgeContext;
import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.CancellationSignal;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.prompt.PromptService;
import com.chaitin.niuniuwiki.rag.RagClient;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.model.ModelGateway.Completion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 封装智能问答相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-26
 */
@Service
public class ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;
    private final RagClient ragClient;
    private final PromptService promptService;
    private final BlockWordService blockWordService;
    private final CompiledKnowledgeContext compiledKnowledgeContext;
    private final AgenticRagService agenticRagService;
    private final ModelGateway modelClient;
    private final ChatPersistenceService persistenceService;
    private final AnswerContextAssembler contextAssembler;

    public ChatService(
            JdbcMaps store,
            JsonMaps jsonMaps,
            RagClient ragClient,
            PromptService promptService,
            BlockWordService blockWordService,
            CompiledKnowledgeContext compiledKnowledgeContext,
            AgenticRagService agenticRagService,
            ModelGateway modelClient,
            ChatPersistenceService persistenceService,
            AnswerContextAssembler contextAssembler
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.ragClient = ragClient;
        this.promptService = promptService;
        this.blockWordService = blockWordService;
        this.compiledKnowledgeContext = compiledKnowledgeContext;
        this.agenticRagService = agenticRagService;
        this.modelClient = modelClient;
        this.persistenceService = persistenceService;
        this.contextAssembler = contextAssembler;
    }

    public ChatResult ask(
            String kbId,
            int appType,
            String message,
            String conversationId,
            String nonce,
            String remoteIp,
            List<String> imagePaths,
            List<ChatAttachment> attachments
    ) {
        return ask(kbId, appType, message, conversationId, nonce, remoteIp, imagePaths, attachments,
                KnowledgeAccessScope.publicAccess());
    }

    public ChatResult ask(
            String kbId,
            int appType,
            String message,
            String conversationId,
            String nonce,
            String remoteIp,
            List<String> imagePaths,
            List<ChatAttachment> attachments,
            KnowledgeAccessScope accessScope
    ) {
        return ask(kbId, appType, message, conversationId, nonce, remoteIp, imagePaths, attachments,
                accessScope, UUID.randomUUID().toString(), AgentEventSink.noop(), CancellationSignal.none());
    }

    public ChatResult ask(
            String kbId,
            int appType,
            String message,
            String conversationId,
            String nonce,
            String remoteIp,
            List<String> imagePaths,
            List<ChatAttachment> attachments,
            KnowledgeAccessScope accessScope,
            String runId,
            AgentEventSink eventSink,
            CancellationSignal cancellationSignal
    ) {
        blockWordService.validate(kbId, message);
        List<String> safeImagePaths = sanitizeImagePaths(imagePaths);
        List<ChatAttachment> safeAttachments = sanitizeAttachments(attachments);
        ChatPersistenceService.OpenTurn turn = persistenceService.openTurn(
                kbId, appType, message, conversationId, nonce, remoteIp,
                safeImagePaths, safeAttachments, runId, accessScope);
        try {
            RunResult agentResult = agenticRagService.execute(
                    new RunRequest(runId, kbId, turn.conversationId(), turn.userMessageId(), message,
                            turn.history(), accessScope),
                    eventSink, cancellationSignal);
            return persistAnswer(kbId, turn.conversationId(), turn.nonce(), turn.appId(),
                    turn.userMessageId(), message, value(remoteIp),
                    safeAttachments, agentResult, eventSink, cancellationSignal);
        } catch (CancellationException exception) {
            agenticRagService.cancel(runId, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            agenticRagService.fail(runId, exception.getMessage());
            throw exception;
        }
    }

    public ChatResult resume(
            String kbId,
            String runId,
            String nonce,
            String remoteIp,
            KnowledgeAccessScope accessScope,
            AgentEventSink eventSink,
            CancellationSignal cancellationSignal
    ) {
        AgenticRagService.RunContext context = agenticRagService.context(runId, kbId);
        Map<String, Object> conversation = store.queryForObject(
                "SELECT id, nonce, app_id FROM conversations WHERE id = ? AND kb_id = ?",
                store.rowMapper(), context.conversationId(), kbId);
        if (!value(conversation.get("nonce")).equals(value(nonce))) {
            throw new ApiException("invalid conversation nonce");
        }
        RunResult agentResult = agenticRagService.resume(runId, kbId, accessScope, eventSink, cancellationSignal);
        return persistAnswer(kbId, context.conversationId(), value(conversation.get("nonce")),
                value(conversation.get("app_id")),
                context.userMessageId(), context.question(), value(remoteIp), List.of(), agentResult,
                eventSink, cancellationSignal);
    }

    private ChatResult persistAnswer(
            String kbId,
            String conversationId,
            String conversationNonce,
            String appId,
            String userMessageId,
            String question,
            String remoteIp,
            List<ChatAttachment> attachments,
            RunResult agentResult,
            AgentEventSink eventSink,
            CancellationSignal cancellationSignal
    ) {
        cancellationSignal.check();
        List<Map<String, Object>> candidateReferences = agentResult.references();
        Completion completion;
        if (agentResult.needsClarification()) {
            completion = new Completion(agentResult.directAnswer(), 0, 0, "agentic", "clarifier");
        } else if (agentResult.remainingTokens() < 512) {
            eventSink.emit(AgenticRagModels.AgentEvent.of(agentResult.runId(), "generate", "DEGRADED",
                    "本次 Agent 已达到 Token 预算，已切换为可验证的证据摘要回答",
                    agentResult.usage().iterations(), agentResult.plan().mode(), List.of(),
                    Map.of("context_mode", "EXTRACTIVE", "remaining_tokens", agentResult.remainingTokens())));
            completion = extractiveFallback(question, candidateReferences, agentResult);
        } else {
            completion = completeWithRecovery(kbId, conversationId, question, candidateReferences, attachments,
                    cancellationSignal, agentResult, eventSink);
        }
        CitationReconciler.Result citationResult = CitationReconciler.reconcile(
                completion.content(), candidateReferences);
        List<Map<String, Object>> references = citationResult.references();
        String answer = citationResult.answer();
        if (!references.isEmpty()
                && Boolean.TRUE.equals(promptService.getInternal(kbId).get("enable_preset_reference"))) {
            answer = CitationReconciler.appendReferenceBlock(answer, references);
        }
        completion = new Completion(answer, completion.promptTokens(), completion.completionTokens(),
                completion.provider(), completion.model());
        String assistantMessageId = UUID.randomUUID().toString();
        List<Map<String, Object>> storedReferences = references.stream()
                .map(this::storedReference)
                .toList();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("references", storedReferences);
        info.put("agent_run_id", agentResult.runId());
        info.put("agent_mode", agentResult.plan().mode().name());
        info.put("agent_plan", agentResult.plan());
        info.put("agent_usage", agentResult.usage());
        info.put("agent_trace", agenticRagService.trace(agentResult.runId()));
        UsageSnapshot completedUsage = new UsageSnapshot(
                agentResult.usage().retrievals(), agentResult.usage().iterations(),
                agentResult.usage().tokens() + Math.max(0, completion.totalTokens()),
                agentResult.usage().elapsedMs(), agentResult.usage().evidenceCount(),
                agentResult.usage().stopReason());
        persistenceService.saveAnswer(kbId, conversationId, appId, userMessageId, assistantMessageId,
                remoteIp, answer, completion, info, references, agentResult.runId(), completedUsage);
        eventSink.emit(AgenticRagModels.AgentEvent.of(agentResult.runId(), "complete", "COMPLETED",
                "回答生成完成", agentResult.usage().iterations(), agentResult.plan().mode(), List.of(),
                Map.of("references", references.size(), "answer_tokens", completion.completionTokens())));
        return new ChatResult(conversationId, conversationNonce, assistantMessageId, answer, references,
                agentResult.runId(), agentResult.plan().mode().name(), agentResult.usage());
    }

    private Completion completeWithRecovery(
            String kbId,
            String conversationId,
            String question,
            List<Map<String, Object>> references,
            List<ChatAttachment> attachments,
            CancellationSignal cancellationSignal,
            RunResult agentResult,
            AgentEventSink eventSink
    ) {
        try {
            return complete(kbId, conversationId, question, references, attachments,
                    cancellationSignal, agentResult.remainingTokens(), ContextMode.FULL);
        } catch (ModelRequestException failure) {
            if (!failure.isContextLimit()) {
                return degradedCompletion(question, references, agentResult, eventSink, failure);
            }
        } catch (ApiException failure) {
            return degradedCompletion(question, references, agentResult, eventSink, failure);
        }

        eventSink.emit(AgenticRagModels.AgentEvent.of(agentResult.runId(), "generate", "RETRY",
                "完整上下文超过模型窗口，正在使用原始证据重试", agentResult.usage().iterations(),
                agentResult.plan().mode(), List.of(), Map.of("context_mode", "SOURCE_ONLY")));
        try {
            return complete(kbId, conversationId, question, references, attachments,
                    cancellationSignal, agentResult.remainingTokens(), ContextMode.SOURCE_ONLY);
        } catch (ModelRequestException failure) {
            if (!failure.isContextLimit()) {
                return degradedCompletion(question, references, agentResult, eventSink, failure);
            }
        } catch (ApiException failure) {
            return degradedCompletion(question, references, agentResult, eventSink, failure);
        }

        eventSink.emit(AgenticRagModels.AgentEvent.of(agentResult.runId(), "generate", "RETRY",
                "原始证据仍超过模型窗口，正在使用摘要证据完成回答", agentResult.usage().iterations(),
                agentResult.plan().mode(), List.of(), Map.of("context_mode", "SUMMARY_ONLY")));
        try {
            return complete(kbId, conversationId, question, references, attachments,
                    cancellationSignal, agentResult.remainingTokens(), ContextMode.SUMMARY_ONLY);
        } catch (ApiException failure) {
            return degradedCompletion(question, references, agentResult, eventSink, failure);
        }
    }

    private Completion degradedCompletion(
            String question,
            List<Map<String, Object>> references,
            RunResult agentResult,
            AgentEventSink eventSink,
            ApiException failure
    ) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("context_mode", "EXTRACTIVE");
        metrics.put("failure_kind", failure instanceof ModelRequestException modelFailure
                ? modelFailure.failureKind().name() : "CLIENT_ERROR");
        if (failure instanceof ModelRequestException modelFailure) {
            metrics.put("upstream_status", modelFailure.upstreamStatus());
        }
        eventSink.emit(AgenticRagModels.AgentEvent.of(agentResult.runId(), "generate", "DEGRADED",
                modelFailureMessage(failure), agentResult.usage().iterations(), agentResult.plan().mode(),
                List.of(), metrics));
        LOGGER.warn("Model answer generation degraded for run {}: {}", agentResult.runId(), failure.getMessage());
        return extractiveFallback(question, references, agentResult);
    }

    private static String modelFailureMessage(ApiException failure) {
        if (!(failure instanceof ModelRequestException modelFailure)) {
            return "模型客户端调用失败，已切换为可验证的证据摘要回答";
        }
        return switch (modelFailure.failureKind()) {
            case AUTHENTICATION -> "模型鉴权失败，已切换为可验证的证据摘要回答";
            case RATE_LIMIT -> "模型服务触发限流，已切换为可验证的证据摘要回答";
            case CONTEXT_LIMIT -> "摘要上下文仍超过模型窗口，已切换为可验证的证据摘要回答";
            case INVALID_REQUEST -> "模型参数或请求格式不兼容，已切换为可验证的证据摘要回答";
            case UPSTREAM_UNAVAILABLE -> "上游模型服务暂不可用，已切换为可验证的证据摘要回答";
            case UNKNOWN -> "上游模型调用失败，已切换为可验证的证据摘要回答";
        };
    }

    /**
     * 将回答引用固化到消息中，避免历史会话被当前活动版本悄悄改写。
     *
     * @author 程序员牛肉
     * @since 2026-07-18
     */
    private Map<String, Object> storedReference(Map<String, Object> reference) {
        Map<String, Object> stored = new LinkedHashMap<>();
        for (String key : List.of("node_id", "node_release_id", "source_version", "knowledge_version_id",
                "knowledge_version", "name", "summary", "url", "emoji")) {
            stored.put(key, reference.getOrDefault(key, ""));
        }
        return stored;
    }

    /**
     * 保存引用时刻的原文。读取快照时仍会使用节点的当前权限进行授权，内容回滚不会恢复权限。
     *
     * @author 程序员牛肉
     * @since 2026-07-18
     */
    public List<Map<String, Object>> search(String kbId, String query) {
        return search(kbId, query, KnowledgeAccessScope.publicAccess());
    }

    public List<Map<String, Object>> search(String kbId, String query, KnowledgeAccessScope accessScope) {
        try {
            String datasetId = store.queryForObject(
                    "SELECT dataset_id FROM knowledge_bases WHERE id = ?", String.class, kbId);
            List<Map<String, Object>> chunks = ragClient.retrieve(
                    datasetId, query, accessScope.groupIds(), List.of());
            Map<String, Map<String, Object>> references = new LinkedHashMap<>();
            String baseUrl = baseUrl(kbId);
            for (Map<String, Object> chunk : chunks) {
                String documentId = value(chunk.get("document_id"));
                List<Map<String, Object>> releases = store.query("""
                        SELECT nr.node_id, nr.name, nr.meta->>'summary' AS summary,
                               nr.meta->>'emoji' AS emoji, links.nav_id, nav.name AS nav_name
                          FROM node_releases nr
                          JOIN kb_release_node_releases links ON links.node_release_id = nr.id
                          JOIN kb_releases kr ON kr.id = links.release_id
                          LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                          LEFT JOIN nodes n ON n.id = nr.node_id
                         WHERE nr.doc_id = ? AND kr.id = (
                               SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                           AND (COALESCE(n.permissions->>'answerable', 'open') = 'open'
                                OR (n.permissions->>'answerable' = 'partial' AND EXISTS (
                                    SELECT 1 FROM node_auth_groups nag
                                     WHERE nag.node_id = nr.node_id AND nag.perm = 'answerable'
                                       AND nag.auth_group_id = ANY(?::int[]))))
                         LIMIT 1
                        """, store.rowMapper(), documentId, kbId, accessScope.postgresGroupArray());
                if (releases.isEmpty()) {
                    continue;
                }
                Map<String, Object> reference = new LinkedHashMap<>(releases.getFirst());
                reference.put("node_path_names", List.of(reference.getOrDefault("nav_name", "")));
                reference.put("url", baseUrl + "/node/" + reference.get("node_id"));
                reference.put("content", value(chunk.get("content")));
                references.putIfAbsent(value(reference.get("node_id")), reference);
            }
            if (!references.isEmpty()) {
                return references.values().stream().limit(8).toList();
            }
        } catch (ApiException exception) {
            // Keep serving existing installations while RAG is temporarily unavailable.
            LOGGER.warn("RAG search failed for knowledge base {}; using PostgreSQL fallback: {}",
                    kbId, exception.getMessage());
        }
        return databaseSearch(kbId, query, accessScope);
    }

    private List<Map<String, Object>> databaseSearch(
            String kbId,
            String query,
            KnowledgeAccessScope accessScope
    ) {
        String pattern = "%" + query.strip().replaceAll("\\s+", "%") + "%";
        List<Map<String, Object>> nodes = store.query("""
                SELECT nr.node_id, nr.name, nr.meta->>'summary' AS summary,
                       nr.meta->>'emoji' AS emoji, links.nav_id, nav.name AS nav_name
                  FROM kb_releases kr
                  JOIN kb_release_node_releases links ON links.release_id = kr.id
                  JOIN node_releases nr ON nr.id = links.node_release_id
                  LEFT JOIN nav_releases nav ON nav.release_id = kr.id AND nav.nav_id = links.nav_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND nr.type = 2 AND (nr.name ILIKE ? OR nr.content ILIKE ?)
                   AND (COALESCE(n.permissions->>'answerable', 'open') = 'open'
                        OR (n.permissions->>'answerable' = 'partial' AND EXISTS (
                            SELECT 1 FROM node_auth_groups nag
                             WHERE nag.node_id = nr.node_id AND nag.perm = 'answerable'
                               AND nag.auth_group_id = ANY(?::int[]))))
                 ORDER BY CASE WHEN nr.name ILIKE ? THEN 0 ELSE 1 END, nr.updated_at DESC
                 LIMIT 8
                """, store.rowMapper(), kbId, pattern, pattern, accessScope.postgresGroupArray(), pattern);
        String baseUrl = baseUrl(kbId);
        nodes.forEach(node -> {
            node.put("name", node.getOrDefault("name", ""));
            node.put("node_path_names", List.of(node.getOrDefault("nav_name", "")));
            node.put("url", baseUrl + "/node/" + node.get("node_id"));
        });
        return nodes;
    }

    private Completion complete(
            String kbId,
            String conversationId,
            String question,
            List<Map<String, Object>> references,
            List<ChatAttachment> attachments,
            CancellationSignal cancellationSignal,
            int remainingTokens,
            ContextMode contextMode
    ) {
        List<Map<String, Object>> history = store.query(
                "SELECT role, content FROM conversation_messages WHERE conversation_id = ? ORDER BY created_at",
                store.rowMapper(), conversationId);
        List<Map<String, Object>> hydratedReferences = new ArrayList<>();
        for (int index = 0; index < references.size(); index++) {
            Map<String, Object> hydrated = new LinkedHashMap<>(references.get(index));
            String content = contextMode == ContextMode.SUMMARY_ONLY
                    ? value(hydrated.get("summary")) : value(hydrated.get("content"));
            if (content.isBlank() && contextMode != ContextMode.SUMMARY_ONLY) {
                String nodeId = value(hydrated.get("node_id"));
                content = store.query(
                        "SELECT nr.content FROM node_releases nr "
                                + "JOIN kb_release_node_releases links ON links.node_release_id = nr.id "
                                + "WHERE links.node_id = ? ORDER BY links.created_at DESC LIMIT 1",
                        (rs, rowNum) -> rs.getString(1), nodeId).stream().findFirst().orElse("");
            }
            hydrated.put("content", content);
            hydratedReferences.add(hydrated);
        }
        Map<String, Object> prompt = promptService.getInternal(kbId);
        String system = value(prompt.get("content"));
        if (!Boolean.TRUE.equals(prompt.get("enable_preset_general_info"))) {
            system += "\n仅根据给定知识库文档回答；若文档没有答案，应明确说明。";
        } else {
            system += "\n知识库资料不足时可以使用通用知识补充，但必须明确区分补充内容。";
        }
        if (Boolean.TRUE.equals(prompt.get("enable_preset_auto_language"))) {
            system += "\n请自动使用与用户问题相同的语言作答。";
        }
        String compiledKnowledge = contextMode == ContextMode.FULL
                ? compiledKnowledgeContext.forSources(
                        kbId,
                        references.stream().map(reference -> value(reference.get("node_id"))).toList())
                : "";
        AnswerContextAssembler.AssembledContext assembled = contextAssembler.assemble(
                hydratedReferences, compiledKnowledge, attachments, contextMode == ContextMode.SUMMARY_ONLY);
        system += "\n回答应准确、简洁，并在引用知识库结论后标注文档编号。"
                + "只标注实际支撑当前结论的文档，不要引用或罗列未使用的候选文档。\n"
                + assembled.securityPolicy();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        int start = Math.max(0, history.size() - 10);
        List<Map<String, Object>> recentHistory = new ArrayList<>(history.subList(start, history.size()));
        if (!recentHistory.isEmpty()
                && "user".equals(value(recentHistory.getLast().get("role")))
                && question.equals(value(recentHistory.getLast().get("content")))) {
            recentHistory.removeLast();
        }
        messages.addAll(recentHistory);
        messages.add(Map.of("role", "user", "content", assembled.evidenceMessage()));
        messages.add(Map.of("role", "user", "content", question));
        Completion completion = modelClient.complete(messages, cancellationSignal,
                remainingTokens, Duration.ofMinutes(2));
        return completion;
    }

    private Completion extractiveFallback(
            String question,
            List<Map<String, Object>> references,
            RunResult agentResult
    ) {
        StringBuilder answer = new StringBuilder();
        if (references.isEmpty()) {
            answer.append("当前知识库没有检索到足以回答这个问题的证据，因此暂时无法给出可靠结论。");
        } else {
            answer.append("根据本次").append(agentResult.plan().mode() == AgenticRagModels.RetrievalMode.MULTI_HOP
                    ? "多跳检索" : "检索").append("，当前能够确认的知识如下：\n\n");
            for (int index = 0; index < references.size(); index++) {
                Map<String, Object> reference = references.get(index);
                String evidence = value(reference.get("summary"));
                if (evidence.isBlank()) {
                    evidence = "已命中该原始文档，但文档没有提供可独立验证完整关系链的摘要，请打开引用核对具体事实";
                } else {
                    evidence = evidence
                            .replaceAll("<[^>]+>", " ")
                            .replaceAll("[#*_`>\\[\\]()]", " ");
                }
                evidence = evidence.replaceAll("\\s+", " ").strip();
                answer.append(index + 1).append(". **").append(value(reference.get("name"))).append("**：")
                        .append(evidence, 0, Math.min(320, evidence.length())).append("。[文档 ")
                        .append(index + 1).append("]\n");
            }
            if (!agentResult.usage().stopReason().equals("EVIDENCE_SUFFICIENT")) {
                answer.append("\n现有证据没有完整覆盖问题中的全部关系或下游影响，以上结论应视为当前知识库范围内的最佳可验证结果，不推断未被文档支持的部分。\n");
            }
        }
        int tokens = Math.max(1, answer.length() / 3);
        return new Completion(answer.toString(), 0, tokens, "agentic", "extractive-fallback");
    }

    private String baseUrl(String kbId) {
        Map<String, Object> kb = store.queryForObject(
                "SELECT access_settings FROM knowledge_bases WHERE id = ?",
                store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(kb.get("access_settings"));
        String base = value(settings.get("base_url")).replaceAll("/+$", "");
        if (!base.isBlank()) {
            return base;
        }
        List<?> hosts = settings.get("hosts") instanceof List<?> list ? list : List.of();
        List<?> sslPorts = settings.get("ssl_ports") instanceof List<?> list ? list : List.of();
        List<?> ports = settings.get("ports") instanceof List<?> list ? list : List.of();
        if (hosts.isEmpty()) {
            return "";
        }
        String host = value(hosts.getFirst());
        if (!sslPorts.isEmpty()) {
            int port = ((Number) sslPorts.getFirst()).intValue();
            return "https://" + host + (port == 443 ? "" : ":" + port);
        }
        if (!ports.isEmpty()) {
            int port = ((Number) ports.getFirst()).intValue();
            return "http://" + host + (port == 80 ? "" : ":" + port);
        }
        return "http://" + host;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> sanitizeImagePaths(List<String> imagePaths) {
        if (imagePaths == null) {
            return List.of();
        }
        return imagePaths.stream()
                .map(ChatService::value)
                .filter(path -> path.startsWith("/static-file/") && !path.contains(".."))
                .distinct()
                .limit(3)
                .toList();
    }

    private List<ChatAttachment> sanitizeAttachments(List<ChatAttachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        int[] remaining = {30_000};
        return attachments.stream()
                .filter(java.util.Objects::nonNull)
                .limit(3)
                .map(attachment -> {
                    String name = value(attachment.name()).strip();
                    String content = value(attachment.content()).strip();
                    int length = Math.min(content.length(), Math.max(0, remaining[0]));
                    remaining[0] -= length;
                    return new ChatAttachment(
                            name.isBlank() ? "未命名文件" : name.substring(0, Math.min(name.length(), 255)),
                            value(attachment.type()),
                            Math.max(0, attachment.size()),
                            content.substring(0, length));
                })
                .filter(attachment -> !attachment.content().isBlank())
                .toList();
    }

    public record ChatAttachment(String name, String type, long size, String content) {
    }

    private enum ContextMode {
        FULL,
        SOURCE_ONLY,
        SUMMARY_ONLY
    }

    public record ChatResult(
            String conversationId,
            String nonce,
            String messageId,
            String answer,
            List<Map<String, Object>> references,
            String runId,
            String agentMode,
            AgenticRagModels.UsageSnapshot agentUsage
    ) {
    }
}
