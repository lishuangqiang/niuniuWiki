package com.chaitin.niuniuwiki.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.model.ModelGateway;
import com.chaitin.niuniuwiki.compiler.KnowledgeEventLedger;
import com.chaitin.niuniuwiki.knowledgebase.KnowledgeBaseDtos;
import com.chaitin.niuniuwiki.integration.IntegrationOutboxService;
import com.chaitin.niuniuwiki.knowledgebase.KnowledgeBaseService;
import com.chaitin.niuniuwiki.node.NodeDtos;
import com.chaitin.niuniuwiki.node.NodeService;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import com.chaitin.niuniuwiki.rag.RagClient;
import com.chaitin.niuniuwiki.rag.VectorTaskPublisher;
import com.chaitin.niuniuwiki.security.AuthContext;
import com.chaitin.niuniuwiki.security.AuthPrincipal;
import com.chaitin.niuniuwiki.security.AuthService;
import com.chaitin.niuniuwiki.security.JwtService;
import com.chaitin.niuniuwiki.share.PublicContentService;
import com.chaitin.niuniuwiki.share.ShareAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.mock;

class DatabaseMigrationTest {

    @Test
    void migratesEmptyPostgresSchemaThroughVersion43() throws IOException {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();

            var result = flyway.migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(43);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                    Integer.class)).isGreaterThan(20);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM system_settings WHERE key = 'model_setting_mode'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'knowledge_versions'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'knowledge_artifacts'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'agentic_rag_runs'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'agentic_rag_evidence'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'knowledge_shadow_indexes'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'knowledge_change_events'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables "
                            + "WHERE table_name = 'conversation_reference_snapshots'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'message_citations'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'integration_outbox'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_name = 'agentic_rag_runs' AND column_name = 'lease_until'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_name = 'conversation_messages' AND column_name = 'agent_run_id'",
                    Integer.class)).isEqualTo(1);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonMaps maps = new JsonMaps(objectMapper);
            JdbcMaps store = createStore(dataSource, maps);
            LegacyDataMigrationRunner legacy = new LegacyDataMigrationRunner(
                    store,
                    maps,
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                    mock(VectorTaskPublisher.class));
            legacy.run(new DefaultApplicationArguments(new String[0]));
            legacy.run(new DefaultApplicationArguments(new String[0]));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM migrations", Integer.class)).isEqualTo(5);

            IntegrationOutboxService outbox = new IntegrationOutboxService(store, maps);
            String outboxId = outbox.enqueue("test.subject", Map.of("kind", "migration-test"));
            assertThat(outbox.claimBatch(10)).singleElement().satisfies(message -> {
                assertThat(message.get("id")).isEqualTo(outboxId);
                assertThat(message.get("subject")).isEqualTo("test.subject");
            });
            outbox.published(outboxId);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM integration_outbox WHERE id = ?", String.class, outboxId))
                    .isEqualTo("PUBLISHED");

            verifyCoreKnowledgeBaseFlow(jdbc, store, maps);
        }
    }

    private void verifyCoreKnowledgeBaseFlow(JdbcTemplate jdbc, JdbcMaps store, JsonMaps maps) {
        AuthService auth = new AuthService(store, mock(JwtService.class));
        RagClient rag = mock(RagClient.class);
        VectorTaskPublisher tasks = mock(VectorTaskPublisher.class);
        org.mockito.Mockito.when(rag.createDataset()).thenReturn("dataset-test");
        AuthContext.set(new AuthPrincipal("admin-test", false, "", "", "admin"));
        try {
            KnowledgeBaseService knowledgeBases = new KnowledgeBaseService(store, maps, auth, rag, tasks);
            String kbId = knowledgeBases.create(new KnowledgeBaseDtos.CreateRequest(
                    "测试知识库", List.of(8000), List.of(), "", "", List.of("wiki.example.test")));
            assertThat(jdbc.queryForObject(
                    "SELECT settings ->> 'home_page_setting' FROM apps WHERE kb_id = ? AND type = 1",
                    String.class, kbId)).isEqualTo("custom");
            String navId = jdbc.queryForObject("SELECT id FROM navs WHERE kb_id = ?", String.class, kbId);
            NodeService nodes = new NodeService(store, maps, auth, tasks, mock(ModelGateway.class));
            String nodeId = nodes.create(new NodeDtos.CreateRequest(
                    kbId, navId, "", 2, "Java 重构", "正文", "📚", "摘要", "md", null));
            String releaseId = knowledgeBases.createRelease(new KnowledgeBaseDtos.ReleaseRequest(
                    kbId, "首次发布", "v1", List.of(nodeId)));

            assertThat(releaseId).isNotBlank();
            assertThat(nodes.detail(kbId, nodeId, "raw").get("name")).isEqualTo("Java 重构");
            assertThat(nodes.detail(kbId, nodeId, "raw").get("rag_info")).isInstanceOf(Map.class);
            assertThat(nodes.detail(kbId, nodeId, "raw").get("permissions")).isInstanceOf(Map.class);
            PublicContentService publicContent = new PublicContentService(
                    store, maps, new ShareAccessService(store, maps));
            HttpSession publicSession = mock(HttpSession.class);
            assertThat(publicContent.nodeGroups(kbId, publicSession)).hasSize(1);
            String firstNodeReleaseId = jdbc.queryForObject(
                    "SELECT node_release_id FROM kb_release_node_releases WHERE release_id = ?",
                    String.class, releaseId);
            nodes.update(new NodeDtos.UpdateRequest(
                    nodeId, kbId, "Java 重构", "第二版正文", "📚", "新版摘要", null, "md", navId));
            String secondReleaseId = knowledgeBases.createRelease(new KnowledgeBaseDtos.ReleaseRequest(
                    kbId, "第二次发布", "v2", List.of(nodeId)));
            String secondNodeReleaseId = jdbc.queryForObject(
                    "SELECT node_release_id FROM kb_release_node_releases WHERE release_id = ?",
                    String.class, secondReleaseId);
            assertThat(publicContent.historicalNodeDetail(
                    kbId, nodeId, firstNodeReleaseId, publicSession).get("content")).isEqualTo("正文");

            KnowledgeEventLedger ledger = new KnowledgeEventLedger(store, maps);
            Map<String, Object> newestEvent = Map.of(
                    "event_id", "event-newest", "event_sequence", 200L, "kb_id", kbId,
                    "node_id", nodeId, "node_release_id", secondNodeReleaseId, "action", "upsert");
            KnowledgeEventLedger.Claim newestClaim = ledger.claim(newestEvent);
            assertThat(newestClaim.process()).isTrue();
            ledger.complete(newestClaim, "hash-v2", "perm-v2", false);
            assertThat(ledger.claim(newestEvent).process()).isFalse();
            KnowledgeEventLedger.Claim staleClaim = ledger.claim(Map.of(
                    "event_id", "event-stale", "event_sequence", 100L, "kb_id", kbId,
                    "node_id", nodeId, "node_release_id", firstNodeReleaseId, "action", "upsert"));
            assertThat(staleClaim.process()).isFalse();
            assertThat(staleClaim.status()).isEqualTo("STALE");

            nodes.editPermission(new NodeDtos.PermissionEditRequest(
                    kbId, List.of(nodeId), Map.of(
                            "visible", "open", "visitable", "closed", "answerable", "closed"),
                    List.of(), List.of(), List.of()));
            assertThatThrownBy(() -> publicContent.historicalNodeDetail(
                    kbId, nodeId, firstNodeReleaseId, publicSession))
                    .hasMessageContaining("Permission Denied");

            knowledgeBases.delete(kbId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM knowledge_bases WHERE id = ?", Integer.class, kbId)).isZero();
        } finally {
            AuthContext.clear();
        }
    }

    private JdbcMaps createStore(DataSource dataSource, JsonMaps maps) {
        return new JdbcMaps(new JdbcTemplate(dataSource), maps);
    }
}
