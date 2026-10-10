# 系统第三方应用接入指南

本文档面向需要接入系统的第三方应用开发者，说明系统提供哪些接入能力、如何取得凭据、以及如何完成单点登录与通讯录同步。具体接入地址、应用凭据和回调地址由管理员在你的环境中分配。

## 目录

| 章节 | 内容 |
| --- | --- |
| 1 | 接入前准备 |
| 2 | 系统能提供什么 |
| 3 | 选择接入方式 |
| 4 | 通用调用约定 |
| 5 | 单点登录接入（OIDC / OAuth2） |
| 6 | 单点登录接入（SAML 2.0） |
| 7 | 单点登录接入（CAS / JWT） |
| 8 | 通讯录同步（SCIM 2.0） |
| 9 | 管理 API 能力清单 |
| 10 | 典型业务场景 |
| 11 | 常见问题 |
| 12 | 接入自查清单 |

---

## 1. 接入前准备

### 1.1 向管理员索取的信息

| 项目 | 用途 |
| --- | --- |
| 接入地址（Base URL） | 所有接口的访问前缀，例如 `https://iam.example.com` |
| 应用标识（client_id） | 单点登录时标识你的应用 |
| 应用密钥（client_secret） | 单点登录换取令牌时使用 |
| 回调地址登记 | 你的应用接收登录结果的地址，需提前登记 |
| 管理员账号 | 仅当你需要调用管理 API 时才需要 |

回调地址是接入过程中最容易出错的一环：**授权请求和换取令牌时使用的回调地址，必须与登记的完全一致**，否则请求会被拒绝。

### 1.2 确认服务可用

```http
GET {Base URL}/actuator/health
```

返回 `UP` 表示服务正常。建议在应用启动时做一次探测，不要直接对业务接口做健康判断。

---

## 2. 系统能提供什么

| 能力 | 说明 |
| --- | --- |
| 统一登录 | 用户一次登录即可访问所有接入的应用，支持 OIDC、SAML 2.0、CAS、JWT 四种协议 |
| 通讯录同步 | 支持通过 SCIM 2.0 从上游同步用户、用户组和组织 |
| 组织人员管理 | 组织架构、用户账号、用户组、角色、权限的完整管理接口 |
| 应用访问控制 | 应用注册、应用授权、访问决策、访问审查 |
| 应用门户 | 用户可查看已授权的应用，也可自助申请未授权的应用 |
| 风险与审计 | 登录风险策略、认证事件与操作审计记录 |

对绝大多数应用来说，只需要其中一两项能力，不必全部接入。

---

## 3. 选择接入方式

按下表定位你的场景：

| 你的场景 | 使用方式 | 参考章节 |
| --- | --- | --- |
| 自研 Web 应用，希望用户用 IAM 账号登录 | OIDC 授权码流程 | 第 5 章 |
| 已有 SAML 2.0 身份的成熟产品（如部分 SaaS） | SAML 2.0 | 第 6 章 |
| 老系统只支持 CAS | CAS | 第 7 章 |
| 自研应用只想校验一个签名令牌 | JWT 单点登录 | 第 7 章 |
| 需要把用户从上游系统同步进 IAM | SCIM 2.0 或身份源连接器 | 第 8 章 |
| 需要管理组织、用户、角色、权限 | 管理 API | 第 9 章 |
| 需要判断某用户可以访问哪些应用 | 访问决策接口 | 第 9.4 节 |
| 需要让用户自助申请应用权限 | 应用访问申请接口 | 第 10.5 节 |

一个应用可以同时使用多种方式。例如既通过 OIDC 完成登录，又通过管理 API 读取用户所属组织。

---

## 4. 通用调用约定

### 4.1 数据格式

| 项目 | 约定 |
| --- | --- |
| 请求与响应 | 统一使用 JSON（OAuth2 令牌端点使用表单格式） |
| 字段命名 | 小驼峰，例如 `displayName`、`loginUrl` |
| 资源标识 | UUID 字符串，例如 `3f2a1c9e-...` |
| 时间 | ISO-8601 UTC，例如 `2026-09-17T04:30:00Z` |
| 枚举 | 全大写下划线，例如 `ACTIVE`、`LOCKED` |
| 布尔值 | `true` / `false` |

枚举值大小写敏感，请勿传入小写或驼峰形式。

### 4.2 鉴权方式

**协议端点**（OIDC、SAML、CAS）按各自协议规范鉴权，无需额外配置。

**管理 API 与 SCIM** 使用访问令牌：

```http
Authorization: Bearer <访问令牌>
```

访问令牌通过登录接口获取，有效期 8 小时。

### 4.3 登录并获取访问令牌

```http
POST {Base URL}/api/v1/authentication/password-login
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "********"
}
```

响应中的 `session.sessionIndex` 即为访问令牌：

```json
{
  "userId": "8f14e45f-...",
  "session": {
    "sessionIndex": "0hT3x...",
    "expiresAt": "2026-09-17T12:30:00Z"
  },
  "passwordChangeRequired": false
}
```

当 `passwordChangeRequired` 为 `true` 时，表示当前为临时密码或密码已过期，应引导用户先修改密码。

### 4.4 无需登录即可访问的端点

| 端点 | 用途 |
| --- | --- |
| `/actuator/health` | 健康检查 |
| `/v3/api-docs`、`/swagger-ui/index.html` | 接口文档 |
| `/.well-known/openid-configuration` | OIDC 发现文档 |
| `/oauth2/jwks` | OIDC 公钥 |
| `/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke` | 令牌相关 |
| `/scim/v2/sources/{code}/**` | 身份源 SCIM 推送（需同步令牌，在业务层校验） |
| `/oauth2/permissions`、`/oauth2/permissions/check`、`/oauth2/permission-admin/**` | 应用内权限注册、校验与委派管理（需客户端凭据） |
| `/oauth2/userinfo` | 用户信息（需携带 access_token） |
| `/saml2/metadata`、`/saml2/metadata.xml` | SAML 元数据 |
| `/cas/serviceValidate`、`/cas/p3/serviceValidate` | CAS 票据校验 |
| `/jwt/verify` | JWT 签名与有效期校验 |
| `/api/v1/authentication/password-login`、`/mobile-login`、`/sms-codes` | 登录相关 |
| `/api/v1/authentication/third-party/*/authorize`、`/callback` | 第三方登录 |
| `/api/v1/users/password-reset-tickets/consumptions` | 凭票据重置密码 |
| `/api/v1/catalog` | 系统能力清单 |

其余接口均需要携带访问令牌。SCIM 的全部接口都需要令牌。

### 4.5 状态码

| 状态码 | 含义 |
| --- | --- |
| `200` | 成功 |
| `201` | 创建成功，登录成功也返回此状态 |
| `204` | 成功，无返回内容（删除、设置密码等） |
| `400` | 参数错误或业务条件不满足 |
| `401` | 未登录、令牌无效或已过期 |
| `404` | 目标资源不存在 |

