---
title: 对接文档
description: OIDC、OAuth2、SAML、CAS、JWT、SCIM 和管理 API 的接入方式
---

<p class="eyebrow">INTEGRATION GUIDE</p>

# 对接文档

<p class="lead">面向需要接入系统的第三方应用开发者，说明统一登录、通讯录同步和管理 API 的基本接入路径。</p>

## 接入前准备 {#prepare}

请先向管理员获取以下信息：

| 信息 | 用途 |
| --- | --- |
| Base URL | 所有接口的访问前缀，例如 `https://iam.example.com` |
| client_id / client_secret | OIDC、OAuth2 或其他协议的应用凭据 |
| 回调地址 | 接收登录结果，必须提前登记且保持完全一致 |
| SCIM Token 或管理 Token | 目录同步或管理 API 调用 |

```http
GET {Base URL}/actuator/health
```

返回 `UP` 表示服务健康。

## 选择接入方式 {#choose}

| 场景 | 推荐方式 |
| --- | --- |
| 自研 Web 应用统一登录 | OIDC 授权码 + PKCE |
| 已有 SAML 2.0 能力的 SaaS | SAML 2.0 |
| 传统系统只支持 Service Ticket | CAS |
| 只需要校验签名令牌 | JWT |
| 同步上游用户、组织和用户组 | SCIM 2.0（见[身份源同步](./directory-sync)）或钉钉、飞书、企业微信连接器 |
| 管理用户、角色和权限 | 管理 API |

## 通用调用约定 {#conventions}

- 普通接口使用 JSON；OAuth2 Token 端点使用表单格式。
- 字段使用小驼峰，资源标识使用 UUID，时间使用 ISO-8601 UTC。
- 管理 API 和 SCIM 使用 `Authorization: Bearer <token>`。
- 常见状态码：`200` 成功、`201` 创建成功、`400` 参数错误、`401` 未认证、`403` 无权限、`404` 资源不存在、`409` 资源冲突。

```http
POST {Base URL}/api/v1/authentication/password-login
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "********"
}
```

登录响应中的 `session.sessionIndex` 是管理 API 使用的会话令牌。若 `passwordChangeRequired` 为 `true`，应先引导用户修改密码。

## OIDC / OAuth2 {#oidc}

推荐使用授权码流程，浏览器端应用必须启用 PKCE。应用回调地址需先在控制台登记。

<figure class="figure">
<svg viewBox="0 0 760 360" role="img" aria-label="OIDC 授权码 + PKCE 流程时序图"><defs><marker id="arr-oidc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 z"/></marker></defs><rect class="box" x="40" y="10" width="140" height="36" rx="8"/><text class="t" x="110" y="32">浏览器</text><line class="lifeline" x1="110" y1="46" x2="110" y2="350"/><rect class="box" x="310" y="10" width="140" height="36" rx="8"/><text class="t" x="380" y="32">业务应用</text><line class="lifeline" x1="380" y1="46" x2="380" y2="350"/><rect class="accent" x="580" y="10" width="140" height="36" rx="8"/><text class="t" x="650" y="32">Js IAM</text><line class="lifeline" x1="650" y1="46" x2="650" y2="350"/><text class="lbl" x="245.0" y="76">① 访问应用</text><line class="line" x1="114" y1="82" x2="376" y2="82" marker-end="url(#arr-oidc)"/><text class="lbl" x="245.0" y="112">② 302 跳转 /oidc/authorize（带 code_challenge）</text><line class="line" x1="376" y1="118" x2="114" y2="118" marker-end="url(#arr-oidc)"/><text class="lbl" x="380.0" y="148">③ 用户登录并同意授权</text><line class="line" x1="114" y1="154" x2="646" y2="154" marker-end="url(#arr-oidc)"/><text class="lbl" x="380.0" y="184">④ 302 回调 redirect_uri?code=…</text><line class="line" x1="646" y1="190" x2="114" y2="190" marker-end="url(#arr-oidc)"/><text class="lbl" x="245.0" y="220">⑤ 携带 code 访问回调地址</text><line class="line" x1="114" y1="226" x2="376" y2="226" marker-end="url(#arr-oidc)"/><text class="lbl" x="515.0" y="256">⑥ POST /oauth2/token（code + code_verifier）</text><line class="line" x1="384" y1="262" x2="646" y2="262" marker-end="url(#arr-oidc)"/><text class="lbl" x="515.0" y="292">⑦ 返回 access_token / id_token / refresh_token</text><line class="line" x1="646" y1="298" x2="384" y2="298" marker-end="url(#arr-oidc)"/><text class="lbl" x="515.0" y="328">⑧ GET /oauth2/userinfo 获取用户信息</text><line class="line" x1="384" y1="334" x2="646" y2="334" marker-end="url(#arr-oidc)"/></svg>
<figcaption>图 1　授权码 + PKCE 流程：client_secret 与令牌只在业务应用后端与平台之间传递</figcaption>
</figure>

- 发现文档：<a href="/.well-known/openid-configuration" target="_self"><code>/.well-known/openid-configuration</code></a>
- 授权入口：`/oidc/authorize`
- 令牌端点：`/oauth2/token`（支持 `client_secret_basic` 与 `client_secret_post`）
- 令牌内省 / 吊销：`/oauth2/introspect`、`/oauth2/revoke`
- 用户信息：`/oauth2/userinfo`（返回的声明受授权 scope 约束）
- 公钥：`/oauth2/jwks`

