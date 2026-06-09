--liquibase formatted sql

--changeset antiam:001-create-pgcrypto runInTransaction:false
create extension if not exists pgcrypto;

--changeset antiam:002-create-core-iam-schema
create table organizations (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    parent_id uuid references organizations(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table user_accounts (
    id uuid primary key default gen_random_uuid(),
    username varchar(128) not null unique,
    display_name varchar(256) not null,
    email varchar(320),
    mobile varchar(64),
    status varchar(32) not null,
    organization_id uuid references organizations(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table permissions (
    id uuid primary key default gen_random_uuid(),
    code varchar(160) not null unique,
    name varchar(256) not null,
    description varchar(1024),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table roles (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    description varchar(1024),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table user_groups (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table applications (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    protocol varchar(32) not null,
    login_url varchar(1024),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table identity_sources (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    type varchar(64) not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table audit_events (
    id uuid primary key default gen_random_uuid(),
    actor varchar(128) not null,
    action varchar(128) not null,
    target_type varchar(128) not null,
    target_id varchar(128) not null,
    detail text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table group_roles (
    group_id uuid not null references user_groups(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (group_id, role_id)
);

create table user_group_members (
    user_id uuid not null references user_accounts(id) on delete cascade,
    group_id uuid not null references user_groups(id) on delete cascade,
    primary key (user_id, group_id)
);

create table user_roles (
    user_id uuid not null references user_accounts(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

create table application_roles (
    application_id uuid not null references applications(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (application_id, role_id)
);

create index idx_user_accounts_org on user_accounts(organization_id);
create index idx_audit_events_target on audit_events(target_type, target_id);
