--liquibase formatted sql

--changeset antiam:037-create-authentication-provider-schema
create table if not exists authentication_providers (
    id uuid primary key default gen_random_uuid(),
    provider_key varchar(128) not null unique,
    name varchar(256) not null,
    provider varchar(64) not null,
    type varchar(32) not null,
    description text,
    configuration text,
    visible boolean not null default true,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

--changeset antiam:038-seed-authentication-providers
insert into authentication_providers (provider_key, name, provider, type, description, configuration, visible, enabled, created_at, updated_at)
values
    ('gitee', 'Gitee认证', 'GITEE', 'SOCIAL', '通过Gitee进行身份认证', '{}', true, true, now(), now()),
    ('github', 'GitHub认证', 'GITHUB', 'SOCIAL', '通过GITHUB进行身份认证', '{}', true, true, now(), now()),
    ('alipay', '支付宝认证', 'ALIPAY', 'SOCIAL', '通过支付宝进行身份认证', '{}', true, true, now(), now()),
    ('qq', 'QQ认证', 'QQ', 'SOCIAL', '通过QQ进行身份认证', '{}', true, true, now(), now()),
    ('wechat', '微信认证', 'WECHAT', 'SOCIAL', '通过微信扫码进行身份认证', '{}', true, true, now(), now()),
    ('wechat-work', '企业微信认证', 'WECHAT_WORK', 'ENTERPRISE', '通过企业微信进行身份认证', '{}', true, true, now(), now()),
    ('feishu', '飞书认证', 'FEISHU', 'ENTERPRISE', '通过飞书进行身份认证', '{}', true, true, now(), now()),
    ('dingtalk', '钉钉认证', 'DINGTALK', 'ENTERPRISE', '通过钉钉进行身份认证', '{}', true, true, now(), now())
on conflict (provider_key) do nothing;
