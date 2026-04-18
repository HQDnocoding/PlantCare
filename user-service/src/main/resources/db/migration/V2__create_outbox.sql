CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic       VARCHAR(100)  NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload     TEXT          NOT NULL,
    processed   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Index để poller query nhanh
CREATE INDEX idx_outbox_unprocessed 
    ON outbox_events(processed, created_at) 
    WHERE processed = FALSE;