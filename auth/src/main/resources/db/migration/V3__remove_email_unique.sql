-- V3__remove_email_unique.sql
-- Date: 2026-03-29
-- Description: Remove unique constraint on email to allow multiple accounts
--              with same email (e.g. same email on Google and Facebook)

DROP INDEX IF EXISTS idx_users_email_unique;

CREATE INDEX idx_users_email
    ON users(email)
    WHERE deleted_at IS NULL;