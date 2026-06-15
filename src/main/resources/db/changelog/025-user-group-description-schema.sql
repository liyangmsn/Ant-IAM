--liquibase formatted sql

--changeset antiam:025-user-group-description-schema
alter table user_groups
    add column if not exists description varchar(1024);
