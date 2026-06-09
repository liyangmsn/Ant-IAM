--liquibase formatted sql

--changeset antiam:013-create-identity-source-connector-schema
create table identity_source_connectors (
    id uuid primary key default gen_random_uuid(),
    identity_source_id uuid not null unique references identity_sources(id) on delete cascade,
    configuration text,
    secret_ref text,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

--changeset antiam:014-create-identity-sync-job-schema
create table identity_sync_jobs (
    id uuid primary key default gen_random_uuid(),
    identity_source_id uuid not null references identity_sources(id) on delete cascade,
    name varchar(256) not null,
    mode varchar(32) not null,
    cron_expression varchar(128),
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_identity_sync_jobs_source on identity_sync_jobs(identity_source_id);

--changeset antiam:015-create-identity-sync-run-schema
create table identity_sync_runs (
    id uuid primary key default gen_random_uuid(),
    sync_job_id uuid not null references identity_sync_jobs(id) on delete cascade,
    status varchar(32) not null,
    started_at timestamptz,
    finished_at timestamptz,
    users_created integer not null,
    users_updated integer not null,
    groups_created integer not null,
    groups_updated integer not null,
    message text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_identity_sync_runs_job on identity_sync_runs(sync_job_id, created_at desc);
