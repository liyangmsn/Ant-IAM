--liquibase formatted sql

--changeset antiam:027-add-password-failure-policy
alter table authentication_policies add column password_max_failure_attempts integer not null default 5;

alter table user_credentials add column failed_attempts integer not null default 0;
alter table user_credentials add column last_failed_at timestamptz;
alter table user_credentials add column locked_at timestamptz;

create index idx_user_credentials_failed_attempts on user_credentials(failed_attempts);
