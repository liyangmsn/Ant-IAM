--liquibase formatted sql

--changeset antiam:020-create-tenant-schema
create table tenants (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    domain varchar(256) not null unique,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

--changeset antiam:021-create-tenant-setting-schema
create table tenant_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id) on delete cascade,
    setting_key varchar(160) not null,
    category varchar(128) not null,
    value_type varchar(32) not null,
    setting_value text,
    description varchar(1024),
    sensitive boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (tenant_id, setting_key)
);

create index idx_tenant_settings_category on tenant_settings(tenant_id, category, setting_key);
