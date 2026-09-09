-- CLI authentication sessions for Local Compute Agent
-- Stores hashed refresh tokens, enabling token rotation and revocation.

CREATE TABLE cli_sessions (
    id            VARCHAR(255) PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    device_name   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at    TIMESTAMP    NOT NULL,
    last_used_at  TIMESTAMP    NOT NULL DEFAULT now(),
    revoked       BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT fk_cli_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_cli_sessions_user_id ON cli_sessions(user_id);
CREATE INDEX idx_cli_sessions_token_hash ON cli_sessions(token_hash);
