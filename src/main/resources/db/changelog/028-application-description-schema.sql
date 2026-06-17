--liquibase formatted sql

--changeset antiam:035-add-application-description
ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS description TEXT;
