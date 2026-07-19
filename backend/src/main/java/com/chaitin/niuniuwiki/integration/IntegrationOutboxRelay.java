package com.chaitin.niuniuwiki.integration;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 可靠转发事务 Outbox，并通过消费者账本实现端到端至少一次投递。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Component
@Profile("!consumer")
public class IntegrationOutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationOutboxRelay.class);

    private final IntegrationOutboxService outboxService;
    private final VectorTaskPublisher publisher;
    private final JsonMaps jsonMaps;

    public IntegrationOutboxRelay(
            IntegrationOutboxService outboxService,
            VectorTaskPublisher publisher,
            JsonMaps jsonMaps
    ) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.jsonMaps = jsonMaps;
    }

    @Scheduled(fixedDelayString = "${niuniu-wiki.messaging.outbox-poll-ms:1000}")
    public void relay() {
        for (Map<String, Object> message : outboxService.claimBatch(25)) {
            String id = String.valueOf(message.get("id"));
            try {
                publisher.publishNow(String.valueOf(message.get("subject")),
                        jsonMaps.jsonMap(message.get("payload")));
                outboxService.published(id);
            } catch (RuntimeException exception) {
                outboxService.failed(id, exception.getMessage());
                LOGGER.warn("Outbox publish failed: id={}, subject={}", id, message.get("subject"));
            }
        }
    }
}
