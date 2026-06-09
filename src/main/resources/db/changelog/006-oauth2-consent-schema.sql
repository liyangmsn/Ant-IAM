--liquibase formatted sql

--changeset antiam:009-create-oauth2-consent-schema
create table oauth_consents (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    client_id varchar(256) not null,
    scopes text,
    granted_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (client_id, user_id)
);

create index idx_oauth_consents_user on oauth_consents(user_id);
create index idx_oauth_consents_application on oauth_consents(application_id);
