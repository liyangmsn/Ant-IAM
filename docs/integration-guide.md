# Ant IAM 第三方应用接入指南

本文档面向需要接入 Ant IAM 的第三方应用开发者，说明系统提供哪些接入能力、如何取得凭据、以及如何完成单点登录与通讯录同步。具体接入地址、应用凭据和回调地址由管理员在你的环境中分配。

## 目录

| 章节 | 内容 |
| --- | --- |
| 1 | 接入前准备 |
| 2 | 系统能提供什么 |
| 3 | 选择接入方式 |
| 4 | 通用调用约定 |
| 5 | 单点登录接入（OIDC / OAuth2） |
| 6 | 单点登录接入（SAML 2.0） |
| 7 | 单点登录接入（CAS） |
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
| 统一登录 | 用户一次登录即可访问所有接入的应用，支持 OIDC、SAML 2.0、CAS 三种协议 |
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
| `/oauth2/userinfo` | 用户信息（需携带 access_token） |
| `/saml2/metadata`、`/saml2/metadata.xml` | SAML 元数据 |
| `/cas/serviceValidate`、`/cas/p3/serviceValidate` | CAS 票据校验 |
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
| `authorization_endpoint` | 授权地址，供已登录的调用方使用，见 5.9 节 |
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
GET {Base URL}/oauth2/consent
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
- 用户此前已授权过时，页面仍会展示授权范围，用户确认后直接跳回你的回调地址。

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
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshToken": "...",
  "idToken": "...",
  "scope": "openid profile email"
}
```

| 令牌 | 用途 | 有效期 |
| --- | --- | --- |
| `accessToken` | 调用用户信息接口、资源接口 | 1 小时 |
| `refreshToken` | 换取新的访问令牌 | 30 天 |
| `idToken` | 身份令牌，包含用户标识，使用 RS256 签名 | 1 小时 |

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

校验结果中的 `active` 表示令牌是否仍然有效。

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

---

## 7. 单点登录接入（CAS）

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
| 用户 | 查询列表、查询详情、创建 |
| 用户组 | 查询列表、查询详情、创建 |
| 组织 | 查询列表、查询详情、创建 |

目前 SCIM 支持查询与创建。若需要更新或删除，请改用第 9 章的管理 API。

列表支持 `filter`、`startIndex`、`count` 三个参数，返回标准的 SCIM `ListResponse` 结构，包含 `totalResults`、`startIndex`、`itemsPerPage` 和 `Resources`。

### 8.3 过滤语法

格式为 `属性 操作符 "值"`：

| 操作符 | 含义 |
| --- | --- |
| `eq` | 等于 |
| `co` | 包含 |
| `sw` | 以……开头 |

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
```

使用其它属性或不支持的写法会返回 `400`。

### 8.4 另一种同步方式

如果你要同步的是钉钉、飞书、企业微信这类外部通讯录，也可以由 IAM 侧配置身份源连接器主动拉取，无需你在上游改造。该方式由管理员在控制台完成配置，你只需提供外部系统的应用凭据。

---

## 9. 管理 API 能力清单

以下接口均需要携带访问令牌。完整路径前缀为 `{Base URL}`。

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
| GET / POST | `/api/v1/access/applications/{id}/sso-config` | 查询 / 配置单点登录 |
| GET | `/api/v1/access/applications/{id}/roles` | 应用已绑定角色 |
| POST / DELETE | `/api/v1/access/application-roles` | 应用绑定 / 解绑角色 |
| GET | `/api/v1/access/applications/{id}/assignments` | 应用授权清单 |
| POST | `/api/v1/access/applications/{id}/assignments` | 新增授权 |
| DELETE | `/api/v1/access/applications/{id}/assignments/{assignmentId}` | 删除授权 |
| GET | `/api/v1/access/applications/{id}/access-decisions` | 访问决策 |
| GET | `/api/v1/access/applications/{id}/access-review` | 访问审查 |

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
| `direct_assignment` | 通过针对该用户的直接授权 |
| `group_assignment` | 通过用户所属用户组的授权 |
| `application_role` | 通过角色获得 |
| `no_assignment` | 没有任何授权 |

**授权对象**可以是用户，也可以是用户组，二者必填其一，并可选设置过期时间：

```json
{
  "userId": "8f14e45f-...",
  "groupId": null,
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

### 9.8 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/audit-events` | 审计事件查询 |
| GET | `/api/v1/audit-events/export` | 导出 CSV |
| GET | `/api/v1/audit-events/{id}` | 事件详情 |

支持按操作者、动作、目标资源、时间范围和关键字过滤。该接口支持分页，参数为 `page`（从 1 开始）和 `limit`（默认 100，导出默认 500）。

所有管理操作都会自动记录审计事件，接入方无需自行上报。

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

通用安全设置包含并发会话数、会话有效期、登录失败锁定阈值、自动解锁时间等。密码策略包含长度、复杂度、有效期、历史密码校验等。

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
