-- Milestone 3/4 — Local Compute Jobs
-- Tracks inference jobs dispatched to the local compute agent.

CREATE TABLE local_jobs (
    id            VARCHAR(255) PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL,
    worker_id     VARCHAR(255),
    status        VARCHAR(32)  NOT NULL,
    model         VARCHAR(255) NOT NULL,
    prompt        TEXT         NOT NULL,
    response      TEXT,
    error         TEXT,
    tokens_used   INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMP,
    completed_at  TIMESTAMP,
    CONSTRAINT fk_local_jobs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_local_jobs_user_id ON local_jobs(user_id);
CREATE INDEX idx_local_jobs_worker_id ON local_jobs(worker_id);
CREATE INDEX idx_local_jobs_status ON local_jobs(status);
