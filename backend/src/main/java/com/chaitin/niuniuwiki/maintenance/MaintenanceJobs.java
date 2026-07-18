package com.chaitin.niuniuwiki.maintenance;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.rag.RagClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提供 NiuniuWiki 后端的运维任务基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-04-28
 */
@Component
@Profile("consumer")
public class MaintenanceJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final RagClient rag;

    public MaintenanceJobs(MyBatisStore store, JsonMaps jsonMaps, RagClient rag) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.rag = rag;
    }

    @Scheduled(cron = "0 10 * * * *")
    public void aggregateHourlyStats() {
        try {
            store.update("""
                    INSERT INTO stat_page_hours(
                        kb_id, hour, ip_count, session_count, page_visit_count, conversation_count,
                        geo_count, conversation_distribution, hot_referer_host, hot_page, hot_os, hot_browser)
                    SELECT kb.id, date_trunc('hour', now()) - interval '1 hour',
                           count(DISTINCT sp.ip), count(DISTINCT sp.session_id), count(sp.id),
                           (SELECT count(*) FROM conversations c WHERE c.kb_id = kb.id
                             AND c.created_at >= date_trunc('hour', now()) - interval '1 hour'
                             AND c.created_at < date_trunc('hour', now())),
                           '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb
                      FROM knowledge_bases kb
                      LEFT JOIN stat_pages sp ON sp.kb_id = kb.id
                       AND sp.created_at >= date_trunc('hour', now()) - interval '1 hour'
                       AND sp.created_at < date_trunc('hour', now())
                     GROUP BY kb.id
                    ON CONFLICT (kb_id, hour) DO NOTHING
                    """);
        } catch (Exception exception) {
            LOGGER.error("Failed to aggregate hourly statistics", exception);
        }
    }

    @Scheduled(cron = "0 1 * * * *")
    public void removeOldRealtimeStats() {
        try {
            if (OffsetDateTime.now().getHour() == 0) {
                store.update("""
                        INSERT INTO node_stats(node_id, pv)
                        SELECT node_id, count(*) FROM stat_pages
                         WHERE created_at >= date_trunc('day', now()) - interval '1 day'
                           AND created_at < date_trunc('day', now()) AND node_id <> ''
                         GROUP BY node_id
                        ON CONFLICT (node_id) DO UPDATE SET pv = node_stats.pv + EXCLUDED.pv
                        """);
            }
            store.update("DELETE FROM stat_pages WHERE created_at < now() - interval '24 hours'");
        } catch (Exception exception) {
            LOGGER.error("Failed to clean realtime statistics", exception);
        }
    }

    @Scheduled(cron = "0 3 0 * * *")
    public void cleanHourlyStats() {
        store.update("DELETE FROM stat_page_hours WHERE hour < now() - interval '90 days'");
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanReleaseBackups() {
        store.update("DELETE FROM node_release_backup WHERE deleted_at < now() - interval '30 days'");
    }

    @Scheduled(cron = "0 26 * * * *")
    public void synchronizeRagStatus() {
        List<Map<String, Object>> knowledgeBases = store.query(
                "SELECT id, dataset_id FROM knowledge_bases", store.rowMapper());
        for (Map<String, Object> kb : knowledgeBases) {
            try {
                List<String> documentIds = store.query(
                        "SELECT DISTINCT nr.doc_id FROM node_releases nr JOIN nodes n ON n.id = nr.node_id "
                                + "WHERE n.kb_id = ? AND nr.doc_id <> ''",
                        (rs, rowNum) -> rs.getString(1), kb.get("id"));
                if (documentIds.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> document : rag.listDocuments(
                        String.valueOf(kb.get("dataset_id")), documentIds)) {
                    String documentId = String.valueOf(document.get("id"));
                    Map<String, Object> info = Map.of(
                            "status", String.valueOf(document.getOrDefault("status", "")),
                            "message", String.valueOf(document.getOrDefault("progress_msg", "")),
                            "synced_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
                    store.update("UPDATE nodes SET rag_info = ?::jsonb WHERE id IN "
                                    + "(SELECT node_id FROM node_releases WHERE doc_id = ?)",
                            jsonMaps.json(info), documentId);
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to synchronize RAG status for knowledge base {}", kb.get("id"), exception);
            }
        }
    }
}
