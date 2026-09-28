--liquibase formatted sql

--changeset antiam:040-add-application-authorization-type
alter table applications
    add column if not exists authorization_type varchar(32) not null default 'MANUAL';

--changeset antiam:041-add-application-assignment-organization
alter table application_assignments
    add column if not exists organization_id uuid references organizations(id) on delete cascade;

alter table application_assignments drop constraint if exists chk_application_assignment_subject;

alter table application_assignments add constraint chk_application_assignment_subject check (
    (case when user_id is not null then 1 else 0 end)
    + (case when group_id is not null then 1 else 0 end)
    + (case when organization_id is not null then 1 else 0 end) = 1
);

create unique index if not exists uq_application_assignments_organization
    on application_assignments(application_id, organization_id)
    where organization_id is not null;

create index if not exists idx_application_assignments_organization on application_assignments(organization_id);
