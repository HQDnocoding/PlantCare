-- Migration V3: Add exponential backoff retry fields to outbox_events

ALTER TABLE outbox_events
ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
ADD COLUMN last_retry_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN in_dlq BOOLEAN NOT NULL DEFAULT FALSE;

-- Update indexes to support exponential backoff retry query
DROP INDEX IF EXISTS idx_outbox_unprocessed;

CREATE INDEX idx_outbox_retryable 
    ON outbox_events(processed, in_dlq, retry_count, created_at, last_retry_at) 
    WHERE processed = FALSE AND in_dlq = FALSE;

CREATE INDEX idx_outbox_dlq 
    ON outbox_events(in_dlq, created_at) 
    WHERE in_dlq = TRUE;