### 4.6 错误信息

参数错误和资源不存在时返回统一格式：

```json
{
  "type": "urn:ant-iam:error:validation",
  "title": "Bad Request",
  "status": 400,
  "detail": "username must not be blank"
}
```

`detail` 字段包含具体原因。`401` 错误仅返回状态码，不保证包含上述结构，请以状态码判断是否需要重新登录。

### 4.7 调用建议

- 访问令牌可复用至过期，不要每次请求前重复登录。
- 收到 `401` 时重新登录一次；若仍失败，应判定为账号或密码问题，不要持续重试。
- 列表接口请使用过滤参数缩小结果范围，避免一次拉取全部数据。

---

## 5. 单点登录接入（OIDC / OAuth2）

这是推荐的接入方式，适用于自研 Web 应用。

### 5.1 整体流程

```text
① 用户访问你的应用
② 你的应用把浏览器重定向到 IAM 授权页
③ 用户在 IAM 完成登录，并确认授权范围
④ IAM 跳回你的回调地址，并带上授权码
⑤ 你的应用用授权码换取访问令牌和身份令牌
⑥ 你的应用读取用户信息，建立本地登录态
```

### 5.2 发现文档

```http
GET {Base URL}/.well-known/openid-configuration
```

返回内容包括授权地址、令牌地址、用户信息地址、公钥地址以及支持的签名算法、授权范围等。建议你的应用启动时读取一次，不要把这些地址硬编码。

| 关键字段 | 说明 |
| --- | --- |
| `authorization_endpoint` | 浏览器授权入口；按需引导 IAM 登录和用户同意，见 5.3 节 |
| `token_endpoint` | 令牌地址，用于换取令牌 |
| `userinfo_endpoint` | 用户信息地址 |
| `jwks_uri` | 公钥地址，用于校验身份令牌签名 |
| `introspection_endpoint` | 令牌校验地址 |
| `revocation_endpoint` | 令牌撤销地址 |
| `response_types_supported` | 支持的响应类型：`code` |
| `grant_types_supported` | 支持的授权模式：`authorization_code`、`refresh_token` |
| `id_token_signing_alg_values_supported` | 身份令牌签名算法：`RS256` |
| `scopes_supported` | 支持的授权范围：`openid`、`profile`、`email`、`phone`、`offline_access` |
| `code_challenge_methods_supported` | 支持的 PKCE 方法：`S256`、`plain` |

浏览器发起登录请使用 5.3 节的授权页地址。

### 5.3 发起登录

需要用户登录你的应用时，把浏览器重定向到 IAM 授权页：

```http
GET {Base URL}/oidc/authorize
  ?client_id=<应用标识>
  &redirect_uri=<回调地址>
  &scope=openid%20profile%20email
  &state=<随机串>
  &code_challenge=<PKCE 挑战值>
  &code_challenge_method=S256
```

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `client_id` | 是 | 管理员分配的应用标识 |
| `redirect_uri` | 是 | 必须与登记的回调地址一致 |
| `scope` | 否 | 申请的授权范围，多个值以空格分隔 |
| `state` | 否 | 建议传入随机串，回调时校验以防伪造 |
| `code_challenge` | 否 | 启用 PKCE 时传入 |
| `code_challenge_method` | 否 | 启用 PKCE 时传入 `S256` |

该页面会自动处理以下情况，你的应用无需额外判断：

- 用户尚未登录 IAM 时，页面会引导用户登录，登录后自动回到授权页。
- 用户首次使用该应用时，页面会展示申请的授权范围，由用户确认。
- 用户此前已授权当前请求的全部范围时，会跳过同意页并直接跳回你的回调地址。

用户在授权页点击「同意授权」后，浏览器会带着授权码跳转到你在 `redirect_uri` 中指定的地址。

### 5.4 处理授权结果

授权成功后，用户被重定向回你的回调地址，并带上授权码：

```text
{回调地址}?code=<授权码>&state=<原样返回的 state>
```

请先校验 `state` 与发起时是否一致，再进入下一步。

授权码一次性使用且有效期 5 分钟，请收到后立即换取令牌。

### 5.5 换取令牌

```http
POST {Base URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=<授权码>
&redirect_uri=<回调地址>
&client_id=<应用标识>
&client_secret=<应用密钥>
&code_verifier=<PKCE 校验值>
```

响应：

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "id_token": "...",
  "scope": "openid profile email"
}
```

| 令牌 | 用途 | 有效期 |
| --- | --- | --- |
| `access_token` | 调用用户信息接口、资源接口 | 1 小时 |
| `refresh_token` | 换取新的访问令牌 | 30 天 |
| `id_token` | 身份令牌，包含用户标识，使用 RS256 签名 | 1 小时 |

这里的令牌由 IAM 签发给你的应用使用，与第 4 章用于调用管理接口的访问令牌是两个不同的凭据，请勿混用。

刷新令牌：

```http
grant_type=refresh_token
&refresh_token=<刷新令牌>
&client_id=<应用标识>
&client_secret=<应用密钥>
```

### 5.6 获取用户信息

```http
GET {Base URL}/oauth2/userinfo
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "sub": "8f14e45f-...",
  "preferredUsername": "zhangsan",
  "name": "张三",
  "email": "zhangsan@example.com",
  "phoneNumber": "13800000000"
}
```

`sub` 是用户在 IAM 中的唯一标识，建议作为你系统里的账号关联依据。

### 5.7 校验与撤销令牌

| 操作 | 请求 |
| --- | --- |
| 校验令牌是否有效 | `POST /oauth2/introspect`，参数 `token`、`client_id`、`client_secret` |
| 撤销令牌 | `POST /oauth2/revoke`，参数 `token`、`token_type_hint`、`client_id`、`client_secret` |

校验结果中的 `active` 表示令牌是否仍然有效；access_token 的校验结果还会通过 `permissions` 返回用户在本应用内的有效权限编码（见 5.10）。

### 5.8 校验身份令牌签名

从 `{Base URL}/oauth2/jwks` 获取公钥，用其中的 `kid` 匹配身份令牌头部的 `kid`，以 RS256 算法验证签名，并校验 `iss`、`aud`、`exp` 等标准声明。

### 5.9 由服务端发起授权（可选）

如果你的应用已经持有用户的访问令牌（例如用户先在 IAM 完成登录，再由你代为发起授权），可以直接调用授权接口获取授权码，无需跳转授权页：

```http
GET {Base URL}/oauth2/authorize
  ?response_type=code
  &client_id=<应用标识>
  &redirect_uri=<回调地址>
  &scope=openid%20profile%20email
  &state=<随机串>
