--liquibase formatted sql

--changeset antiam:037-add-user-identity-source
alter table user_accounts
    add column if not exists identity_source_id uuid references identity_sources(id);

create index if not exists idx_user_accounts_identity_source on user_accounts(identity_source_id);
