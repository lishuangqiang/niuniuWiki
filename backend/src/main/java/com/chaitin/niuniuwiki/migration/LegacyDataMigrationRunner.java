package com.chaitin.niuniuwiki.migration;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 移植原 Go 后端中的五个数据迁移，并继续以 migrations 表作为兼容性数据源。
 *
 * @author 程序员牛肉
 * @since 2026-04-13
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyDataMigrationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyDataMigrationRunner.class);

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final TransactionTemplate transactions;
    private final VectorTaskPublisher vectorTasks;

    public LegacyDataMigrationRunner(
            MyBatisStore store,
            JsonMaps jsonMaps,
            TransactionTemplate transactions,
            VectorTaskPublisher vectorTasks
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.transactions = transactions;
        this.vectorTasks = vectorTasks;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate("0001_migrate_node_version", this::migrateNodeVersions);
        migrate("0002_create_bot_auth", this::createBotAuth);
        migrate("0003_fix_group_ids", this::fixGroupIds);
        migrate("0004_update_node_status_unreleased", this::updateUnreleasedStatus);
        migrate("0005_create_first_nav_tabs", this::createFirstNavTabs);
    }

    private void migrate(String name, Runnable migration) {
        transactions.executeWithoutResult(status -> {
            int claimed = store.update(
                    "INSERT INTO migrations(name, executed_at) VALUES (?, now()) ON CONFLICT (name) DO NOTHING",
                    name);
            if (claimed == 0) {
                return;
            }
            LOGGER.info("Running legacy data migration {}", name);
            migration.run();
        });
    }

    private void migrateNodeVersions() {
        List<Map<String, Object>> knowledgeBases = store.query(
                "SELECT id FROM knowledge_bases ORDER BY created_at", store.rowMapper());
        for (Map<String, Object> kb : knowledgeBases) {
            String kbId = value(kb.get("id"));
            List<Map<String, Object>> nodes = store.query(
                    "SELECT * FROM nodes WHERE kb_id = ? ORDER BY position", store.rowMapper(), kbId);
            for (Map<String, Object> node : nodes) {
                String nodeId = value(node.get("id"));
                String releaseId = UUID.randomUUID().toString();
                store.update(
                        "INSERT INTO node_releases(id, kb_id, node_id, doc_id, type, name, meta, content, position, "
                                + "parent_id, publisher_id, editor_id, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, '', ?, now(), now())",
                        releaseId, kbId, nodeId, "", node.get("type"), node.get("name"),
                        jsonMaps.json(node.get("meta")), value(node.get("content")), node.get("position"),
                        value(node.get("parent_id")), value(node.get("editor_id")));
                vectorTasks.publishNow(Map.of(
                        "kb_id", kbId, "node_release_id", releaseId, "node_id", nodeId,
                        "doc_id", "", "action", "upsert", "group_ids", List.of()));
            }
            String kbReleaseId = UUID.randomUUID().toString();
            store.update(
                    "INSERT INTO kb_releases(id, kb_id, tag, message, publisher_id, created_at) "
                            + "VALUES (?, ?, 'init', 'release all old nodes', '', now())",
                    kbReleaseId, kbId);
            snapshot(kbId, kbReleaseId);
        }
        List<Map<String, Object>> oldDocuments = store.query(
                "SELECT kb_id, doc_id FROM nodes WHERE doc_id IS NOT NULL AND doc_id <> ''", store.rowMapper());
        for (Map<String, Object> document : oldDocuments) {
            vectorTasks.publishNow(Map.of(
                    "kb_id", value(document.get("kb_id")), "node_release_id", "", "node_id", "",
                    "doc_id", value(document.get("doc_id")), "action", "delete", "group_ids", List.of()));
        }
    }

    private void createBotAuth() {
        Map<Integer, BotType> types = Map.of(
                2, new BotType("widget", "widget_bot_settings", "is_open", "网页挂件机器人"),
                3, new BotType("dingtalk_bot", "", "dingtalk_bot_is_enabled", "钉钉机器人"),
                4, new BotType("feishu_bot", "", "feishu_bot_is_enabled", "飞书机器人"),
                5, new BotType("wechat_bot", "", "wechat_app_is_enabled", "企业微信机器人"),
                6, new BotType("wechat_service_bot", "", "wechat_service_is_enabled", "企业微信客服"),
                7, new BotType("discord_bot", "", "discord_bot_is_enabled", "Discord 机器人"),
                8, new BotType("wechat_official_account", "", "wechat_official_account_is_enabled", "微信公众号"));
        List<Map<String, Object>> apps = store.query(
                "SELECT id, kb_id, type, settings FROM apps WHERE type BETWEEN 2 AND 8", store.rowMapper());
        for (Map<String, Object> app : apps) {
            BotType type = types.get(number(app.get("type")));
            Map<String, Object> settings = jsonMaps.jsonMap(app.get("settings"));
            boolean enabled;
            if (type.container().isBlank()) {
                enabled = Boolean.TRUE.equals(settings.get(type.flag()));
            } else {
                enabled = Boolean.TRUE.equals(jsonMaps.jsonMap(settings.get(type.container())).get(type.flag()));
            }
            if (!enabled) {
                continue;
            }
            String kbId = value(app.get("kb_id"));
            Integer existing = store.queryForObject(
                    "SELECT count(*) FROM auths WHERE kb_id = ? AND source_type = ?",
                    Integer.class, kbId, type.source());
            if (existing != null && existing > 0) {
                continue;
            }
            store.update(
                    "INSERT INTO auths(user_info, union_id, ip, kb_id, source_type, last_login_time, created_at, updated_at) "
                            + "VALUES (?::jsonb, ?, '', ?, ?, now(), now(), now())",
                    jsonMaps.json(Map.of("username", type.label())),
                    "bot_" + app.get("id") + "_" + type.source(), kbId, type.source());
        }
    }

    private void fixGroupIds() {
        List<Map<String, Object>> documents = store.query("""
                SELECT DISTINCT ON (nr.node_id) nr.kb_id, nr.doc_id
                  FROM node_releases nr JOIN nodes n ON n.id = nr.node_id
                 WHERE n.permissions->>'answerable' = 'closed' AND nr.doc_id <> ''
                 ORDER BY nr.node_id, nr.updated_at DESC
                """, store.rowMapper());
        documents.forEach(document -> vectorTasks.publishNow(Map.of(
                "kb_id", value(document.get("kb_id")), "node_release_id", "", "node_id", "",
                "doc_id", value(document.get("doc_id")), "action", "update_group_ids", "group_ids", List.of())));
    }

    private void updateUnreleasedStatus() {
        store.update("UPDATE nodes SET status = 0 WHERE status = 1 "
                + "AND NOT EXISTS (SELECT 1 FROM node_releases nr WHERE nr.node_id = nodes.id)");
    }

    private void createFirstNavTabs() {
        List<Map<String, Object>> knowledgeBases = store.query(
                "SELECT id, name FROM knowledge_bases ORDER BY created_at", store.rowMapper());
        for (Map<String, Object> kb : knowledgeBases) {
            String kbId = value(kb.get("id"));
            String navId = UUID.randomUUID().toString();
            store.update(
                    "INSERT INTO navs(id, name, kb_id, position, created_at, updated_at) VALUES (?, ?, ?, 0, now(), now())",
                    navId, kb.get("name"), kbId);
            store.update("UPDATE nodes SET nav_id = ? WHERE kb_id = ?", navId, kbId);
            List<Map<String, Object>> releases = store.query(
                    "SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1",
                    store.rowMapper(), kbId);
            if (releases.isEmpty()) {
                continue;
            }
            String releaseId = value(releases.getFirst().get("id"));
            store.update(
                    "INSERT INTO nav_releases(id, nav_id, release_id, kb_id, name, position, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, 0, now())",
                    UUID.randomUUID().toString(), navId, releaseId, kbId, kb.get("name"));
            store.update(
                    "UPDATE kb_release_node_releases SET nav_id = ? WHERE kb_id = ? AND release_id = ?",
                    navId, kbId, releaseId);
        }
    }

    private void snapshot(String kbId, String releaseId) {
        List<Map<String, Object>> releases = store.query(
                "SELECT DISTINCT ON (node_id) id, node_id FROM node_releases "
                        + "WHERE kb_id = ? ORDER BY node_id, updated_at DESC",
                store.rowMapper(), kbId);
        for (Map<String, Object> nodeRelease : releases) {
            store.update(
                    "INSERT INTO kb_release_node_releases(id, kb_id, release_id, node_id, node_release_id, nav_id, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, '', now())",
                    UUID.randomUUID().toString(), kbId, releaseId,
                    nodeRelease.get("node_id"), nodeRelease.get("id"));
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record BotType(String source, String container, String flag, String label) {
    }
}