刷新令牌默认每次使用后轮换，旧的 access_token 与 refresh_token 会同时失效。错误响应遵循 RFC 6749，包含 `error` 与 `error_description`。

完整参数以发现文档和管理员分配的应用配置为准，不要在前端代码中保存 client_secret。

## 应用内权限 {#app-permissions}

应用把需要鉴权的操作注册为权限点，由平台统一授权；执行这些操作前先向平台校验。授权链路为：权限点 → 应用内角色 → 用户 / 用户组 / 组织。用户必须先拥有应用访问授权，应用内权限才会生效。

- 注册：控制台创建应用时填写权限清单，或应用以客户端凭据调用 `PUT /oauth2/permissions` 全量同步（清单外的权限点会被删除）。
- 授权：在应用详情的【应用权限】页创建应用内角色并授予用户、用户组或组织。
- 鉴权：`POST /oauth2/permissions/check` 实时校验；`/oauth2/introspect` 与 `/oauth2/userinfo` 也会返回 `permissions` 数组。

权限编码以字母或数字开头，可包含字母、数字和 `: . _ -`，建议用 `资源:动作` 的形式；`iam:` 前缀为平台保留。`GET /oauth2/permissions` 可查询应用当前已注册的权限点。

```http
PUT {Base URL}/oauth2/permissions
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/json

{
  "permissions": [
    { "code": "order:read", "name": "查看订单" },
    { "code": "order:approve", "name": "审批订单", "description": "审批金额不限" }
  ]
}
```

已授予角色的权限点被同步删除后，会同时从这些角色中移除；之后再注册同名编码，需要重新加入角色。

```http
POST {Base URL}/oauth2/permissions/check
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/json

{ "token": "<用户 access_token>", "permissions": ["order:approve"] }

→ { "active": true, "allowed": true, "sub": "…", "results": { "order:approve": true } }
```

只有全部权限满足时 `allowed` 才为 `true`；`reason` 为 `token_inactive`、`permission_denied` 或应用访问决策的拒绝原因（如 `no_assignment`、`user_not_active`）。令牌只能由签发它的应用校验，拿其他应用的令牌来校验一律返回 `token_inactive`。

权限变更实时生效，不需要用户重新登录。`introspect` / `userinfo` 返回的 `permissions` 适合登录后渲染菜单；执行受保护操作前请调用 `check`，不要长期缓存权限结果。

## SAML 2.0 {#saml}

在控制台登记服务提供方的 Entity ID 与 ACS 地址，再将 IdP 元数据导入服务提供方。

- IdP 元数据：`/saml2/metadata.xml`，`KeyDescriptor` 中包含签名证书
- 签发断言：`/saml2/sso/xml?entity_id={SP Entity ID}`

断言使用 RSA-SHA256 做 enveloped 签名（exc-c14n），并包含 `AuthnStatement`。签名密钥轮换后请重新导入元数据。

## CAS {#cas}

在控制台登记 Service 地址，service 参数需与登记值完全一致。

- 登录：`/cas/login?service={Service 地址}`
- 票据校验：`/cas/serviceValidate`（JSON）、`/cas/p3/serviceValidate`（CAS 3.0 XML）

Service Ticket 一次有效：无论校验成功与否，提交校验后即被消费。

## JWT {#jwt}

- 签发：`/jwt/sso?audience={Audience}`
- 校验：`POST /jwt/verify`，请求体 `{"token": "...", "issuer": "...", "audience": "..."}`，issuer 与 audience 可选
- 公钥：`/oauth2/jwks`

令牌必须包含 `exp`；自定义声明不能覆盖 `iss`、`sub`、`aud`、`exp` 等保留声明。

## SCIM 与管理 API {#directory}

::: tip 把第三方系统的组织架构同步进来
第三方系统作为身份源推送组织、人员和用户组时，请使用身份源专属的 SCIM 端点和同步令牌，详见[身份源同步](./directory-sync)。下面的全局 SCIM 端点使用管理员令牌，面向平台自身的管理和导出。
:::

全局 SCIM 2.0 端点：

- `/scim/v2/Users`：用户列表、详情、创建、替换（PUT）、局部更新（PATCH）和删除
- `/scim/v2/Groups`：用户组列表、详情、创建、替换、局部更新（含成员增删）和删除
- `/scim/v2/Organizations`：组织列表、详情、创建、替换和删除（仅空组织）
- `/scim/v2/ServiceProviderConfig`、`/ResourceTypes`、`/Schemas`：能力发现

列表过滤支持 `eq`、`ne`、`co`、`sw`、`ew`、`pr`，可用 `and`、`or`、`not (...)` 组合，匹配不区分大小写。

管理 API 覆盖用户、组织、角色、权限、应用、授权、访问申请、审计和身份源。接口详情请向管理员获取 OpenAPI 接口文档。

## 接入自查清单 {#checklist}

- 已确认 Base URL、环境和证书有效。
- 已登记并逐字核对回调地址。
- 已区分前端公开配置与后端密钥。
- 已处理 401、令牌过期和密码改密要求。
- 已在测试环境完成登录、退出、刷新令牌和异常回调验证。
- 使用应用内权限时，受保护操作在服务端调用 `check`，没有只依赖前端隐藏按钮。
- 已限制 SCIM / 管理 Token 的权限、来源和保存位置。
