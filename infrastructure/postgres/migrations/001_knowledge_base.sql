CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS pipeline_schema_migrations (
    version integer PRIMARY KEY,
    description text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ingest_runs (
    id uuid PRIMARY KEY,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    status text NOT NULL CHECK (status IN ('running', 'succeeded')),
    embedding_model text NOT NULL,
    embedding_dimension integer NOT NULL CHECK (embedding_dimension > 0),
    chunk_count integer CHECK (chunk_count >= 0)
);

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id char(64) PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    title text NOT NULL CHECK (btrim(title) <> ''),
    source text NOT NULL CHECK (source ~ '^https?://'),
    document_group text NOT NULL CHECK (btrim(document_group) <> ''),
    so_qd text,
    publication_year integer CHECK (publication_year BETWEEN 1000 AND 9999),
    legal_status text NOT NULL CHECK (btrim(legal_status) <> ''),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id char(64) PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    document_id char(64) NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL CHECK (chunk_index >= 0),
    part_index integer NOT NULL CHECK (part_index >= 0),
    chunk_type text NOT NULL CHECK (
        chunk_type IN ('article', 'clause', 'preamble', 'appendix', 'free_text')
    ),
    text text NOT NULL CHECK (btrim(text) <> ''),
    title text NOT NULL CHECK (btrim(title) <> ''),
    source text NOT NULL CHECK (source ~ '^https?://'),
    document_group text NOT NULL CHECK (btrim(document_group) <> ''),
    dieu text,
    dieu_title text,
    khoan text,
    diem text,
    so_qd text,
    publication_year integer CHECK (publication_year BETWEEN 1000 AND 9999),
    page_start integer CHECK (page_start >= 1),
    page_end integer CHECK (page_end >= page_start),
    tags text[] NOT NULL DEFAULT '{}',
    legal_status text NOT NULL CHECK (btrim(legal_status) <> ''),
    embedding vector(1024) NOT NULL,
    embedding_model text NOT NULL CHECK (btrim(embedding_model) <> ''),
    ingest_run_id uuid NOT NULL REFERENCES ingest_runs(id),
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' ||
            coalesce('Điều ' || dieu, '') || ' ' ||
            coalesce('Khoản ' || khoan, '') || ' ' ||
            text
        )
    ) STORED,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index),
    CHECK ((page_start IS NULL) = (page_end IS NULL))
);

CREATE INDEX IF NOT EXISTS knowledge_chunks_document_idx
    ON knowledge_chunks (document_id);
CREATE INDEX IF NOT EXISTS knowledge_chunks_group_idx
    ON knowledge_chunks (document_group);
CREATE INDEX IF NOT EXISTS knowledge_chunks_legal_status_idx
    ON knowledge_chunks (legal_status);
CREATE INDEX IF NOT EXISTS knowledge_chunks_search_idx
    ON knowledge_chunks USING gin (search_vector);
CREATE INDEX IF NOT EXISTS knowledge_chunks_embedding_hnsw_idx
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);

INSERT INTO pipeline_schema_migrations (version, description)
VALUES (1, 'knowledge documents, chunks, pgvector and search indexes')
ON CONFLICT (version) DO NOTHING;
