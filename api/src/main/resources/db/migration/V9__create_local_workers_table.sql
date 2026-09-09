-- Persistent identity for Local Compute Agent workers.
-- Survives server restarts. Linked to a user account.

CREATE TABLE local_workers (
    id            VARCHAR(255) PRIMARY KEY,
    worker_id     VARCHAR(255) NOT NULL UNIQUE,
    user_id       VARCHAR(255) NOT NULL,
    device_name   VARCHAR(255),
    cli_version   VARCHAR(50),
    last_seen_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT fk_local_workers_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_local_workers_user_id ON local_workers(user_id);
CREATE INDEX idx_local_workers_worker_id ON local_workers(worker_id);
