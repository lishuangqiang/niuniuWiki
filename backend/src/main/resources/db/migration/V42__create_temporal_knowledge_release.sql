-- 双时态知识、影子索引、发布门禁与可靠事件账本。

ALTER TABLE knowledge_artifacts
    ADD COLUMN IF NOT EXISTS identity_key text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS valid_from timestamptz,
    ADD COLUMN IF NOT EXISTS valid_to timestamptz,
    ADD COLUMN IF NOT EXISTS recorded_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS source_version text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS knowledge_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS supersedes_id text REFERENCES knowledge_artifacts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS source_permissions jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE knowledge_artifacts a
   SET identity_key = CASE WHEN a.identity_key = '' THEN a.artifact_key ELSE a.identity_key END,
       source_version = CASE
           WHEN a.source_version = '' AND cardinality(a.source_release_ids) > 0 THEN a.source_release_ids[1]
           ELSE a.source_version END,
       knowledge_version = CASE
           WHEN a.knowledge_version = 0 THEN v.version_no ELSE a.knowledge_version END,
       recorded_at = COALESCE(a.recorded_at, a.created_at),
       valid_from = COALESCE(a.valid_from, v.published_at, a.created_at),
       status = CASE
           WHEN s.active_version_id = a.version_id THEN 'EFFECTIVE'
           WHEN v.status = 'PUBLISHED' THEN 'EXPIRED'
           ELSE 'CANDIDATE' END
  FROM knowledge_versions v
  LEFT JOIN knowledge_compiler_states s ON s.kb_id = v.kb_id
 WHERE v.id = a.version_id;

UPDATE knowledge_artifacts a
   SET valid_to = COALESCE(a.valid_to, next_version.published_at, now())
  FROM knowledge_versions current_version
  LEFT JOIN LATERAL (
      SELECT published_at
        FROM knowledge_versions candidate
       WHERE candidate.kb_id = current_version.kb_id
         AND candidate.version_no > current_version.version_no
         AND candidate.published_at IS NOT NULL
       ORDER BY candidate.version_no
       LIMIT 1
  ) next_version ON true
 WHERE a.version_id = current_version.id
   AND a.status = 'EXPIRED'
   AND a.valid_to IS NULL;

CREATE INDEX IF NOT EXISTS knowledge_artifacts_temporal_idx
    ON knowledge_artifacts(kb_id, identity_key, valid_from DESC, recorded_at DESC);
CREATE INDEX IF NOT EXISTS knowledge_artifacts_supersedes_idx
    ON knowledge_artifacts(supersedes_id);
CREATE INDEX IF NOT EXISTS knowledge_artifacts_effective_idx
    ON knowledge_artifacts(kb_id, version_id, status) WHERE status IN ('EFFECTIVE', 'CONFLICT');

ALTER TABLE knowledge_versions
    ADD COLUMN IF NOT EXISTS shadow_index_id text,
    ADD COLUMN IF NOT EXISTS validation_status text NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS validation_report jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS activated_at timestamptz,
    ADD COLUMN IF NOT EXISTS rollback_of_version_id text REFERENCES knowledge_versions(id) ON DELETE SET NULL;

ALTER TABLE knowledge_compiler_states
    ADD COLUMN IF NOT EXISTS previous_version_id text,
    ADD COLUMN IF NOT EXISTS active_index_id text,
    ADD COLUMN IF NOT EXISTS previous_index_id text,
    ADD COLUMN IF NOT EXISTS last_reconciled_at timestamptz;

CREATE TABLE IF NOT EXISTS knowledge_shadow_indexes (
    id                text PRIMARY KEY,
    kb_id             text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id        text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    status            text NOT NULL DEFAULT 'BUILDING',
    manifest_hash     text NOT NULL DEFAULT '',
    document_count    integer NOT NULL DEFAULT 0,
    permissions_hash  text NOT NULL DEFAULT '',
    validation_report jsonb NOT NULL DEFAULT '{}'::jsonb,
    built_at          timestamptz,
    validated_at      timestamptz,
    activated_at      timestamptz,
    retired_at        timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_shadow_indexes_version_uniq UNIQUE(version_id)
);

CREATE INDEX IF NOT EXISTS knowledge_shadow_indexes_kb_status_idx
    ON knowledge_shadow_indexes(kb_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_index_documents (
    id                 text PRIMARY KEY,
    index_id           text NOT NULL REFERENCES knowledge_shadow_indexes(id) ON DELETE CASCADE,
    kb_id              text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id         text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    artifact_id        text NOT NULL REFERENCES knowledge_artifacts(id) ON DELETE CASCADE,
    identity_key       text NOT NULL,
    title              text NOT NULL DEFAULT '',
    summary            text NOT NULL DEFAULT '',
    content            text NOT NULL DEFAULT '',
    content_hash       text NOT NULL,
    source_node_ids    text[] NOT NULL DEFAULT '{}',
    source_release_ids text[] NOT NULL DEFAULT '{}',
    permissions        jsonb NOT NULL DEFAULT '{}'::jsonb,
    search_vector      tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(content, ''))
    ) STORED,
    created_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_index_documents_artifact_uniq UNIQUE(index_id, artifact_id)
);

