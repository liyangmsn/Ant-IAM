--liquibase formatted sql

--changeset antiam:019-create-system-setting-schema
create table system_settings (
    id uuid primary key default gen_random_uuid(),
    setting_key varchar(160) not null unique,
    category varchar(128) not null,
    value_type varchar(32) not null,
    setting_value text,
    description varchar(1024),
    sensitive boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_system_settings_category on system_settings(category, setting_key);
