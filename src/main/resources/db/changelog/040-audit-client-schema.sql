--liquibase formatted sql

--changeset antiam:040-add-audit-client-info
alter table audit_events
    add column if not exists ip_address varchar(64),
    add column if not exists user_agent varchar(512);
