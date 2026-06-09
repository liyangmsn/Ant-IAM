--liquibase formatted sql

--changeset antiam:026-add-authentication-policy-actions
alter table authentication_policies add column mfa_enrollment_required boolean not null default false;
alter table authentication_policies add column step_up_risk_level varchar(32) not null default 'MEDIUM';
alter table authentication_policies add column deny_risk_level varchar(32) not null default 'HIGH';
