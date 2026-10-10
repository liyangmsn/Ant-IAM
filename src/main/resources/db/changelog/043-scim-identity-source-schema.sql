--liquibase formatted sql

--changeset antiam:043-scim-identity-source
alter table organizations
    add column if not exists identity_source_id uuid references identity_sources(id) on delete set null,
    add column if not exists external_id varchar(256);

alter table user_groups
    add column if not exists identity_source_id uuid references identity_sources(id) on delete set null,
    add column if not exists external_id varchar(256);

alter table user_accounts
    add column if not exists external_id varchar(256);

alter table identity_sources
    add column if not exists last_synced_at timestamptz;

-- 同一身份源内 externalId 唯一；external_id 为空的记录（本地数据、旧数据）不参与约束。
create unique index if not exists uk_organizations_source_external
    on organizations(identity_source_id, external_id) where external_id is not null;
create unique index if not exists uk_user_groups_source_external
    on user_groups(identity_source_id, external_id) where external_id is not null;
create unique index if not exists uk_user_accounts_source_external
    on user_accounts(identity_source_id, external_id) where external_id is not null;

create index if not exists idx_organizations_identity_source on organizations(identity_source_id);
create index if not exists idx_user_groups_identity_source on user_groups(identity_source_id);
