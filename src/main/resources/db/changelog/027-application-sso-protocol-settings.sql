--liquibase formatted sql

--changeset antiam:034-add-application-sso-protocol-settings
alter table application_sso_configs add column grant_types text;
alter table application_sso_configs add column pkce_required boolean not null default false;
alter table application_sso_configs add column post_logout_redirect_uris text;
alter table application_sso_configs add column login_initiation_uri varchar(1024);
alter table application_sso_configs add column access_token_ttl_minutes integer not null default 20;
alter table application_sso_configs add column authorization_code_ttl_minutes integer not null default 5;
alter table application_sso_configs add column refresh_token_ttl_minutes integer not null default 43200;
alter table application_sso_configs add column id_token_ttl_minutes integer not null default 30;
alter table application_sso_configs add column reuse_refresh_tokens boolean not null default false;
alter table application_sso_configs add column id_token_signature_algorithm varchar(64) not null default 'RS256';

update application_sso_configs
set grant_types = 'authorization_code
refresh_token'
where grant_types is null;
