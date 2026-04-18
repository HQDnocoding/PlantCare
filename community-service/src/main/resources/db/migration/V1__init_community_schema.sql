-- V1__init_community_schema.sql

-- ── Posts ────────────────────────────────────────────────────────────────────
CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL,                  
    content         TEXT NOT NULL,
    image_urls      TEXT[]          DEFAULT '{}',
    image_paths     TEXT[]          DEFAULT '{}',   
    upvote_count    INT NOT NULL    DEFAULT 0,
    downvote_count  INT NOT NULL    DEFAULT 0,
    comment_count   INT NOT NULL    DEFAULT 0,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT posts_content_not_empty CHECK (char_length(trim(content)) > 0),
    CONSTRAINT posts_max_images CHECK (array_length(image_urls, 1) <= 4 OR image_urls IS NULL)
);

CREATE INDEX idx_posts_author_id  ON posts(author_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_posts_created_at ON posts(created_at DESC) WHERE is_deleted = FALSE;
-- Index cho hot score sort: (upvote - downvote) / age — dùng partial index
CREATE INDEX idx_posts_hot        ON posts(created_at DESC, upvote_count DESC) WHERE is_deleted = FALSE;

-- ── Tags ─────────────────────────────────────────────────────────────────────
CREATE TABLE post_tags (
    post_id     UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag         VARCHAR(32) NOT NULL,
    PRIMARY KEY (post_id, tag)
);

CREATE INDEX idx_post_tags_tag ON post_tags(lower(tag));

-- ── Votes ────────────────────────────────────────────────────────────────────
CREATE TABLE votes (
    user_id     UUID NOT NULL,
    target_id   UUID NOT NULL,
    target_type VARCHAR(10) NOT NULL,   -- 'POST' | 'COMMENT'
    value       SMALLINT NOT NULL,      -- +1 | -1
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, target_id, target_type),
    CONSTRAINT votes_value_check CHECK (value IN (1, -1))
);

CREATE INDEX idx_votes_target ON votes(target_id, target_type);

-- ── Comments ─────────────────────────────────────────────────────────────────
CREATE TABLE comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL,
    -- NULL = top-level comment, NOT NULL = reply (chỉ 1 cấp)
    parent_id       UUID REFERENCES comments(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    upvote_count    INT NOT NULL DEFAULT 0,
    downvote_count  INT NOT NULL DEFAULT 0,
    reply_count     INT NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()

);

CREATE INDEX idx_comments_post_id   ON comments(post_id, created_at) WHERE is_deleted = FALSE;
CREATE INDEX idx_comments_parent_id ON comments(parent_id) WHERE parent_id IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX idx_comments_author_id ON comments(author_id) WHERE is_deleted = FALSE;

-- ── Triggers: updated_at ─────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_votes_updated_at
    BEFORE UPDATE ON votes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ── Triggers: denormalized counts ────────────────────────────────────────────

-- Vote count trên post
CREATE OR REPLACE FUNCTION sync_post_vote_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.target_type = 'POST' THEN
            UPDATE posts SET
                upvote_count   = upvote_count   + CASE WHEN NEW.value =  1 THEN 1 ELSE 0 END,
                downvote_count = downvote_count + CASE WHEN NEW.value = -1 THEN 1 ELSE 0 END
            WHERE id = NEW.target_id;
        END IF;

    ELSIF TG_OP = 'UPDATE' THEN
        -- Đổi vote: cộng mới, trừ cũ
        IF NEW.target_type = 'POST' THEN
            UPDATE posts SET
                upvote_count   = upvote_count
                    + CASE WHEN NEW.value =  1 THEN 1 ELSE 0 END
                    - CASE WHEN OLD.value =  1 THEN 1 ELSE 0 END,
                downvote_count = downvote_count
                    + CASE WHEN NEW.value = -1 THEN 1 ELSE 0 END
                    - CASE WHEN OLD.value = -1 THEN 1 ELSE 0 END
            WHERE id = NEW.target_id;
        END IF;

    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.target_type = 'POST' THEN
            UPDATE posts SET
                upvote_count   = GREATEST(0, upvote_count   - CASE WHEN OLD.value =  1 THEN 1 ELSE 0 END),
                downvote_count = GREATEST(0, downvote_count - CASE WHEN OLD.value = -1 THEN 1 ELSE 0 END)
            WHERE id = OLD.target_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_post_votes
    AFTER INSERT OR UPDATE OR DELETE ON votes
    FOR EACH ROW EXECUTE FUNCTION sync_post_vote_counts();

