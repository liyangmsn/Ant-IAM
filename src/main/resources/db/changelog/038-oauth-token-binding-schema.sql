--liquibase formatted sql

--changeset antiam:038-oauth-access-token-refresh-binding
alter table oauth_access_tokens
    add column if not exists refresh_token_id uuid;

--changeset antiam:038-oauth-access-token-refresh-binding-index
create index if not exists idx_oauth_access_tokens_refresh_token_id
    on oauth_access_tokens (refresh_token_id);

--changeset antiam:038-oauth-access-token-optional-id-token
alter table oauth_access_tokens
    alter column id_token_hash drop not null;
