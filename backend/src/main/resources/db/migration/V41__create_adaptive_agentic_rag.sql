CREATE TABLE IF NOT EXISTS agentic_rag_runs (
    id                     text PRIMARY KEY,
    kb_id                  text NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    conversation_id        text NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_message_id        text NOT NULL REFERENCES conversation_messages(id) ON DELETE CASCADE,
    answer_message_id      text,
    question               text NOT NULL,
    mode                   text NOT NULL DEFAULT 'UNPLANNED',
    status                 text NOT NULL DEFAULT 'PLANNING',
    plan                   jsonb NOT NULL DEFAULT '{}'::jsonb,
    budget                 jsonb NOT NULL DEFAULT '{}'::jsonb,
    usage                  jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_iteration      integer NOT NULL DEFAULT 0,
    evidence_sufficient    boolean NOT NULL DEFAULT false,
    evidence_confidence    numeric(5,4) NOT NULL DEFAULT 0,
    clarification_question text NOT NULL DEFAULT '',
    stop_reason            text NOT NULL DEFAULT '',
    error_message          text NOT NULL DEFAULT '',
    started_at             timestamptz NOT NULL DEFAULT now(),
    completed_at           timestamptz,
    updated_at             timestamptz NOT NULL DEFAULT now(),
    created_at             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS agentic_rag_runs_kb_created_idx
    ON agentic_rag_runs(kb_id, created_at DESC);
CREATE INDEX IF NOT EXISTS agentic_rag_runs_status_idx
    ON agentic_rag_runs(status, updated_at);
CREATE INDEX IF NOT EXISTS agentic_rag_runs_conversation_idx
    ON agentic_rag_runs(conversation_id, created_at);

CREATE TABLE IF NOT EXISTS agentic_rag_steps (
    id          text PRIMARY KEY,
    run_id      text NOT NULL REFERENCES agentic_rag_runs(id) ON DELETE CASCADE,
    sequence_no integer NOT NULL,
    stage       text NOT NULL,
    status      text NOT NULL,
    iteration   integer NOT NULL DEFAULT 0,
    message     text NOT NULL DEFAULT '',
    input       jsonb NOT NULL DEFAULT '{}'::jsonb,
    output      jsonb NOT NULL DEFAULT '{}'::jsonb,
    metrics     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT agentic_rag_steps_run_sequence_uniq UNIQUE (run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS agentic_rag_steps_run_idx
    ON agentic_rag_steps(run_id, sequence_no);

CREATE TABLE IF NOT EXISTS agentic_rag_evidence (
    id           text PRIMARY KEY,
    run_id       text NOT NULL REFERENCES agentic_rag_runs(id) ON DELETE CASCADE,
    evidence_key text NOT NULL,
    node_id      text NOT NULL DEFAULT '',
    document_id  text NOT NULL DEFAULT '',
    title        text NOT NULL DEFAULT '',
    summary      text NOT NULL DEFAULT '',
    content      text NOT NULL DEFAULT '',
    url          text NOT NULL DEFAULT '',
    emoji        text NOT NULL DEFAULT '',
    score        numeric(10,6) NOT NULL DEFAULT 0,
    query        text NOT NULL DEFAULT '',
    hop          integer NOT NULL DEFAULT 1,
    metadata     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT agentic_rag_evidence_run_key_uniq UNIQUE (run_id, evidence_key)
);

CREATE INDEX IF NOT EXISTS agentic_rag_evidence_run_score_idx
    ON agentic_rag_evidence(run_id, score DESC);
CREATE INDEX IF NOT EXISTS agentic_rag_evidence_node_idx
    ON agentic_rag_evidence(run_id, node_id);