Authorization: Bearer <访问令牌>
```

该接口返回 JSON，而非跳转：

```json
{
  "redirectTo": "https://your-app.example.com/callback?code=...&state=...",
  "code": "...",
  "state": "...",
  "consentRequired": false,
  "requestedScopes": ["openid", "profile"]
}
```

| 字段 | 说明 |
| --- | --- |
| `redirectTo` | `consentRequired` 为 `false` 时，这是带授权码的回调地址，可直接跳转 |
| `code` | 授权码；`consentRequired` 为 `true` 时为空 |
| `consentRequired` | 为 `true` 表示用户尚未授权，需先把浏览器跳转到 `{Base URL}{redirectTo}` 完成授权确认 |
| `requestedScopes` | 本次实际生效的授权范围 |

该接口对调用者身份有要求，仅适用于能够自行完成用户登录的受信任应用。

---

### 5.10 应用内权限

应用可以把自身需要鉴权的操作（例如“审批订单”“导出报表”）注册为**应用内权限点**，由 IAM 统一授权；应用在执行这些操作前先向 IAM 校验，不再自行维护权限数据。

**授权模型**：权限点 → 应用内角色 → 用户 / 用户组 / 组织（授予组织即覆盖其下级组织成员）。用户必须先通过应用访问决策（应用已启用、用户状态正常、拥有应用访问授权），应用内权限才会生效；失去应用访问授权的用户不具备任何应用内权限。

**1. 注册权限点**，任选其一：

- 管理员在控制台创建应用时填写权限清单，或在应用详情的【应用权限】页维护；管理 API 为 `POST /api/v1/access/applications` 请求体中的 `permissions` 字段。
- 应用使用客户端凭据声明式地全量同步，适合在启动或发布时调用。清单外已注册的权限点会被删除并从应用内角色中移除：

```http
PUT /oauth2/permissions
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/json

{
  "permissions": [
    { "code": "order:read", "name": "查看订单" },
    { "code": "order:approve", "name": "审批订单", "description": "审批待处理订单" }
  ]
}
```

权限编码在应用内唯一，可包含字母、数字以及 `:` `.` `_` `-`。`GET /oauth2/permissions` 返回当前已注册的权限点。

**2. 授权**：管理员在【应用权限】页创建应用内角色、勾选权限点，再将角色授予用户、用户组或组织。

**3. 鉴权**：应用在执行受保护操作前，以客户端凭据提交用户的 access_token（必须由本应用签发）和待校验的权限编码，IAM 实时返回结果：

```http
POST /oauth2/permissions/check
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/json

{ "token": "<用户 access_token>", "permissions": ["order:approve"] }
```

```json
{
  "active": true,
  "allowed": true,
  "sub": "<用户 UUID>",
  "username": "zhangsan",
  "results": { "order:approve": true }
}
```

只有全部权限都满足时 `allowed` 才为 `true`，应在 `allowed` 为 `false` 时拒绝操作。`reason` 的取值：`token_inactive`（令牌无效、过期、已撤销或不属于本应用）、`permission_denied`（缺少权限），以及应用访问决策的拒绝原因如 `no_assignment`、`application_disabled`。

除实时校验外，`POST /oauth2/introspect` 和 `GET /oauth2/userinfo` 的响应也包含 `permissions` 数组，适合登录后一次性获取权限、在前端控制菜单与按钮显隐。缓存权限时请设置较短的有效期，权限变更以 `/oauth2/permissions/check` 的实时结果为准。

### 5.11 委派应用权限管理

每个应用自动内置两个角色，用来把本应用的权限管理交给业务方，而不需要给他们 IAM 控制台权限：

| 内置角色编码 | 名称 | 包含的保留权限点 | 能做什么 |
| --- | --- | --- | --- |
| `iam:app-owner` | 应用权限负责人 | `iam:app:permission:manage` | 维护本应用的权限点与角色，授予任意角色，任命授权管理员 |
| `iam:app-grant-manager` | 授权管理员 | `iam:app:grant:manage` | 把普通角色授予或撤销给人员 |

- `iam:` 前缀为系统保留，应用声明或同步权限点、创建角色时都不能使用；`PUT /oauth2/permissions` 同步时不会删除保留权限点。
- 内置角色不可修改或删除；普通角色不能包含保留权限点；授权管理员不能授予内置角色；不能撤销最后一名应用权限负责人。
- 委派身份依赖应用访问授权：失去应用访问授权的人，管理能力同时失效。
- 被委派的人可以在 IAM 门户的「我管理的应用」中管理，也可以在业务应用自己的权限管理页中管理。

业务应用代表当前登录用户调用管理接口时，同时携带客户端凭据和该用户的 access_token（必须由本应用签发），IAM 按该用户的委派身份判定：

```http
POST /oauth2/permission-admin/roles/order-auditor/members
Authorization: Basic base64(client_id:client_secret)
X-Acting-User-Token: <操作人的 access_token>
Content-Type: application/json

{ "subjectType": "USER", "subjectIds": ["<用户 UUID>"] }
```

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/oauth2/permission-admin/me` | 操作人的管理级别（`OWNER`、`GRANT_MANAGER` 或 `NONE`），用于决定是否展示权限管理入口 |
| GET | `/oauth2/permission-admin/roles` | 本应用的角色及其权限点 |
| GET / POST | `/oauth2/permission-admin/roles/{roleCode}/members` | 角色的授予对象 / 授予 |
| DELETE | `/oauth2/permission-admin/roles/{roleCode}/members/{memberId}` | 撤销 |
| GET | `/oauth2/permission-admin/subjects?type=USER&keyword=` | 搜索可授予的在职用户、用户组或组织 |
| GET | `/oauth2/permission-admin/users/{userId}/permissions` | 某人在本应用内的有效权限 |

缺少 `X-Acting-User-Token` 返回 `400`；令牌无效或不属于本应用返回 `401`；操作人没有对应的委派身份返回 `403`。审计日志记录操作人与来源客户端（`via=client:<client_id>`）。

## 6. 单点登录接入（SAML 2.0）

适用于已支持 SAML 2.0 的成熟产品。

| 端点 | 用途 |
| --- | --- |
| `{Base URL}/saml2/metadata.xml` | 元数据，可直接导入你的 SP 配置 |
| `{Base URL}/saml2/metadata` | 元数据的 JSON 形式，便于查看 |
| `{Base URL}/saml2/sso?entity_id=<你的 EntityID>` | 发起单点登录 |
| `{Base URL}/saml2/sso/xml?entity_id=<你的 EntityID>` | 获取 SAML Response XML |

接入步骤：

1. 把你的 SP EntityID 和 ACS 地址提供给管理员登记。
2. 导入元数据或手工配置 IAM 的 SSO 地址与签名证书。
3. 由你的应用发起登录请求，IAM 认证通过后返回 SAML Response。
4. 校验签名后读取断言中的用户信息。

断言的 `subject` 为用户名，`attributes` 中包含附加属性。

