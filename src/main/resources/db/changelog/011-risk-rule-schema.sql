--liquibase formatted sql

--changeset antiam:017-create-risk-rule-schema
create table risk_rules (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null unique,
    name varchar(256) not null,
    type varchar(64) not null,
    condition_value text,
    threshold integer not null,
    risk_level varchar(32) not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

--changeset antiam:018-create-risk-assessment-schema
create table risk_assessments (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references user_accounts(id) on delete cascade,
    ip_address varchar(128),
    user_agent varchar(1024),
    risk_level varchar(32) not null,
    matched_rules text,
    decision text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_risk_assessments_user on risk_assessments(user_id, created_at desc);
