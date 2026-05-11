-- Create idempotency records table for scan-service
-- Stores cached responses for idempotent requests
-- Used to prevent duplicate processing on retries

CREATE TABLE IF NOT EXISTS idempotency_records
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    user_id         UUID         NOT NULL,
    method          VARCHAR(10)  NOT NULL,
    path            VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_body   TEXT         NOT NULL,
    response_status INTEGER      NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL
);

-- Indexes for query optimization
CREATE INDEX idx_user_method_path ON idempotency_records (user_id, method, path);
CREATE INDEX idx_request_hash ON idempotency_records (request_hash);
CREATE INDEX idx_expires_at ON idempotency_records (expires_at);
