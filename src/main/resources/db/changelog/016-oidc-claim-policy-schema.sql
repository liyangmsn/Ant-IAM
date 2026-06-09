--liquibase formatted sql

--changeset antiam:024-add-oidc-claim-policy
alter table application_sso_configs add column id_token_claims text;
alter table application_sso_configs add column custom_claims text;
