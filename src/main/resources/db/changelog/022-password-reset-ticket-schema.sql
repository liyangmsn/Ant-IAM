--liquibase formatted sql

--changeset antiam:030-create-password-reset-ticket-schema
create table password_reset_tickets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    token_hash varchar(128) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    requested_by varchar(128),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_password_reset_tickets_user on password_reset_tickets(user_id);
create index idx_password_reset_tickets_expires_at on password_reset_tickets(expires_at);
