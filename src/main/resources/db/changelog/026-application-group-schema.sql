--liquibase formatted sql

--changeset antiam:033-create-application-group-schema
create table application_groups (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    description text,
    built_in boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

alter table applications add column group_id uuid references application_groups(id) on delete set null;

create index idx_applications_group on applications(group_id);
