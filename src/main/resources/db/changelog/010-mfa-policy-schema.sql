--liquibase formatted sql

--changeset antiam:016-create-mfa-challenge-schema
create table mfa_challenges (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    factor_id uuid not null references mfa_factors(id) on delete cascade,
    challenge_id varchar(128) not null unique,
    code_hash varchar(128) not null,
    status varchar(32) not null,
    expires_at timestamptz,
    verified_at timestamptz,
    attempts integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_mfa_challenges_user on mfa_challenges(user_id);
create index idx_mfa_challenges_factor on mfa_challenges(factor_id);
