--liquibase formatted sql

--changeset antiam:010-create-saml-assertion-schema
create table saml_assertions (
    id uuid primary key default gen_random_uuid(),
    assertion_id varchar(128) not null unique,
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    issuer varchar(512),
    audience varchar(512),
    acs_url varchar(1024),
    not_before timestamptz,
    not_on_or_after timestamptz,
    attributes text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_saml_assertions_user on saml_assertions(user_id);
create index idx_saml_assertions_application on saml_assertions(application_id);

--changeset antiam:011-create-cas-ticket-schema
create table cas_service_tickets (
    id uuid primary key default gen_random_uuid(),
    ticket_hash varchar(128) not null unique,
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    service_url varchar(1024) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_cas_service_tickets_user on cas_service_tickets(user_id);
create index idx_cas_service_tickets_application on cas_service_tickets(application_id);