-- Vote count trên comment
CREATE OR REPLACE FUNCTION sync_comment_vote_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.target_type = 'COMMENT' THEN
            UPDATE comments SET
                upvote_count   = upvote_count   + CASE WHEN NEW.value =  1 THEN 1 ELSE 0 END,
                downvote_count = downvote_count + CASE WHEN NEW.value = -1 THEN 1 ELSE 0 END
            WHERE id = NEW.target_id;
        END IF;

    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.target_type = 'COMMENT' THEN
            UPDATE comments SET
                upvote_count   = upvote_count
                    + CASE WHEN NEW.value =  1 THEN 1 ELSE 0 END
                    - CASE WHEN OLD.value =  1 THEN 1 ELSE 0 END,
                downvote_count = downvote_count
                    + CASE WHEN NEW.value = -1 THEN 1 ELSE 0 END
                    - CASE WHEN OLD.value = -1 THEN 1 ELSE 0 END
            WHERE id = NEW.target_id;
        END IF;

    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.target_type = 'COMMENT' THEN
            UPDATE comments SET
                upvote_count   = GREATEST(0, upvote_count   - CASE WHEN OLD.value =  1 THEN 1 ELSE 0 END),
                downvote_count = GREATEST(0, downvote_count - CASE WHEN OLD.value = -1 THEN 1 ELSE 0 END)
            WHERE id = OLD.target_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_comment_votes
    AFTER INSERT OR UPDATE OR DELETE ON votes
    FOR EACH ROW EXECUTE FUNCTION sync_comment_vote_counts();

-- Comment count trên post
CREATE OR REPLACE FUNCTION sync_post_comment_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.is_deleted = FALSE THEN
        UPDATE posts SET comment_count = comment_count + 1 WHERE id = NEW.post_id;
        -- Nếu là reply thì tăng reply_count của parent comment
        IF NEW.parent_id IS NOT NULL THEN
            UPDATE comments SET reply_count = reply_count + 1 WHERE id = NEW.parent_id;
        END IF;

    ELSIF TG_OP = 'UPDATE' THEN
        -- Soft delete vừa xảy ra
        IF OLD.is_deleted = FALSE AND NEW.is_deleted = TRUE THEN
            UPDATE posts SET comment_count = GREATEST(0, comment_count - 1) WHERE id = NEW.post_id;
            IF NEW.parent_id IS NOT NULL THEN
                UPDATE comments SET reply_count = GREATEST(0, reply_count - 1) WHERE id = NEW.parent_id;
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_comment_count
    AFTER INSERT OR UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION sync_post_comment_count();


-- Hàm kiểm tra độ sâu của comment (Chỉ cho phép tối đa 1 cấp reply)
CREATE OR REPLACE FUNCTION check_comment_depth()
RETURNS TRIGGER AS $$
BEGIN
    -- Nếu là reply (parent_id không NULL)
    IF NEW.parent_id IS NOT NULL THEN
        -- Kiểm tra xem parent có phải là một reply khác không
        -- Nếu parent cũng có parent_id => nghĩa là đang reply vào cấp 2 => Báo lỗi
        IF EXISTS (
            SELECT 1 FROM comments 
            WHERE id = NEW.parent_id AND parent_id IS NOT NULL
        ) THEN
            RAISE EXCEPTION 'Chỉ cho phép reply vào comment cấp 1 (REPLY_DEPTH_EXCEEDED)';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Tạo trigger chạy trước khi chèn comment mới
CREATE TRIGGER trg_check_comment_depth
    BEFORE INSERT ON comments
    FOR EACH ROW EXECUTE FUNCTION check_comment_depth();