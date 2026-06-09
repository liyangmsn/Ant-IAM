--liquibase formatted sql

--changeset antiam:029-add-password-history-policy
alter table authentication_policies add column password_history_count integer not null default 0;

create table user_credential_histories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    type varchar(32) not null,
    secret_hash varchar(512) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_user_credential_histories_user_type_created
    on user_credential_histories(user_id, type, created_at desc);
