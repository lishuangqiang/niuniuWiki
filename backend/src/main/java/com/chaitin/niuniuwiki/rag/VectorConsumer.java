package com.chaitin.niuniuwiki.rag;

import com.chaitin.niuniuwiki.compiler.KnowledgeCompilerEngine;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PullSubscribeOptions;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 提供 NiuniuWiki 后端的RAG 与向量任务基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-12
 */
@Component
@Profile("consumer")
public class VectorConsumer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final String STATUS_SUBJECT = "raglite.events.doc.update";

    private final NiuniuWikiProperties properties;
    private final ObjectMapper objectMapper;
    private final VectorTaskHandler handler;
    private final KnowledgeCompilerEngine compilerEngine;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private volatile boolean running;
    private Connection connection;

    public VectorConsumer(
            NiuniuWikiProperties properties,
            ObjectMapper objectMapper,
            VectorTaskHandler handler,
            KnowledgeCompilerEngine compilerEngine
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.compilerEngine = compilerEngine;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Options.Builder builder = new Options.Builder()
                .server(properties.getMessaging().getNatsUrl())
                .connectionName("niuniu-wiki-java-consumer")
                .maxReconnects(-1);
        if (!properties.getMessaging().getUsername().isBlank()) {
            builder.userInfo(properties.getMessaging().getUsername().toCharArray(),
                    properties.getMessaging().getPassword().toCharArray());
        }
        connection = Nats.connect(builder.build());
        VectorTaskPublisher.ensureTaskStream(connection);
        JetStream jetStream = connection.jetStream();
        running = true;
        workers.submit(() -> consume(jetStream, VectorTaskPublisher.VECTOR_SUBJECT,
                "niuniu-wiki-vector-consumer", handler::handle));
        workers.submit(() -> consume(jetStream, STATUS_SUBJECT,
                "raglite-doc-update-consumer", handler::handleDocumentStatus));
        workers.submit(() -> consume(jetStream, VectorTaskPublisher.KNOWLEDGE_COMPILE_SUBJECT,
                "niuniu-wiki-knowledge-compiler", compilerEngine::handle));
        LOGGER.info("NiuniuWiki Java consumer started");
    }

    private void consume(JetStream jetStream, String subject, String durable, TaskHandler taskHandler) {
        try {
            PullSubscribeOptions options = PullSubscribeOptions.builder().durable(durable).build();
            JetStreamSubscription subscription = jetStream.subscribe(subject, options);
            while (running && !Thread.currentThread().isInterrupted()) {
                List<Message> messages = subscription.fetch(10, Duration.ofSeconds(2));
                for (Message message : messages) {
                    try {
                        taskHandler.handle(objectMapper.readValue(message.getData(), MAP));
                        message.ack();
                    } catch (Exception exception) {
                        LOGGER.error("NATS message processing failed on {}", subject, exception);
                        message.nak();
                    }
                }
            }
        } catch (Exception exception) {
            if (running) {
                LOGGER.error("NATS consumer stopped unexpectedly on {}", subject, exception);
            }
        }
    }

    @PreDestroy
    void close() {
        running = false;
        workers.shutdownNow();
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    private interface TaskHandler {
        void handle(Map<String, Object> task);
    }
}
