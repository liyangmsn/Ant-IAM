--liquibase formatted sql

--changeset iam:033-oauth2-authorization-nonce
alter table oauth_authorization_codes
    add column nonce varchar(255);