CREATE INDEX IF NOT EXISTS knowledge_index_documents_search_idx
    ON knowledge_index_documents USING gin(search_vector);
CREATE INDEX IF NOT EXISTS knowledge_index_documents_sources_idx
    ON knowledge_index_documents USING gin(source_node_ids);

CREATE TABLE IF NOT EXISTS knowledge_source_snapshots (
    id                 text PRIMARY KEY,
    kb_id              text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id         text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    node_id            text NOT NULL,
    node_release_id    text NOT NULL,
    source_version     text NOT NULL DEFAULT '',
    name               text NOT NULL DEFAULT '',
    nav_id             text NOT NULL DEFAULT '',
    parent_id          text NOT NULL DEFAULT '',
    content_hash       text NOT NULL DEFAULT '',
    permission_hash    text NOT NULL DEFAULT '',
    permissions        jsonb NOT NULL DEFAULT '{}'::jsonb,
    recorded_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_source_snapshots_version_node_uniq UNIQUE(version_id, node_id)
);

CREATE INDEX IF NOT EXISTS knowledge_source_snapshots_lookup_idx
    ON knowledge_source_snapshots(kb_id, node_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_version_changes (
    id                  text PRIMARY KEY,
    kb_id               text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id          text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    previous_version_id text,
    node_id             text NOT NULL,
    change_type         text NOT NULL,
    before_snapshot     jsonb NOT NULL DEFAULT '{}'::jsonb,
    after_snapshot      jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_version_changes_version_idx
    ON knowledge_version_changes(kb_id, version_id, change_type);

CREATE TABLE IF NOT EXISTS knowledge_release_validations (
    id          text PRIMARY KEY,
    kb_id       text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id  text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    check_code  text NOT NULL,
    severity    text NOT NULL,
    status      text NOT NULL,
    message     text NOT NULL,
    metrics     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_release_validations_version_idx
    ON knowledge_release_validations(kb_id, version_id, severity, status);

CREATE TABLE IF NOT EXISTS knowledge_version_activations (
    id                     text PRIMARY KEY,
    kb_id                  text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id             text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    previous_version_id    text,
    valid_from             timestamptz NOT NULL DEFAULT now(),
    valid_to               timestamptz,
    reason                 text NOT NULL DEFAULT 'publish',
    activated_by           text NOT NULL DEFAULT '',
    created_at             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_version_activations_temporal_idx
    ON knowledge_version_activations(kb_id, valid_from DESC, valid_to);

CREATE TABLE IF NOT EXISTS knowledge_change_events (
    id                text PRIMARY KEY,
    event_id          text NOT NULL UNIQUE,
    kb_id             text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    aggregate_id      text NOT NULL DEFAULT '',
    sequence_no       bigint NOT NULL DEFAULT 0,
    event_type        text NOT NULL,
    source_version    text NOT NULL DEFAULT '',
    payload           jsonb NOT NULL DEFAULT '{}'::jsonb,
    status            text NOT NULL DEFAULT 'PENDING',
    attempts          integer NOT NULL DEFAULT 0,
    error_message     text NOT NULL DEFAULT '',
    recorded_at       timestamptz NOT NULL DEFAULT now(),
    processed_at      timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS knowledge_change_events_source_uniq
    ON knowledge_change_events(kb_id, event_type, source_version)
    WHERE source_version <> '';
CREATE INDEX IF NOT EXISTS knowledge_change_events_pending_idx
    ON knowledge_change_events(status, recorded_at) WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE IF NOT EXISTS knowledge_source_states (
    kb_id               text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    aggregate_id        text NOT NULL,
    last_sequence_no    bigint NOT NULL DEFAULT 0,
    last_event_id       text NOT NULL DEFAULT '',
    last_source_version text NOT NULL DEFAULT '',
    content_hash        text NOT NULL DEFAULT '',
    permission_hash     text NOT NULL DEFAULT '',
    tombstoned          boolean NOT NULL DEFAULT false,
    last_seen_at        timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(kb_id, aggregate_id)
);

CREATE TABLE IF NOT EXISTS knowledge_reconciliation_runs (
    id             text PRIMARY KEY,
    kb_id          text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    status         text NOT NULL DEFAULT 'RUNNING',
    expected_count integer NOT NULL DEFAULT 0,
    actual_count   integer NOT NULL DEFAULT 0,
    missing_count  integer NOT NULL DEFAULT 0,
    stale_count    integer NOT NULL DEFAULT 0,
    report         jsonb NOT NULL DEFAULT '{}'::jsonb,
    compile_run_id text,
    started_at     timestamptz NOT NULL DEFAULT now(),
    completed_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_reconciliation_runs_kb_idx
    ON knowledge_reconciliation_runs(kb_id, created_at DESC);

ALTER TABLE conversation_references
    ADD COLUMN IF NOT EXISTS node_release_id text,
    ADD COLUMN IF NOT EXISTS knowledge_version_id text,
    ADD COLUMN IF NOT EXISTS recorded_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS conversation_reference_snapshots (
    id                   text PRIMARY KEY,
    conversation_id      text NOT NULL,
    message_id           text NOT NULL,
    kb_id                text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    node_id              text NOT NULL,
    node_release_id      text NOT NULL DEFAULT '',
    knowledge_version_id text NOT NULL DEFAULT '',
    name                 text NOT NULL DEFAULT '',
    content              text NOT NULL DEFAULT '',
    meta                 jsonb NOT NULL DEFAULT '{}'::jsonb,
    permissions          jsonb NOT NULL DEFAULT '{}'::jsonb,
    recorded_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_reference_snapshots_message_node_uniq UNIQUE(message_id, node_id)
);

CREATE INDEX IF NOT EXISTS conversation_reference_snapshots_lookup_idx
    ON conversation_reference_snapshots(kb_id, node_id, node_release_id, recorded_at DESC);

-- 为已有知识版本生成可回滚的影子索引与激活区间。
INSERT INTO knowledge_shadow_indexes(
    id, kb_id, version_id, status, manifest_hash, document_count, built_at, validated_at, activated_at, created_at)
SELECT 'legacy-index-' || v.id, v.kb_id, v.id,
       CASE WHEN s.active_version_id = v.id THEN 'ACTIVE' ELSE 'RETIRED' END,
       v.manifest_hash,
       (SELECT count(*) FROM knowledge_artifacts a WHERE a.version_id = v.id AND a.artifact_key <> '__index__'),
       COALESCE(v.published_at, v.created_at), COALESCE(v.published_at, v.created_at),
       CASE WHEN s.active_version_id = v.id THEN COALESCE(v.published_at, v.created_at) END,
       v.created_at
  FROM knowledge_versions v
  LEFT JOIN knowledge_compiler_states s ON s.kb_id = v.kb_id
 WHERE v.status = 'PUBLISHED'
ON CONFLICT(version_id) DO NOTHING;

INSERT INTO knowledge_index_documents(
    id, index_id, kb_id, version_id, artifact_id, identity_key, title, summary, content,
    content_hash, source_node_ids, source_release_ids, permissions, created_at)
SELECT 'legacy-doc-' || a.id, i.id, a.kb_id, a.version_id, a.id,
       COALESCE(NULLIF(a.identity_key, ''), a.artifact_key), a.title, a.summary, a.content,
       a.content_hash, a.source_node_ids, a.source_release_ids, a.source_permissions, a.created_at
  FROM knowledge_artifacts a
  JOIN knowledge_shadow_indexes i ON i.version_id = a.version_id
 WHERE a.artifact_key <> '__index__' AND a.status <> 'CONFLICT'
ON CONFLICT(index_id, artifact_id) DO NOTHING;

UPDATE knowledge_versions v
   SET shadow_index_id = i.id,
       validation_status = CASE WHEN v.status = 'PUBLISHED' THEN 'PASSED' ELSE v.validation_status END,
       activated_at = CASE WHEN EXISTS (
           SELECT 1 FROM knowledge_compiler_states s WHERE s.active_version_id = v.id
       ) THEN COALESCE(v.published_at, v.created_at) END
  FROM knowledge_shadow_indexes i
 WHERE i.version_id = v.id;

UPDATE knowledge_compiler_states s
   SET previous_version_id = v.previous_version_id,
       active_index_id = i.id
  FROM knowledge_versions v
  LEFT JOIN knowledge_shadow_indexes i ON i.version_id = v.id
 WHERE v.id = s.active_version_id;

INSERT INTO knowledge_version_activations(
    id, kb_id, version_id, previous_version_id, valid_from, reason, activated_by, created_at)
SELECT 'legacy-activation-' || v.id, v.kb_id, v.id, v.previous_version_id,
       COALESCE(v.published_at, v.created_at), 'legacy_publish', v.created_by, v.created_at
  FROM knowledge_versions v
  JOIN knowledge_compiler_states s ON s.active_version_id = v.id
ON CONFLICT(id) DO NOTHING;
