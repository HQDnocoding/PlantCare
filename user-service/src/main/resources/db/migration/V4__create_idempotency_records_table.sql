-- V4__create_idempotency_records_table.sql

CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    method VARCHAR(20) NOT NULL,
    path VARCHAR(500) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT,
    request_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_records_expires_at_check CHECK (expires_at > created_at)
);

CREATE INDEX idx_idempotency_key ON idempotency_records(idempotency_key);
CREATE INDEX idx_user_id ON idempotency_records(user_id);
CREATE INDEX idx_expires_at ON idempotency_records(expires_at);
CREATE INDEX idx_user_id_idempotency_key ON idempotency_records(user_id, idempotency_key);

COMMENT ON TABLE idempotency_records IS 'Stores HTTP request idempotency data to prevent duplicate processing';
COMMENT ON COLUMN idempotency_records.idempotency_key IS 'Unique key for request deduplication';
COMMENT ON COLUMN idempotency_records.response_body IS 'Cached response body for returning on retry';
COMMENT ON COLUMN idempotency_records.request_hash IS 'SHA-256 hash of request body for conflict detection';
