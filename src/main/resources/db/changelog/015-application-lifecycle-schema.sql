--liquibase formatted sql

--changeset antiam:023-add-application-enabled
alter table applications add column enabled boolean not null default true;