- 断言使用 RSA-SHA256 做 enveloped 签名（exc-c14n），并包含 `AuthnStatement`；签名证书就是元数据 `KeyDescriptor use="signing"` 中的证书。
- 签名密钥与 OIDC 共用 `/api/v1/jwt-signing-keys`，密钥轮换后请重新导入元数据。
- 目前不解析 SP 发来的 `AuthnRequest`，也不支持 SLO（单点登出）。

---

## 7. 单点登录接入（CAS / JWT）

适用于只支持 CAS 的老系统，以及只需要校验一个签名令牌的自研应用。

### 7.1 CAS 单点登录

适用于仅支持 CAS 的老系统。

| 端点 | 用途 |
| --- | --- |
| `{Base URL}/cas/login?service=<你的服务地址>` | 发起登录，成功后返回服务票据 |
| `{Base URL}/cas/serviceValidate?service=&ticket=` | 校验票据，返回 JSON |
| `{Base URL}/cas/p3/serviceValidate?service=&ticket=` | 校验票据，返回 CAS 3.0 XML |

接入步骤：

1. 把你的服务地址提供给管理员登记。
2. 未登录用户重定向到 `/cas/login`，并带上 `service` 参数。
3. 校验返回的 `ticket`：调用 `serviceValidate` 并传入相同的 `service`。
4. 校验结果中的 `success` 表示票据有效，`user` 为用户名，`attributes` 为附加属性。

`service` 参数在校验时必须与登录时完全一致。

> 所有单点登录协议（OIDC、SAML、CAS、JWT）在签发前都会校验应用访问授权：用户没有该应用的授权时返回 `403`，响应体 `type` 以 `application-access-denied` 结尾，`reason` 给出原因（取值见 9.4 访问决策）。

### 7.2 JWT 单点登录

适用于自研应用：不引入 OIDC 客户端库，直接拿一个 RS256 签名的 JWT，自己验签后读取用户身份。

| 端点 | 用途 |
| --- | --- |
| `{Base URL}/jwt/sso?audience=<你的 Audience>` | 为当前登录用户签发 JWT，`audience` 也接受应用的 `client_id` |
| `POST {Base URL}/jwt/verify` | 校验 JWT 签名与有效期，返回令牌声明 |
| `{Base URL}/oauth2/jwks` | 公钥集，可自行在本地验签 |

接入步骤：

1. 把你的 `audience` 提供给管理员，登记到该应用的 SSO 配置中。
2. 用户未登录时，引导其访问 `/jwt/sso?audience=<你的 Audience>`（IAM 会要求先登录）。
3. 从响应中取出 `accessToken`，`tokenType` 为 `Bearer`。
4. 校验令牌：调用 `POST /jwt/verify`，或按 `kid` 从 `/oauth2/jwks` 取公钥在本地验签。

签发响应字段：

| 字段 | 说明 |
| --- | --- |
| `accessToken` | 签发的 JWT |
| `tokenType` | 固定为 `Bearer` |
| `issuer` | 签发方标识，取自请求根地址 |
| `audience` | 应用配置的 JWT Audience |
| `subject` | 用户账号 |
| `issuedAt` / `expiresAt` | 签发与过期时间，有效期取应用 SSO 配置的 access_token 有效期 |

请求示例：

```bash
curl -X POST '{Base URL}/jwt/verify' \
  -H 'Content-Type: application/json' \
  -d '{"token":"<accessToken>"}'
```

校验通过时 `valid` 为 `true`，`claims` 中默认包含 `preferred_username`、`name`、`email`、`phone_number`、`tenant_id`、`organization_id`，以及应用 SSO 配置里的自定义声明。校验失败时 `valid` 为 `false`，`failureCode` 取以下值：

| failureCode | 含义 |
| --- | --- |
| `MALFORMED_TOKEN` | 令牌格式不合法，或缺少有效段 |
| `UNSUPPORTED_ALGORITHM` | 非 RS256 令牌，拒绝接受 |
| `UNKNOWN_KEY` | 令牌 `kid` 在服务端找不到对应签名密钥 |
| `INVALID_SIGNATURE` | 签名校验失败 |
| `TOKEN_EXPIRED` | 令牌已过期 |
| `TOKEN_NOT_YET_VALID` | `nbf` 未到生效时间 |

令牌使用的签名密钥可在管理接口 `/api/v1/jwt-signing-keys` 查看、轮换与退役；轮换后旧的公钥仍可用于校验未过期的既有令牌。

---

## 8. 通讯录同步（SCIM 2.0）

适用于上游系统需要把用户和组织同步进 IAM 的场景。支持用户、用户组、组织三类资源。

### 8.1 发现接口

| 端点 | 用途 |
| --- | --- |
| `/scim/v2/ServiceProviderConfig` | 服务能力声明 |
| `/scim/v2/ResourceTypes` | 支持的资源类型 |
| `/scim/v2/Schemas` | 字段定义 |

以上接口同样需要携带访问令牌。

### 8.2 资源操作

| 资源 | 支持的操作 |
| --- | --- |
| 用户 | 列表、详情、创建（`POST`）、整体替换（`PUT`）、局部更新（`PATCH`）、删除（`DELETE`） |
| 用户组 | 列表、详情、创建、整体替换、局部更新、删除 |
| 组织 | 列表、详情、创建、整体替换、删除（不支持 `PATCH`） |

各操作的注意事项：

- **用户 `PUT`**：整体替换显示名、邮箱、手机号和启用状态，`userName` 不可修改。
- **用户 `PATCH`**：支持 `add` / `replace` / `remove` 操作 `active`、`displayName`、`name.formatted`、`emails`、`phoneNumbers`，未识别的属性会被忽略；`remove` 必须带 `path`。
- **用户 `DELETE`**：删除账号及其凭据、令牌和授权。离职场景更推荐 `PATCH` 设置 `active=false` 以保留审计轨迹。
- **用户组 `PATCH`**：支持替换 `displayName`，以及对 `members` 做 `add` / `remove` / `replace`，也可用 `members[value eq "<userId>"]` 移除单个成员。
- **组织 `PUT`**：更新名称和父组织，`externalId` 不可修改。
- **组织 `DELETE`**：只能删除空组织，存在子组织或成员时拒绝。

局部更新示例：

```http
PATCH /scim/v2/Users/{id}
Content-Type: application/scim+json

{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations": [
    {"op": "replace", "path": "active", "value": false}
  ]
}
```

列表支持 `filter`、`startIndex`、`count` 三个参数，返回标准的 SCIM `ListResponse` 结构，包含 `totalResults`、`startIndex`、`itemsPerPage` 和 `Resources`。

### 8.3 过滤语法

格式为 `属性 操作符 "值"`：

