--liquibase formatted sql

--changeset antiam:022-add-tenant-scope-to-core-resources
alter table user_accounts add column tenant_id uuid references tenants(id);
alter table applications add column tenant_id uuid references tenants(id);
alter table identity_sources add column tenant_id uuid references tenants(id);

create index idx_user_accounts_tenant on user_accounts(tenant_id);
create index idx_applications_tenant on applications(tenant_id);
create index idx_identity_sources_tenant on identity_sources(tenant_id);
