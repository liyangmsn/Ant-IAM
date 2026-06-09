--liquibase formatted sql

--changeset antiam:007-add-oauth2-pkce-fields
alter table oauth_authorization_codes
    add column code_challenge varchar(256),
    add column code_challenge_method varchar(16);

--changeset antiam:008-create-oauth2-refresh-token-schema
create table oauth_refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    token_hash varchar(128) not null unique,
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    client_id varchar(256) not null,
    scopes text,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    last_used_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_oauth_refresh_tokens_user on oauth_refresh_tokens(user_id);
create index idx_oauth_refresh_tokens_client on oauth_refresh_tokens(client_id);
