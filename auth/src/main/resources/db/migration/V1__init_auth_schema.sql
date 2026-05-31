create table users(
    id UUID primary key default gen_random_uuid(),
    phone varchar(20) unique,
    email varchar(255),
    full_name varchar(255) not null,
    password_hash varchar(255) ,
    avatar_url varchar(500),
    role varchar(50) not null default 'FARMER',
    status varchar(50) not null default 'UNVERIFIED',
    created_at timestamp default now(),
    updated_at timestamp default now(),
    deleted_at timestamp default null
);


create table social_accounts(
    id UUID primary key default gen_random_uuid(),
    user_id UUID not null references users(id) on delete cascade,
    provider varchar(50) not null,
    provider_id varchar(255) not null,
    email varchar(255),
    created_at timestamp not null default now(),
    UNIQUE(provider, provider_id)
);

create table refresh_tokens(
    id UUID primary key default gen_random_uuid(),
    user_id UUID not null references users(id) on delete cascade,
    token_hash varchar(255) not null unique ,
    device_info varchar(500) not null,
    ip_address varchar(255) not null,
    created_at timestamp not null default now(),
    revoked_at timestamp default null,
    expires_at timestamp not null
);

create table otp_codes(
    id uuid primary key default gen_random_uuid(),
    phone varchar(20) not null,
    code_hash varchar(255) not null,
    purpose varchar(255) not null,
    created_at timestamp not null default now(),
    expires_at timestamp not null,
    verified_at timestamp default null,
    attempts int not null default 0
);

create index idx_users_phone on users(phone) where deleted_at is null;
create index idx_users_email on users(email) where deleted_at is null;

create index idx_refresh_token_hash on refresh_tokens(token_hash) where revoked_at is null;

create index idx_refresh_token_user on refresh_tokens(user_id);

create index idx_otp_code_phone_purpose on otp_codes(phone,purpose, created_at desc);

create index idx_social_user on social_accounts(user_id);

create or replace function fn_update_updated_at()
returns trigger as $$
begin
    new.updated_at=now();
    return new;
end;
$$ language plpgsql;

create trigger trf_users_updated_at
    before update on users
for each row
    execute function fn_update_updated_at();

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    retryable BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_events (retryable, status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID,
    method VARCHAR(20) NOT NULL,
    path VARCHAR(500) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT,
    request_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_records_expires_at_check CHECK (expires_at > created_at)
);

CREATE INDEX idx_idempotency_key_records ON idempotency_records(idempotency_key);
CREATE INDEX idx_idempotency_user_id ON idempotency_records(user_id);
CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at);
CREATE INDEX idx_idempotency_user_id_key ON idempotency_records(user_id, idempotency_key);

COMMENT ON TABLE idempotency_records IS 'Stores HTTP request idempotency data to prevent duplicate processing';
COMMENT ON COLUMN idempotency_records.idempotency_key IS 'Unique key for request deduplication (from X-Idempotency-Key or X-Correlation-Id header)';
COMMENT ON COLUMN idempotency_records.user_id IS 'User ID (nullable for unauthenticated endpoints like login)';
COMMENT ON COLUMN idempotency_records.response_status IS 'HTTP response status code (200, 201, 400, etc.)';
COMMENT ON COLUMN idempotency_records.response_body IS 'Cached response body for returning on retry';
COMMENT ON COLUMN idempotency_records.request_hash IS 'SHA-256 hash of request body for conflict detection';
COMMENT ON COLUMN idempotency_records.expires_at IS 'When this record expires (default 24 hours)';

CREATE TABLE processed_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_key VARCHAR(200) NOT NULL UNIQUE,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_messages_message_key ON processed_messages(message_key);