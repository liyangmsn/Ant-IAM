--liquibase formatted sql

--changeset antiam:041-create-application-permission-schema
create table application_permissions (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null references applications(id) on delete cascade,
    code varchar(160) not null,
    name varchar(256) not null,
    description varchar(1024),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_application_permissions_code unique (application_id, code)
);

create table application_permission_roles (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null references applications(id) on delete cascade,
    code varchar(128) not null,
    name varchar(256) not null,
    description varchar(1024),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_application_permission_roles_code unique (application_id, code)
);

create table application_permission_role_permissions (
    role_id uuid not null references application_permission_roles(id) on delete cascade,
    permission_id uuid not null references application_permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table application_permission_role_members (
    id uuid primary key default gen_random_uuid(),
    role_id uuid not null references application_permission_roles(id) on delete cascade,
    user_id uuid references user_accounts(id) on delete cascade,
    group_id uuid references user_groups(id) on delete cascade,
    organization_id uuid references organizations(id) on delete cascade,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_application_permission_role_members_subject check (
        (case when user_id is null then 0 else 1 end)
        + (case when group_id is null then 0 else 1 end)
        + (case when organization_id is null then 0 else 1 end) = 1)
);

create unique index uk_app_permission_role_members_user on application_permission_role_members(role_id, user_id) where user_id is not null;
create unique index uk_app_permission_role_members_group on application_permission_role_members(role_id, group_id) where group_id is not null;
create unique index uk_app_permission_role_members_org on application_permission_role_members(role_id, organization_id) where organization_id is not null;
create index idx_app_permission_role_members_user on application_permission_role_members(user_id);
create index idx_app_permission_role_members_group on application_permission_role_members(group_id);
create index idx_app_permission_role_members_org on application_permission_role_members(organization_id);
