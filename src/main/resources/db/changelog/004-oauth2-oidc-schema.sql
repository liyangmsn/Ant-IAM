--liquibase formatted sql

--changeset antiam:006-create-oauth2-oidc-schema
create table oauth_authorization_codes (
    id uuid primary key default gen_random_uuid(),
    code_hash varchar(128) not null unique,
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    client_id varchar(256) not null,
    redirect_uri varchar(1024) not null,
    scopes text,
    state varchar(512),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table oauth_access_tokens (
    id uuid primary key default gen_random_uuid(),
    token_hash varchar(128) not null unique,
    id_token_hash varchar(128) not null unique,
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    client_id varchar(256) not null,
    scopes text,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_oauth_authorization_codes_client on oauth_authorization_codes(client_id);
create index idx_oauth_access_tokens_user on oauth_access_tokens(user_id);
create index idx_oauth_access_tokens_client on oauth_access_tokens(client_id);
