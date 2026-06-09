--liquibase formatted sql

--changeset antiam:025-add-risk-context
alter table risk_assessments add column device_fingerprint varchar(256);
alter table risk_assessments add column geo_location varchar(128);

create index idx_risk_assessments_user_device
    on risk_assessments(user_id, device_fingerprint, created_at desc)
    where device_fingerprint is not null;
