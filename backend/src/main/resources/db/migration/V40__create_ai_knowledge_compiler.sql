CREATE TABLE IF NOT EXISTS knowledge_versions (
    id                  text PRIMARY KEY,
    kb_id               text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_no          bigint NOT NULL,
    previous_version_id text,
    run_id              text NOT NULL,
    status              text NOT NULL DEFAULT 'BUILDING',
    manifest_hash       text NOT NULL DEFAULT '',
    stats               jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by          text NOT NULL DEFAULT '',
    published_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_versions_kb_version_uniq UNIQUE (kb_id, version_no)
);

CREATE INDEX IF NOT EXISTS knowledge_versions_kb_created_idx
    ON knowledge_versions(kb_id, created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_compiler_runs (
    id                   text PRIMARY KEY,
    kb_id                text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    trigger_type         text NOT NULL DEFAULT 'manual',
    status               text NOT NULL DEFAULT 'QUEUED',
    requested_node_ids   text[] NOT NULL DEFAULT '{}',
    requested_release_ids text[] NOT NULL DEFAULT '{}',
    force_full           boolean NOT NULL DEFAULT false,
    version_id           text,
    stats                jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_message        text NOT NULL DEFAULT '',
    created_by           text NOT NULL DEFAULT '',
    started_at           timestamptz,
    completed_at         timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_compiler_runs_kb_created_idx
    ON knowledge_compiler_runs(kb_id, created_at DESC);
CREATE INDEX IF NOT EXISTS knowledge_compiler_runs_status_idx
    ON knowledge_compiler_runs(status, created_at);

CREATE TABLE IF NOT EXISTS knowledge_compiler_states (
    kb_id             text PRIMARY KEY REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    active_version_id text,
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_artifacts (
    id                 text PRIMARY KEY,
    kb_id              text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id         text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    artifact_key       text NOT NULL,
    type               text NOT NULL DEFAULT 'reference',
    title              text NOT NULL,
    summary            text NOT NULL DEFAULT '',
    content            text NOT NULL DEFAULT '',
    facts              jsonb NOT NULL DEFAULT '{}'::jsonb,
    entities           jsonb NOT NULL DEFAULT '{}'::jsonb,
    source_node_ids    text[] NOT NULL DEFAULT '{}',
    source_release_ids text[] NOT NULL DEFAULT '{}',
    content_hash       text NOT NULL,
    confidence         numeric(5,4) NOT NULL DEFAULT 0,
    status             text NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_artifacts_version_key_uniq UNIQUE (version_id, artifact_key)
);

CREATE INDEX IF NOT EXISTS knowledge_artifacts_kb_version_idx
    ON knowledge_artifacts(kb_id, version_id);
CREATE INDEX IF NOT EXISTS knowledge_artifacts_source_nodes_idx
    ON knowledge_artifacts USING gin(source_node_ids);

CREATE TABLE IF NOT EXISTS knowledge_dependencies (
    id                text PRIMARY KEY,
    kb_id             text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id        text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    artifact_id       text NOT NULL REFERENCES knowledge_artifacts(id) ON DELETE CASCADE,
    source_node_id    text NOT NULL,
    source_release_id text NOT NULL,
    dependency_type   text NOT NULL DEFAULT 'derived_from',
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_dependencies_source_idx
    ON knowledge_dependencies(kb_id, source_node_id, source_release_id);

CREATE TABLE IF NOT EXISTS knowledge_conflicts (
    id           text PRIMARY KEY,
    kb_id        text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id   text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    artifact_id  text,
    conflict_key text NOT NULL,
    kind         text NOT NULL,
    severity     text NOT NULL DEFAULT 'WARNING',
    claim_a      jsonb NOT NULL DEFAULT '{}'::jsonb,
    claim_b      jsonb NOT NULL DEFAULT '{}'::jsonb,
    status       text NOT NULL DEFAULT 'OPEN',
    resolution   text NOT NULL DEFAULT '',
    created_at   timestamptz NOT NULL DEFAULT now(),
    resolved_at  timestamptz
);

CREATE INDEX IF NOT EXISTS knowledge_conflicts_kb_version_idx
    ON knowledge_conflicts(kb_id, version_id, status);

CREATE TABLE IF NOT EXISTS knowledge_lint_issues (
    id          text PRIMARY KEY,
    kb_id       text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    version_id  text NOT NULL REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    artifact_id text,
    rule_code   text NOT NULL,
    severity    text NOT NULL,
    message     text NOT NULL,
    details     jsonb NOT NULL DEFAULT '{}'::jsonb,
    status      text NOT NULL DEFAULT 'OPEN',
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS knowledge_lint_issues_kb_version_idx
    ON knowledge_lint_issues(kb_id, version_id, status);
