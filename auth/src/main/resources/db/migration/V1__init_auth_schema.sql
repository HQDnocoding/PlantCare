create table users(
    id UUID primary key default gen_random_uuid(),
    phone varchar(20) unique,
    email varchar(255) unique,
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

create table idempotency_keys(
    key varchar(255) primary key,
    user_id UUID,
    request_hash varchar(255) not null,
    response_status int not null,
    response_body text not null,
    created_at timestamp not null default now(),
    expires_at timestamp not null
);

create index idx_users_phone on users(phone) where deleted_at is null;
create index idx_users_email on users(email) where deleted_at is null;

create index idx_refresh_token_hash on refresh_tokens(token_hash) where revoked_at is null;

create index idx_refresh_token_user on refresh_tokens(user_id);

create index idx_otp_code_phone_purpose on otp_codes(phone,purpose, created_at desc);

create index idx_social_user on social_accounts(user_id);

create index idx_idempotency_key on idempotency_keys(expires_at);

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