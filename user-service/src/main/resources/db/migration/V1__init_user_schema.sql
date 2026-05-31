CREATE TABLE user_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,   -- ref tới auth-service.users.id
    display_name    VARCHAR(64) NOT NULL,
    bio             VARCHAR(300),
    avatar_url      TEXT,                   -- Firebase Storage URL
    avatar_path     TEXT,                   -- Firebase Storage path (để xóa file cũ)
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
 
-- Index cho lookup thường xuyên
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_display_name ON user_profiles(display_name);
 
-- Trigger tự update updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
 
CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
 
-- Follow relationships
CREATE TABLE follows (
    follower_id     UUID NOT NULL,   -- user_id của người follow
    following_id    UUID NOT NULL,   -- user_id của người được follow
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT no_self_follow CHECK (follower_id != following_id)
);
 
CREATE INDEX idx_follows_follower    ON follows(follower_id);
CREATE INDEX idx_follows_following   ON follows(following_id);
 
-- Denormalized follow counts (tránh COUNT(*) mỗi lần query)
CREATE TABLE follow_counts (
    user_id         UUID PRIMARY KEY,
    follower_count  INT NOT NULL DEFAULT 0,
    following_count INT NOT NULL DEFAULT 0
);
 
-- Tự tạo follow_counts khi insert user_profile
CREATE OR REPLACE FUNCTION init_follow_counts()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO follow_counts(user_id) VALUES (NEW.user_id)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
 
CREATE TRIGGER trg_init_follow_counts
    AFTER INSERT ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION init_follow_counts();

CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    retryable    BOOLEAN NOT NULL DEFAULT TRUE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT now(),
    retry_count  INT NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status
    ON outbox_events(retryable, status, created_at);

CREATE TABLE idempotency_records (
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

CREATE TABLE processed_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_key VARCHAR(200) NOT NULL UNIQUE,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_messages_message_key ON processed_messages(message_key);