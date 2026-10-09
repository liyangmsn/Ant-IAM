--liquibase formatted sql

--changeset antiam:042-application-permission-delegation
alter table application_permissions
    add column reserved boolean not null default false;

alter table application_permission_roles
    add column built_in boolean not null default false;

-- 存量应用补建两个保留权限点：应用权限负责人、授权管理员。
insert into application_permissions (application_id, code, name, description, reserved, created_at, updated_at)
select a.id, 'iam:app:permission:manage', '管理应用权限', '维护本应用的权限点与角色，授予任意角色并任命授权管理员', true, now(), now()
from applications a
where not exists (
    select 1 from application_permissions p where p.application_id = a.id and p.code = 'iam:app:permission:manage');

insert into application_permissions (application_id, code, name, description, reserved, created_at, updated_at)
select a.id, 'iam:app:grant:manage', '分配应用角色', '把本应用的普通角色授予或撤销给人员', true, now(), now()
from applications a
where not exists (
    select 1 from application_permissions p where p.application_id = a.id and p.code = 'iam:app:grant:manage');

-- 两个内置角色，各自只包含对应的保留权限点。
insert into application_permission_roles (application_id, code, name, description, built_in, created_at, updated_at)
select a.id, 'iam:app-owner', '应用权限负责人', '系统内置：维护本应用的权限点与角色，授予任意角色并任命授权管理员', true, now(), now()
from applications a
where not exists (
    select 1 from application_permission_roles r where r.application_id = a.id and r.code = 'iam:app-owner');

insert into application_permission_roles (application_id, code, name, description, built_in, created_at, updated_at)
select a.id, 'iam:app-grant-manager', '授权管理员', '系统内置：把本应用的普通角色授予或撤销给人员', true, now(), now()
from applications a
where not exists (
    select 1 from application_permission_roles r where r.application_id = a.id and r.code = 'iam:app-grant-manager');

insert into application_permission_role_permissions (role_id, permission_id)
select r.id, p.id
from application_permission_roles r
join application_permissions p on p.application_id = r.application_id
where (r.code = 'iam:app-owner' and p.code = 'iam:app:permission:manage')
   or (r.code = 'iam:app-grant-manager' and p.code = 'iam:app:grant:manage')
on conflict do nothing;
