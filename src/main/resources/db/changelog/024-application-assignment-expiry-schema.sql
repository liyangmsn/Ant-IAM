--liquibase formatted sql

--changeset antiam:032-add-application-assignment-expiry
alter table application_assignments add column expires_at timestamptz;

create index idx_application_assignments_expires_at on application_assignments(expires_at);
