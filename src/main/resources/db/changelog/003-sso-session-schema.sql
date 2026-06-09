--liquibase formatted sql

--changeset antiam:004-create-application-sso-schema
create table application_sso_configs (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null unique references applications(id) on delete cascade,
    protocol varchar(32) not null,
    client_id varchar(256),
    client_secret_hash text,
    redirect_uris text,
    scopes text,
    saml_entity_id varchar(512),
    saml_acs_url varchar(1024),
    cas_service_url varchar(1024),
    jwt_audience varchar(512),
    form_login_template text,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table application_assignments (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid references user_accounts(id) on delete cascade,
    group_id uuid references user_groups(id) on delete cascade,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_application_assignment_subject check (
        (user_id is not null and group_id is null)
        or (user_id is null and group_id is not null)
    )
);

create unique index uq_application_assignments_user
    on application_assignments(application_id, user_id)
    where user_id is not null;

create unique index uq_application_assignments_group
    on application_assignments(application_id, group_id)
    where group_id is not null;

--changeset antiam:005-create-authentication-session-schema
create table authentication_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references user_accounts(id) on delete set null,
    application_id uuid references applications(id) on delete set null,
    protocol varchar(32) not null,
    session_index varchar(256) not null,
    ip_address varchar(128),
    user_agent varchar(1024),
    expires_at timestamptz,
    ended_at timestamptz,
    active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table authentication_events (
    id uuid primary key default gen_random_uuid(),
    session_id uuid references authentication_sessions(id) on delete set null,
    user_id uuid references user_accounts(id) on delete set null,
    application_id uuid references applications(id) on delete set null,
    type varchar(64) not null,
    ip_address varchar(128),
    user_agent varchar(1024),
    detail text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_authentication_sessions_user_active on authentication_sessions(user_id, active);
create index idx_authentication_events_created_at on authentication_events(created_at desc);
