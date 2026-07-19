-- 问答引用以消息为边界，Agent 运行以租约为边界，异步发布使用事务 Outbox。

ALTER TABLE agentic_rag_runs
    ADD COLUMN IF NOT EXISTS access_context jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS owner_instance_id text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS lease_until timestamptz,
    ADD COLUMN IF NOT EXISTS run_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancel_requested boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS next_step_sequence integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS agentic_rag_runs_lease_idx
    ON agentic_rag_runs(status, lease_until)
    WHERE status IN ('PLANNING', 'RUNNING', 'GENERATING');

ALTER TABLE conversation_messages
    ADD COLUMN IF NOT EXISTS agent_run_id text;

CREATE UNIQUE INDEX IF NOT EXISTS conversation_messages_agent_run_uniq
    ON conversation_messages(agent_run_id)
    WHERE agent_run_id IS NOT NULL AND agent_run_id <> '';

CREATE TABLE IF NOT EXISTS message_citations (
    id                   text PRIMARY KEY,
    message_id           text NOT NULL REFERENCES conversation_messages(id) ON DELETE CASCADE,
    conversation_id      text NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    citation_no          integer NOT NULL,
    node_id              text NOT NULL,
    node_release_id      text NOT NULL DEFAULT '',
    knowledge_version_id text NOT NULL DEFAULT '',
    name                 text NOT NULL DEFAULT '',
    summary              text NOT NULL DEFAULT '',
    url                  text NOT NULL DEFAULT '',
    emoji                text NOT NULL DEFAULT '',
    recorded_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT message_citations_message_number_uniq UNIQUE(message_id, citation_no),
    CONSTRAINT message_citations_message_node_uniq UNIQUE(message_id, node_id)
);

CREATE INDEX IF NOT EXISTS message_citations_conversation_idx
    ON message_citations(conversation_id, recorded_at, citation_no);

INSERT INTO message_citations(
    id, message_id, conversation_id, citation_no, node_id, node_release_id,
    knowledge_version_id, name, summary, url, emoji, recorded_at)
SELECT md5(message.id || ':' || reference.ordinality),
       message.id,
       message.conversation_id,
       reference.ordinality::integer,
       COALESCE(reference.value->>'node_id', ''),
       COALESCE(reference.value->>'node_release_id', ''),
       COALESCE(reference.value->>'knowledge_version_id', ''),
       COALESCE(reference.value->>'name', ''),
       COALESCE(reference.value->>'summary', ''),
       COALESCE(reference.value->>'url', ''),
       COALESCE(reference.value->>'emoji', ''),
       message.created_at
  FROM conversation_messages message
 CROSS JOIN LATERAL jsonb_array_elements(COALESCE(message.info->'references', '[]'::jsonb))
      WITH ORDINALITY AS reference(value, ordinality)
 WHERE message.role = 'assistant'
   AND COALESCE(reference.value->>'node_id', '') <> ''
ON CONFLICT DO NOTHING;

-- 历史表保留为会话级兼容视图的数据源，但阻止并发写入产生重复记录。
DELETE FROM conversation_references duplicate
 USING conversation_references retained
 WHERE duplicate.ctid > retained.ctid
   AND duplicate.conversation_id = retained.conversation_id
   AND duplicate.node_id = retained.node_id;

CREATE UNIQUE INDEX IF NOT EXISTS conversation_references_conversation_node_uniq
    ON conversation_references(conversation_id, node_id)
    WHERE conversation_id IS NOT NULL AND node_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS integration_outbox (
    id            text PRIMARY KEY,
    subject       text NOT NULL,
    payload       jsonb NOT NULL,
    status        text NOT NULL DEFAULT 'PENDING',
    attempts      integer NOT NULL DEFAULT 0,
    available_at  timestamptz NOT NULL DEFAULT now(),
    claimed_at    timestamptz,
    published_at  timestamptz,
    error_message text NOT NULL DEFAULT '',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT integration_outbox_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS integration_outbox_pending_idx
    ON integration_outbox(status, available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
