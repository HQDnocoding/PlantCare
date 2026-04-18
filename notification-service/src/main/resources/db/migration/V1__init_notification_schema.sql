CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    type        VARCHAR(50) NOT NULL,  -- FOLLOW, COMMENT, VOTE, REPLY
    title       VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    actor_id    UUID,                  -- người thực hiện hành động
    actor_name  VARCHAR(255),
    target_id   UUID,                  -- postId, commentId...
    target_type VARCHAR(50),           -- POST, COMMENT
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE fcm_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    token       VARCHAR(500) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_notifications_user_id
    ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notifications_unread
    ON notifications(user_id, is_read)
    WHERE is_read = FALSE;

CREATE INDEX idx_fcm_tokens_user_id ON fcm_tokens(user_id);