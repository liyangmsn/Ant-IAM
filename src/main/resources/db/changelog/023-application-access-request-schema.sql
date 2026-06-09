--liquibase formatted sql

--changeset antiam:031-create-application-access-request-schema
create table application_access_requests (
    id uuid primary key default gen_random_uuid(),
    application_id uuid not null references applications(id) on delete cascade,
    user_id uuid not null references user_accounts(id) on delete cascade,
    status varchar(32) not null,
    reason text,
    decision_reason text,
    requested_by varchar(128),
    decided_by varchar(128),
    decided_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_application_access_requests_status on application_access_requests(status, created_at desc);
create index idx_application_access_requests_user on application_access_requests(user_id, created_at desc);
create unique index uk_application_access_requests_pending
    on application_access_requests(application_id, user_id)
    where status = 'PENDING';
