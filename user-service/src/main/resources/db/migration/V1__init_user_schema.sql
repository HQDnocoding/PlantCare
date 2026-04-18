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