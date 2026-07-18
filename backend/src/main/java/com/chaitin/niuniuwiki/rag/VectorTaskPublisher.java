package com.chaitin.niuniuwiki.rag;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.compiler.KnowledgeEventLedger;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.DiscardPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提供 NiuniuWiki 后端的RAG 与向量任务基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-05-25
 */
@Component
public class VectorTaskPublisher {

    public static final String VECTOR_SUBJECT = "apps.niuniu-wiki.vector.task";
    public static final String KNOWLEDGE_COMPILE_SUBJECT = "apps.niuniu-wiki.knowledge.compile";

    private final NiuniuWikiProperties properties;
    private final ObjectMapper objectMapper;
    private final KnowledgeEventLedger eventLedger;
    private volatile Connection connection;
    private volatile JetStream jetStream;

    public VectorTaskPublisher(
            NiuniuWikiProperties properties,
            ObjectMapper objectMapper,
            KnowledgeEventLedger eventLedger
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventLedger = eventLedger;
    }

    public void upsertAfterCommit(String kbId, String releaseId, String nodeId) {
        publishAfterCommit(Map.of(
                "kb_id", kbId,
                "node_release_id", releaseId,
                "node_id", nodeId,
                "doc_id", "",
                "action", "upsert",
                "group_ids", List.of()));
    }

    public void deleteAfterCommit(String kbId, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        publishAfterCommit(Map.of(
                "kb_id", kbId,
                "node_release_id", "",
                "node_id", "",
                "doc_id", documentId,
                "action", "delete",
                "group_ids", List.of()));
    }

    public void summaryAfterCommit(String kbId, String nodeId) {
        publishAfterCommit(Map.of(
                "kb_id", kbId,
                "node_release_id", "",
                "node_id", nodeId,
                "doc_id", "",
                "action", "summary",
                "group_ids", List.of()));
    }

    public void groupsAfterCommit(String kbId, String documentId, List<Integer> groupIds) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        publishAfterCommit(Map.of(
                "kb_id", kbId,
                "node_release_id", "",
                "node_id", "",
                "doc_id", documentId,
                "action", "update_group_ids",
                "group_ids", groupIds == null ? List.of() : groupIds));
    }

    public void compileAfterCommit(String kbId, String runId) {
        publishAfterCommit(KNOWLEDGE_COMPILE_SUBJECT, Map.of(
                "kb_id", kbId,
                "run_id", runId));
    }

    public void compileNow(String kbId, String runId) {
        publishNow(KNOWLEDGE_COMPILE_SUBJECT, Map.of("kb_id", kbId, "run_id", runId));
    }

    public void publishNow(Map<String, Object> task) {
        publishNow(VECTOR_SUBJECT, task);
    }

    private void publishNow(String subject, Map<String, Object> task) {
        try {
            byte[] payload = objectMapper.writeValueAsString(task).getBytes(StandardCharsets.UTF_8);
            stream().publish(subject, payload);
        } catch (Exception exception) {
            reset();
            throw new ApiException("NATS 异步任务发布失败: " + exception.getMessage());
        }
    }

    private void publishAfterCommit(Map<String, Object> task) {
        publishAfterCommit(VECTOR_SUBJECT, eventLedger.enrich(task));
    }

    private void publishAfterCommit(String subject, Map<String, Object> task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publishNow(subject, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(subject, task);
            }
        });
    }

    private JetStream stream() throws Exception {
        JetStream current = jetStream;
        if (current != null && connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            return current;
        }
        synchronized (this) {
            if (jetStream == null || connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
                Options.Builder builder = new Options.Builder()
                        .server(properties.getMessaging().getNatsUrl())
                        .connectionName("niuniu-wiki-java-api")
                        .connectionTimeout(Duration.ofSeconds(5))
                        .maxReconnects(3);
                if (!properties.getMessaging().getUsername().isBlank()) {
                    builder.userInfo(
                            properties.getMessaging().getUsername().toCharArray(),
                            properties.getMessaging().getPassword().toCharArray());
                }
                connection = Nats.connect(builder.build());
                ensureTaskStream(connection);
                jetStream = connection.jetStream();
            }
            return jetStream;
        }
    }

    public static void ensureTaskStream(Connection connection) throws Exception {
        JetStreamManagement management = connection.jetStreamManagement();
        if (management.getStreamNames().contains("task")) {
            StreamConfiguration existing = management.getStreamInfo("task").getConfiguration();
            Set<String> subjects = new LinkedHashSet<>(existing.getSubjects());
            if (subjects.add(KNOWLEDGE_COMPILE_SUBJECT)) {
                management.updateStream(StreamConfiguration.builder(existing).subjects(subjects).build());
            }
            return;
        }
        management.addStream(StreamConfiguration.builder()
                .name("task")
                .subjects("apps.niuniu-wiki.summary.task", VECTOR_SUBJECT, KNOWLEDGE_COMPILE_SUBJECT)
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .discardPolicy(DiscardPolicy.Old)
                .maxAge(Duration.ofDays(7))
                .maxBytes(1024L * 1024 * 1024)
                .maxMessages(1_000_000)
                .maxMsgSize(50L * 1024 * 1024)
                .replicas(1)
                .duplicateWindow(Duration.ofMinutes(2))
                .build());
    }

    private synchronized void reset() {
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        connection = null;
        jetStream = null;
    }

    @PreDestroy
    void close() {
        reset();
    }
}
