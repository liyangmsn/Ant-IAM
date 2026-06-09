--liquibase formatted sql

--changeset antiam:028-add-password-expiry-policy
alter table authentication_policies add column password_expires_in_days integer not null default 0;
