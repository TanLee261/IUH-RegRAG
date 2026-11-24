-- Only hashes of refresh JWTs are stored; issued access JWTs are stateless
-- except when their jti is explicitly revoked at logout.
CREATE TABLE auth_refresh_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX auth_refresh_tokens_user_idx ON auth_refresh_tokens (user_id, revoked_at);

CREATE TABLE auth_revoked_access_tokens (
    id uuid PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NOT NULL DEFAULT now()
);
