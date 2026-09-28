--liquibase formatted sql

--changeset antiam:036-add-user-avatar-url
alter table user_accounts
    add column if not exists avatar_url varchar(1024);
