-- Business schema is owned by the backend. The Phase 1 knowledge_* tables
-- and pipeline_schema_migrations remain owned by the Python data pipeline.

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL
        CONSTRAINT users_email_normalized CHECK (
            email = lower(btrim(email)) AND email <> ''
        ),
    password_hash varchar(255) NOT NULL,
    role varchar(16) NOT NULL
        CONSTRAINT users_role_valid CHECK (role IN ('STUDENT', 'ADMIN')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

CREATE TABLE chat_sessions (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    title varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX chat_sessions_user_updated_idx
    ON chat_sessions (user_id, updated_at DESC);

CREATE TABLE messages (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role varchar(16) NOT NULL
        CONSTRAINT messages_role_valid CHECK (role IN ('USER', 'ASSISTANT')),
    content text NOT NULL
        CONSTRAINT messages_content_not_blank CHECK (btrim(content) <> ''),
    citations jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT messages_citations_array CHECK (jsonb_typeof(citations) = 'array'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_user_citations_empty CHECK (
        role <> 'USER' OR citations = '[]'::jsonb
    )
);

CREATE INDEX messages_session_created_idx
    ON messages (session_id, created_at, id);

CREATE TABLE feedback (
    id uuid PRIMARY KEY,
    message_id uuid NOT NULL UNIQUE REFERENCES messages(id) ON DELETE CASCADE,
    rating varchar(16) NOT NULL
        CONSTRAINT feedback_rating_valid CHECK (rating IN ('HELPFUL', 'NOT_HELPFUL')),
    comment text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id uuid PRIMARY KEY,
    uploaded_by uuid REFERENCES users(id) ON DELETE SET NULL,
    title varchar(500) NOT NULL
        CONSTRAINT documents_title_not_blank CHECK (btrim(title) <> ''),
    original_filename varchar(500),
    source_url text,
    storage_key text,
    version varchar(100),
    content_sha256 char(64)
        CONSTRAINT documents_content_sha256_valid CHECK (
            content_sha256 ~ '^[0-9a-f]{64}$'
        ),
    knowledge_document_id char(64)
        CONSTRAINT documents_knowledge_id_valid CHECK (
            knowledge_document_id ~ '^[0-9a-f]{64}$'
        ),
    ingest_status varchar(16) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT documents_ingest_status_valid CHECK (
            ingest_status IN (
                'PENDING', 'PROCESSING', 'READY', 'NEEDS_REVIEW', 'FAILED'
            )
        ),
    ingest_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX documents_uploaded_created_idx
    ON documents (uploaded_by, created_at DESC);
CREATE INDEX documents_ingest_status_idx ON documents (ingest_status);
CREATE INDEX documents_knowledge_document_idx ON documents (knowledge_document_id);