| 操作符 | 含义 |
| --- | --- |
| `eq` | 等于 |
| `ne` | 不等于 |
| `co` | 包含 |
| `sw` | 以……开头 |
| `ew` | 以……结尾 |
| `pr` | 有值（无需写值） |

匹配不区分大小写。多个条件可用 `and`、`or` 组合，`not (...)` 取反，括号改变优先级（`or` 优先级最低）。

各资源支持的过滤属性：

| 资源 | 可用属性 |
| --- | --- |
| 用户 | `userName`、`displayName`、`active`、`emails.value`、`phoneNumbers.value` |
| 用户组 | `displayName`、`members.value` |
| 组织 | `externalId`、`displayName`、`parentId` |

示例：

```text
userName eq "zhangsan"
emails.value co "example.com"
displayName sw "研发"
userName sw "zh" and active eq "true"
not (emails.value ew "example.com")
```

使用其它属性或不支持的写法会返回 `400`。

### 8.4 第三方系统作为身份源推送（推荐）

8.1–8.3 的全局 SCIM 端点使用管理员会话令牌，能读写全部目录数据，适合平台自身的管理工具。**第三方系统要把自己的组织架构同步进来时，请使用身份源专属端点**：

| 项目 | 说明 |
| --- | --- |
| 基础地址 | `{Base URL}/scim/v2/sources/{身份源编码}`，资源路径 `/Organizations`、`/Users`、`/Groups` 与全局端点相同 |
| 认证 | `Authorization: Bearer <同步令牌>`，令牌在控制台身份源详情「同步配置」页生成，长期有效，可重新生成或吊销 |
| 数据范围 | 只能读写本身份源推送的数据；访问其他数据返回 `404` |
| 引用方式 | 组织、上级组织、人员所属组织用第三方自己的 `externalId` 引用；挂到平台已有组织下时用 `{"value": "<UUID>", "type": "id"}` |
| 人员所属组织 | 扩展 schema `urn:antiam:params:scim:schemas:extension:2.0:User` 的 `organization` 属性 |
| 删除语义 | 人员 `DELETE` 为停用（可通过 `active: true` 恢复）；组织有子组织或人员时 `409`；用户组解除成员后删除 |
| 错误格式 | SCIM Error（`schemas`、`status`、`scimType`、`detail`），`Content-Type: application/scim+json` |

接入步骤：管理员在控制台「身份源管理」添加类型为 **通用 SCIM** 的身份源 → 生成同步令牌 → 第三方按「组织 → 人员 → 用户组」顺序推送。完整的字段说明、示例和错误码见对外文档站的「身份源同步」页（`/docs/directory-sync.html`），设计见 `docs/scim-identity-source-design.md`。

