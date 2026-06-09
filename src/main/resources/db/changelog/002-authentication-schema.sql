--liquibase formatted sql

--changeset antiam:003-create-authentication-schema
create table user_credentials (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    type varchar(32) not null,
    secret_hash varchar(512) not null,
    temporary boolean not null,
    expires_at timestamptz,
    last_used_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (user_id, type)
);

create table mfa_factors (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    type varchar(32) not null,
    name varchar(256) not null,
    secret text,
    verified boolean not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table authentication_policies (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    priority integer not null,
    mfa_required boolean not null,
    password_min_length integer not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_user_credentials_user on user_credentials(user_id);
create index idx_mfa_factors_user on mfa_factors(user_id);
