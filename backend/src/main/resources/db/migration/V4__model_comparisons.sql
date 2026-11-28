CREATE TABLE model_comparisons (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    user_message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    assistant_message_id uuid REFERENCES messages(id) ON DELETE SET NULL,
    user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    question text NOT NULL,
    model_a varchar(100) NOT NULL,
    model_b varchar(100) NOT NULL,
    answer_a text NOT NULL,
    answer_b text NOT NULL,
    sources_a jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT model_comparisons_sources_a_array CHECK (jsonb_typeof(sources_a) = 'array'),
    sources_b jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT model_comparisons_sources_b_array CHECK (jsonb_typeof(sources_b) = 'array'),
    winner varchar(16)
        CONSTRAINT model_comparisons_winner_valid CHECK (
            winner IS NULL OR winner IN ('A', 'B', 'TIE', 'BOTH_BAD')
        ),
    voted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX model_comparisons_session_idx
    ON model_comparisons (session_id, created_at DESC);
CREATE INDEX model_comparisons_winner_idx
    ON model_comparisons (winner);