管理端接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/identity-sources/{id}/scim-token` | 令牌状态、SCIM 基础地址、最近推送时间、本源数据量 |
| POST | `/api/v1/identity-sources/{id}/scim-token` | 生成或重新生成令牌，明文只返回一次 |
| DELETE | `/api/v1/identity-sources/{id}/scim-token` | 吊销令牌 |

### 8.5 由 IAM 主动拉取

如果你要同步的是钉钉、飞书、企业微信这类外部通讯录，也可以由 IAM 侧配置身份源连接器主动拉取，无需你在上游改造。该方式由管理员在控制台完成配置，你只需提供外部系统的应用凭据。

---

## 9. 管理 API 能力清单

以下接口均需要携带访问令牌。完整路径前缀为 `{Base URL}`。

### 9.0 控制台权限模型

管理接口按模块授权，权限点编码为 `iam:<模块>:<read|write>`，服务启动时自动写入权限表。拥有 `write` 即隐含 `read`。

| 模块 | 权限点 | 覆盖接口 |
| --- | --- | --- |
| 概览 | `iam:dashboard:read` | `/api/v1/dashboard/**` |
| 用户与组织 | `iam:user:read` / `iam:user:write` | 用户、组织、用户组、SCIM |
| 角色与权限 | `iam:role:read` / `iam:role:write` | 角色、权限、角色授权、`/api/v1/users/role-assignments` |
| 应用 | `iam:application:read` / `iam:application:write` | 应用、应用分组、访问申请、令牌、签名密钥 |
| 身份源 | `iam:identity-source:read` / `iam:identity-source:write` | `/api/v1/identity-sources/**` |
| 认证 | `iam:authentication:read` / `iam:authentication:write` | 认证源、认证策略、会话、认证事件 |
| 安全 | `iam:security:read` / `iam:security:write` | 安全设置、风险规则 |
| 系统 | `iam:system:read` / `iam:system:write` | 系统参数、租户、文件上传 |
| 审计 | `iam:audit:read` | `/api/v1/audit-events/**` |

规则：

- `GET` 请求需要模块的 `read` 权限点，其余方法需要 `write` 权限点；未归入任何模块的管理接口仅 IAM 管理员可访问。
- 持有 `iam_admin` 角色（直接授予或通过用户组继承）的用户是 IAM 管理员，拥有全部权限点。
- 防提权：凡是会改变控制台权限归属的操作只允许 IAM 管理员执行，包括创建管理员账号、修改或重置控制台用户、变更携带控制台权限的角色或用户组、创建或授予 `iam:*` 权限点。
- 用户本人可访问自己的资料、MFA、第三方绑定、会话和授权记录，不需要控制台权限。
- `GET /api/v1/users/me/console-access` 返回当前用户的 `superAdmin` 标记和权限点列表，前端据此决定是否展示后台入口和菜单。


### 9.1 组织目录

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/organizations` | 组织列表 |
| GET | `/api/v1/organizations/tree` | 组织树 |
| GET | `/api/v1/organizations/{id}` | 组织详情 |
| GET | `/api/v1/organizations/{id}/users` | 组织成员，可包含子组织 |
| POST | `/api/v1/organizations` | 创建组织 |
| PUT | `/api/v1/organizations/{id}` | 更新组织 |
| DELETE | `/api/v1/organizations/{id}` | 删除组织 |

### 9.2 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/users` | 用户检索，支持租户、组织、状态、关键字过滤 |
| POST | `/api/v1/users` | 创建用户 |
| GET | `/api/v1/users/me` | 当前登录用户 |
| GET | `/api/v1/users/{id}` | 用户详情 |
| PUT | `/api/v1/users/{id}` | 更新用户 |
| POST | `/api/v1/users/{id}/activate` | 激活 |
| POST | `/api/v1/users/{id}/suspend` | 暂停 |
| POST | `/api/v1/users/{id}/lock` | 锁定 |
| POST | `/api/v1/users/{id}/depart` | 离职 |
| GET | `/api/v1/users/{id}/effective-access` | 用户的角色与权限 |
| POST | `/api/v1/users/{id}/password` | 设置密码 |
| POST | `/api/v1/users/me/password` | 当前用户修改密码 |
| POST | `/api/v1/users/me/avatar` | 当前用户上传头像（需先配置对象存储） |
| POST | `/api/v1/users/role-assignments` | 指派角色 |
| DELETE | `/api/v1/users/role-assignments` | 撤销角色 |
| POST | `/api/v1/users/group-memberships` | 加入用户组 |
| DELETE | `/api/v1/users/group-memberships` | 移出用户组 |

### 9.3 用户组、角色与权限

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/v1/access/groups` | 用户组列表 / 创建 |
| GET / PUT / DELETE | `/api/v1/access/groups/{id}` | 用户组详情 / 更新 / 删除 |
| GET | `/api/v1/access/groups/{id}/members` | 用户组成员 |
| POST | `/api/v1/access/groups/{id}/members` | 添加成员 |
| DELETE | `/api/v1/access/groups/{id}/members/{userId}` | 移除成员 |
| GET | `/api/v1/access/groups/{id}/effective-access` | 用户组的有效权限 |
| GET / POST | `/api/v1/access/roles` | 角色列表 / 创建 |
| GET / PUT | `/api/v1/access/roles/{id}` | 角色详情 / 更新 |
| GET | `/api/v1/access/roles/{id}/impact` | 角色影响范围 |
| POST / DELETE | `/api/v1/access/role-permissions` | 角色绑定 / 解绑权限 |
| GET / POST | `/api/v1/access/permissions` | 权限列表 / 创建 |
| GET / PUT | `/api/v1/access/permissions/{id}` | 权限详情 / 更新 |
| GET | `/api/v1/access/permissions/{id}/impact` | 权限影响范围 |
| POST / DELETE | `/api/v1/access/group-roles` | 用户组绑定 / 解绑角色 |

角色影响范围与权限影响范围用于在变更前评估影响面，会返回受影响的用户、用户组和权限清单。

### 9.4 应用与访问控制

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/access/applications` | 应用检索 |
| POST | `/api/v1/access/applications` | 创建应用 |
| GET / PUT | `/api/v1/access/applications/{id}` | 应用详情 / 更新 |
| POST | `/api/v1/access/applications/{id}/enable` | 启用应用 |
| POST | `/api/v1/access/applications/{id}/disable` | 停用应用 |
| DELETE | `/api/v1/access/applications/{id}` | 删除应用 |
| POST | `/api/v1/access/applications/{id}/client-secret` | 重置客户端密钥，明文只返回一次 |
| GET / POST | `/api/v1/access/applications/{id}/sso-config` | 查询 / 配置单点登录 |
| GET | `/api/v1/access/applications/{id}/roles` | 应用已绑定角色 |
| POST / DELETE | `/api/v1/access/application-roles` | 应用绑定 / 解绑角色 |
| GET | `/api/v1/access/applications/{id}/assignments` | 应用授权清单 |
| POST | `/api/v1/access/applications/{id}/assignments` | 新增授权 |
| POST | `/api/v1/access/applications/{id}/assignments/batch` | 批量授权（已存在的授权会重新启用并更新过期时间） |
| POST | `/api/v1/access/applications/{id}/assignments/batch-delete` | 批量取消授权 |
| DELETE | `/api/v1/access/applications/{id}/assignments/{assignmentId}` | 删除授权 |
| GET | `/api/v1/access/users/{id}/application-assignments` | 直接授予某用户的应用授权 |
| GET | `/api/v1/access/organizations/{id}/application-assignments` | 授予某组织的应用授权 |
| GET | `/api/v1/access/applications/{id}/access-decisions` | 访问决策 |
| GET | `/api/v1/access/applications/{id}/access-review` | 访问审查 |
| GET / POST | `/api/v1/access/applications/{id}/permissions` | 应用内权限点列表 / 新增 |
| PUT / DELETE | `/api/v1/access/applications/{id}/permissions/{permissionId}` | 更新 / 删除应用内权限点 |
| GET / POST | `/api/v1/access/applications/{id}/permission-roles` | 应用内角色列表 / 新增 |
| PUT / DELETE | `/api/v1/access/applications/{id}/permission-roles/{roleId}` | 更新 / 删除应用内角色 |
| GET / POST | `/api/v1/access/applications/{id}/permission-roles/{roleId}/members` | 应用内角色的授予对象 / 批量授予 |
| DELETE | `/api/v1/access/applications/{id}/permission-roles/{roleId}/members/{memberId}` | 撤销应用内角色 |
| GET | `/api/v1/access/applications/{id}/permission-decisions?userId=` | 查询用户在应用内的有效权限 |
| GET | `/api/v1/access/applications/{id}/admin-access` | 当前用户对该应用权限的管理级别 |
| GET | `/api/v1/access/applications/{id}/grantable-subjects?type=&keyword=` | 搜索可授予对象 |
| GET | `/api/v1/access/me/managed-applications` | 当前用户被委派管理的应用 |

**访问决策**用于判断某用户能否访问某应用：

```http
GET /api/v1/access/applications/{id}/access-decisions?userId=<用户 UUID>
```

```json
{
  "allowed": true,
  "reason": "direct_assignment",
  "assignmentId": "..."
}
```

`reason` 的取值说明：

| 取值 | 含义 |
| --- | --- |
| `application_disabled` | 应用已停用 |
| `user_not_active` | 用户状态异常，非正常在职状态 |
| `tenant_mismatch` | 用户不属于应用所属租户 |
| `all_access` | 应用授权范围为「全员可访问」 |
| `direct_assignment` | 通过针对该用户的直接授权 |
| `group_assignment` | 通过用户所属用户组的授权 |
| `organization_assignment` | 通过用户所在组织（或其上级组织）的授权 |
| `application_role` | 通过角色获得 |
| `no_assignment` | 没有任何授权 |

**授权范围**：应用的 `authorizationType` 为 `MANUAL`（默认，仅被授权的主体可访问）或 `ALL_ACCESS`（同租户内所有在职用户可访问）。

**授权对象**可以是用户、用户组或组织，`userId`、`groupId`、`organizationId` 必须且只能填写一个，并可选设置过期时间。授权给组织后，该组织及其所有下级组织的成员都可访问：

```json
{
  "userId": null,
  "groupId": null,
  "organizationId": "8f14e45f-...",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

**用户应用门户**：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/access/me/applications` | 当前用户可访问的应用 |
| GET | `/api/v1/access/me/requestable-applications` | 当前用户可申请的应用 |
| GET | `/api/v1/access/users/{id}/applications` | 指定用户可访问的应用 |

**应用访问申请与审批**：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/access/me/application-access-requests` | 用户提交申请 |
| GET | `/api/v1/access/me/application-access-requests` | 查询自己的申请 |
| POST | `/api/v1/access/me/application-access-requests/{id}/cancel` | 取消申请 |
| GET | `/api/v1/access/application-access-requests` | 申请列表，供审批使用 |
| POST | `/api/v1/access/application-access-requests/{id}/approve` | 审批通过 |
| POST | `/api/v1/access/application-access-requests/{id}/reject` | 驳回 |
| POST | `/api/v1/access/application-access-requests/{id}/cancel` | 取消 |

申请状态为 `PENDING`、`APPROVED`、`REJECTED`、`CANCELED`。审批时可通过 `assignmentExpiresAt` 指定授权到期时间。

### 9.5 身份源与同步

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/v1/identity-sources` | 身份源列表 / 创建 |
| GET / PUT / DELETE | `/api/v1/identity-sources/{id}` | 详情 / 更新 / 删除 |
| POST | `/api/v1/identity-sources/{id}/enable`、`/disable` | 启用 / 停用 |
| GET / POST | `/api/v1/identity-sources/{id}/connector` | 连接器配置 |
| POST | `/api/v1/identity-sources/{id}/connector/enable`、`/disable` | 连接器启停 |
| GET / POST | `/api/v1/identity-sources/{id}/sync-jobs` | 同步任务列表 / 创建 |
| GET / PUT | `/api/v1/identity-sources/sync-jobs/{id}` | 任务详情 / 更新 |
| POST | `/api/v1/identity-sources/sync-jobs/{id}/enable`、`/disable` | 任务启停 |
| POST | `/api/v1/identity-sources/sync-jobs/{id}/runs` | 立即触发同步 |
| GET | `/api/v1/identity-sources/sync-jobs/{id}/runs` | 运行历史 |
| GET | `/api/v1/identity-sources/sync-runs/{id}` | 运行详情 |

支持的身份源类型包括本地目录、LDAP、AD、钉钉、企业微信、飞书和 SCIM。同步任务可配置执行周期，也可按需手动触发。运行结果包含新增和更新的用户数、用户组数及失败原因。

### 9.6 认证策略与认证源

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/v1/authentication-policies` | 认证策略列表 / 创建 |
| PUT | `/api/v1/authentication-policies/{id}` | 更新策略 |
| POST | `/api/v1/authentication-policies/{id}/enable`、`/disable` | 策略启停 |
| POST | `/api/v1/authentication-policies/evaluations` | 策略评估 |
| GET / POST | `/api/v1/authentication-providers` | 认证源列表 / 创建 |
| GET / PUT / DELETE | `/api/v1/authentication-providers/{id}` | 详情 / 更新 / 删除 |
| POST | `/api/v1/authentication-providers/{id}/enable`、`/disable` | 认证源启停 |

认证策略可配置是否强制多因素认证、密码强度与有效期、连续失败锁定、风险等级触发条件等。策略评估接口用于在登录过程中判断当前登录是否需要额外认证或直接拒绝。

认证源用于配置微信、QQ、飞书、钉钉、GitHub 等第三方登录方式。

### 9.7 风险控制

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/v1/risk/rules` | 风险规则列表 / 创建 |
| GET / PUT | `/api/v1/risk/rules/{id}` | 规则详情 / 更新 |
| POST | `/api/v1/risk/rules/{id}/enable`、`/disable` | 规则启停 |
| POST | `/api/v1/risk/assessments` | 发起风险评估 |
| GET | `/api/v1/risk/assessments` | 评估历史 |
| GET | `/api/v1/risk/assessments/{id}` | 评估详情 |

风险评估支持传入 IP、User-Agent、设备指纹和地理位置，返回风险等级、命中的规则以及处置建议。

登录时会按启用的规则实时评估：高风险直接拒绝登录，中风险要求 MFA 二次认证（用户未绑定 MFA 时放行并记录）。目前登录链路只带 IP、User-Agent 和失败次数，设备指纹与地理位置类规则仅在调用 `/api/v1/risk/assessments` 时生效。

### 9.8 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/audit-events` | 审计事件查询 |
| GET | `/api/v1/audit-events/export` | 导出 CSV |
| GET | `/api/v1/audit-events/{id}` | 事件详情 |

支持按操作者、动作、目标资源、时间范围和关键字过滤。该接口支持分页，参数为 `page`（从 1 开始）和 `limit`（默认 100，导出默认 500）。

所有管理操作都会自动记录审计事件（含客户端 IP 与 User-Agent），接入方无需自行上报。

### 9.9 仪表盘

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/dashboard/summary` | 概览统计 |
| GET | `/api/v1/dashboard/metrics` | 分类指标 |
| GET | `/api/v1/dashboard/recent-risk-assessments` | 最近风险评估 |
| GET | `/api/v1/dashboard/recent-sync-runs` | 最近同步运行 |

概览统计包含用户数、活跃用户数、组织数、应用数、身份源数、活跃会话数、高风险评估数、失败同步数和待处理申请数。

### 9.10 系统设置与租户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/v1/settings` | 系统配置列表 / 新增或更新 |
| GET / DELETE | `/api/v1/settings/{key}` | 配置详情 / 删除 |
| GET / POST | `/api/v1/tenants` | 租户列表 / 创建 |
| GET / PUT | `/api/v1/tenants/{id}` | 租户详情 / 更新 |
| POST | `/api/v1/tenants/{id}/activate`、`/suspend` | 租户启停 |
| GET / POST | `/api/v1/tenants/{id}/settings` | 租户配置 |
| GET / DELETE | `/api/v1/tenants/{id}/settings/{key}` | 租户配置详情 / 删除 |
| GET / PUT | `/api/v1/security-settings/general` | 通用安全设置 |
| GET / PUT | `/api/v1/security-settings/password-policy` | 密码策略 |

通用安全设置包含并发会话数（超出时踢掉最早的会话）、会话有效期、“记住我”时长、验证码有效期、登录失败统计窗口与锁定阈值、自动解锁时间以及内容安全策略（CSP），保存后即时生效。密码策略包含长度、复杂度、连续重复字符、个人信息与弱密码检查、连续字符与键盘序列、历史密码、有效期与到期提醒；最小长度以此处配置为准，不符合时返回中文提示。

外部服务的连通性检查与运维接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/settings/message/mail/test` | 用已保存的邮件配置和指定模板发送测试邮件 |
| POST | `/api/v1/settings/message/sms/test` | 用已保存的短信配置发送测试短信 |
| GET | `/api/v1/settings/geo-ip/lookup` | 按当前地理库解析指定 IP |
| POST | `/api/v1/settings/geo-ip/update` | 用 MaxMind License Key 下载 GeoLite2-City 数据库 |
| POST | `/api/v1/settings/storage/validate` | 用已保存的对象存储配置写入探测文件 |

### 9.11 令牌与签名密钥运维

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/oauth/tokens` | 已签发的令牌列表 |
| GET | `/api/v1/oauth/tokens/{type}/{id}` | 令牌详情 |
| POST | `/api/v1/oauth/tokens/{type}/{id}/revoke` | 强制撤销令牌 |
| GET | `/api/v1/jwt-signing-keys` | 签名密钥列表 |
| POST | `/api/v1/jwt-signing-keys/rotations` | 轮换签名密钥 |
| POST | `/api/v1/jwt-signing-keys/{id}/retire` | 退役签名密钥 |

### 9.12 其它登录方式

除账号密码外，还支持：

| 方式 | 接口 |
| --- | --- |
| 短信验证码登录 | `POST /api/v1/authentication/sms-codes`、`POST /api/v1/authentication/mobile-login` |
| 第三方登录 | `GET /api/v1/authentication/third-party/{providerKey}/authorize`、`POST .../callback` |
| 多因素认证 | `POST /api/v1/users/{id}/mfa-challenges`、`POST /api/v1/users/mfa-challenge-verifications` |
| 密码重置 | `POST /api/v1/users/password-reset-tickets`，用户凭票据重置 |
| 会话管理 | `GET /api/v1/authentication/sessions`、`POST /api/v1/authentication/sessions/end` |

---

## 10. 典型业务场景

### 10.1 新应用接入单点登录

1. 向管理员提供应用名称、回调地址，获取 `client_id` 和 `client_secret`。
2. 读取发现文档，确认各项端点地址。
3. 未登录用户引导至 `{Base URL}/oauth2/consent` 授权页。
4. 在回调地址中校验 `state`，取出 `code`。
5. 用授权码换取令牌，并安全保存 `refreshToken`。
6. 用 `userinfo` 获取用户标识 `sub`，建立本地会话。
7. 如果平台配置了 PKCE，补上 `code_challenge` 与 `code_verifier`。

### 10.2 判断用户能否访问某应用

```http
GET /api/v1/access/applications/{id}/access-decisions?userId=<用户 UUID>
```

根据返回的 `allowed` 决定是否放行，`reason` 可用于给出提示文案或排查授权来源。

### 10.3 读取用户在组织中的位置与权限

1. `GET /api/v1/users/{id}` 获取用户所属的组织与用户组。
2. `GET /api/v1/organizations/tree` 获取完整组织树，定位用户所在层级。
3. `GET /api/v1/users/{id}/effective-access` 获取用户最终生效的角色与权限。

### 10.4 从上游系统同步通讯录

1. 确认上游系统支持 SCIM 2.0，获取访问令牌。
2. 调用 `GET /scim/v2/ResourceTypes` 与 `GET /scim/v2/Schemas` 确认字段要求。
3. 按组织、用户、用户组的顺序创建资源。
4. 使用 `filter` 查询确认同步结果。

若上游是钉钉、飞书或企业微信，直接由管理员配置身份源连接器更省事。

### 10.5 实现应用权限自助申请

用户侧：

1. `GET /api/v1/access/me/requestable-applications` 获取可申请的应用列表。
2. `POST /api/v1/access/me/application-access-requests` 提交申请。
3. `GET /api/v1/access/me/application-access-requests` 查看进度。

审批侧：

4. `GET /api/v1/access/application-access-requests?status=PENDING` 拉取待审批列表。
5. `POST /api/v1/access/application-access-requests/{id}/approve` 审批通过，可指定授权到期时间。
6. 用户再次调用 `/me/applications` 即可看到新授权的应用。

### 10.6 用户离职后的访问回收

1. `POST /api/v1/users/{id}/depart` 标记离职。系统会自动禁用针对该用户的直接应用授权，并结束其活跃登录会话、撤销已签发的令牌。
2. `GET /api/v1/access/applications/{id}/access-review` 逐个应用核对是否还有残留授权。
3. 对于通过用户组获得的访问，调用 `DELETE /api/v1/access/groups/{groupId}/members/{userId}` 将该用户移出用户组。

### 10.7 排查某次操作

```http
GET /api/v1/audit-events?actor=zhangsan&from=2026-09-01T00:00:00Z&to=2026-09-17T00:00:00Z
```

返回包含操作者、动作、目标资源和发生时间。审计事件详情中包含操作上下文。

---

## 11. 常见问题

**访问令牌过期怎么办？**

重新调用登录接口获取新令牌。令牌有效期为 8 小时，建议在本地缓存并复用。

**为什么请求返回 401？**

可能是未携带访问令牌、令牌已过期或令牌被撤销。401 不保证返回结构化错误信息，请以状态码判断。

**列表接口支持分页吗？**

组织、用户、角色、应用等管理接口的列表返回完整结果，不支持分页，请使用过滤条件缩小范围。审计事件接口支持分页。SCIM 接口支持 `startIndex` 和 `count`。

**SCIM 更新用户时提示方法不支持？**

SCIM 目前支持查询与创建。更新用户、用户组、组织请使用第 9 章的管理 API。

**回调时报回调地址不匹配？**

请求中的回调地址必须与管理员登记的完全一致，包括协议、域名、端口和路径。

**登录后一直跳回授权同意页面？**

授权页同时承担登录入口与授权确认两个职责，因此每次从这个入口进入都会看到授权确认。用户点击「同意授权」后即可正常跳回你的应用，不会重复创建授权记录。

**如何调整令牌有效期？**

令牌有效期由平台统一管理，如需变更请联系管理员。

**接口文档在哪里？**

`{Base URL}/swagger-ui/index.html`。

**接口文档里显示的鉴权方式与本文档不一致？**

以本文档为准，管理 API 统一使用 `Authorization: Bearer <访问令牌>`。

**用标准 OIDC 客户端库接入时，跳转授权地址后返回 401？**

发现文档中的 `authorization_endpoint` 面向已登录的调用方。浏览器发起的登录请使用授权页 `{Base URL}/oauth2/consent`：多数客户端库支持自定义授权端点，按 5.3 节的参数拼接即可；若客户端库不支持覆盖，可由你的应用自行完成跳转与回调处理。

**短信验证码登录收不到验证码？**

短信通道由管理员配置。测试环境可能使用固定验证码，请向管理员确认。

---

## 12. 接入自查清单

上线前逐条确认：

- [ ] 已确认接入地址与回调地址，并完成登记
- [ ] 回调地址在授权请求与换取令牌时保持一致
- [ ] 未登录用户已引导至 `{Base URL}/oauth2/consent` 授权页
- [ ] 授权请求携带了 `state`，并在回调时完成校验
- [ ] 已实现授权码换令牌，并妥善保存刷新令牌
- [ ] 已校验身份令牌签名与 `iss`、`aud`、`exp`
- [ ] 访问令牌做了缓存，未在每次请求前重复登录
- [ ] 收到 401 时只重试一次，没有无限循环
- [ ] 管理 API 列表使用了过滤参数，未拉取全量数据
- [ ] 枚举值使用了大写形式
- [ ] 时间字段使用 ISO-8601 UTC 格式
- [ ] SCIM 同步按组织、用户、用户组的顺序执行
- [ ] 已确认用户离职后访问回收的覆盖范围
