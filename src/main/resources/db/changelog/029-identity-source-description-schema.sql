--liquibase formatted sql

--changeset antiam:036-add-identity-source-description
alter table identity_sources
    add column if not exists description varchar(1024);
