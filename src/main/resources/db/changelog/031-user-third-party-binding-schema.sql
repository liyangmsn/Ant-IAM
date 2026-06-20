--liquibase formatted sql

--changeset antiam:039-create-user-third-party-binding-schema
create table if not exists user_third_party_bindings (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    provider_key varchar(128) not null,
    provider varchar(64) not null,
    subject varchar(256) not null,
    union_id varchar(256),
    display_name varchar(256),
    email varchar(320),
    mobile varchar(64),
    avatar_url varchar(1024),
    raw_profile text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists uk_user_third_party_bindings_user_provider
    on user_third_party_bindings(user_id, provider_key);

create unique index if not exists uk_user_third_party_bindings_provider_subject
    on user_third_party_bindings(provider_key, subject);
