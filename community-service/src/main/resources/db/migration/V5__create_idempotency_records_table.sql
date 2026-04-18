-- V5__create_idempotency_records_table.sql
-- Creates the idempotency_records table for storing HTTP request idempotency data

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

-- Index for fast lookup by idempotency key
CREATE INDEX IF NOT EXISTS idx_idempotency_key ON idempotency_records(idempotency_key);

-- Index for user-based queries
CREATE INDEX IF NOT EXISTS idx_user_id ON idempotency_records(user_id);

-- Index for cleanup (query by expiry)
CREATE INDEX IF NOT EXISTS idx_expires_at ON idempotency_records(expires_at);

-- Index for combined queries (user + key), mainly for authenticated endpoints
CREATE INDEX IF NOT EXISTS idx_user_id_idempotency_key ON idempotency_records(user_id, idempotency_key);

-- Comment
COMMENT ON TABLE idempotency_records IS 'Stores HTTP request idempotency data to prevent duplicate processing';
COMMENT ON COLUMN idempotency_records.idempotency_key IS 'Unique key for request deduplication (from X-Idempotency-Key or X-Correlation-Id header)';
COMMENT ON COLUMN idempotency_records.response_status IS 'HTTP response status code (200, 201, 400, etc.)';
COMMENT ON COLUMN idempotency_records.response_body IS 'Cached response body for returning on retry';
COMMENT ON COLUMN idempotency_records.request_hash IS 'SHA-256 hash of request body for conflict detection';
COMMENT ON COLUMN idempotency_records.expires_at IS 'When this record expires (default 24 hours)';
