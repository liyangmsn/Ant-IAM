--liquibase formatted sql

--changeset antiam:039-application-self-service-access-request
alter table applications
    add column if not exists self_service_access_request_enabled boolean not null default true;
