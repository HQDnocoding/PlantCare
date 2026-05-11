-- V2__fix_email_unique.sql
-- Date: 2026-03-28
-- Description: Remove unique constraint on email to allow null emails
--              from social login (Facebook may not return email)
--              Replace with partial unique index

ALTER TABLE users DROP CONSTRAINT users_email_key;

DROP INDEX IF EXISTS idx_users_email;

CREATE UNIQUE INDEX idx_users_email_unique
    ON users(email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;