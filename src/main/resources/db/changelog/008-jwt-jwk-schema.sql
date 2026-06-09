--liquibase formatted sql

--changeset antiam:012-create-jwt-signing-key-schema
create table jwt_signing_keys (
    id uuid primary key default gen_random_uuid(),
    key_id varchar(128) not null unique,
    public_key_pem text not null,
    private_key_pem text not null,
    activated_at timestamptz,
    retired_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_jwt_signing_keys_active on jwt_signing_keys(retired_at, activated_at);
