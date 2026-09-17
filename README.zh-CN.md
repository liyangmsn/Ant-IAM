# Ant IAM

Ant IAM 是一套基于 Spring Boot 4 全新开发的独立企业级 IAM / IDaaS 后端项目，覆盖组织目录、用户生命周期、用户组、RBAC、应用访问、身份源同步和审计等能力。

第三方应用接入指南见 [docs/integration-guide.md](docs/integration-guide.md)，其中包含接入方式选择、单点登录与通讯录同步流程、接口能力清单和常见问题。

## 技术栈

- Java 25
- Spring Boot 4.0.6
- Spring WebMVC、Spring Security、Spring Data JPA
- PostgreSQL
- Liquibase 数据库迁移
- Lombok

## 当前模块

- 组织目录、组织详情、组织树、组织成员和组织更新：`/api/v1/organizations`、`/api/v1/organizations/{organizationId}`、`/api/v1/organizations/tree`、`/api/v1/organizations/{organizationId}/users`
- 用户、用户资料、目录检索与生命周期操作：`/api/v1/users`、`/api/v1/users/{userId}`、`/api/v1/users/me`，列表接口支持 `tenantId`、`organizationId`、`status` 和 `keyword` 过滤
- 用户有效角色与权限：`/api/v1/users/{userId}/effective-access`
- 用户组成员清单：`/api/v1/access/groups/{groupId}/members`
- 用户组有效角色与权限：`/api/v1/access/groups/{groupId}/effective-access`
- 权限影响分析：`/api/v1/access/permissions/{permissionId}/impact`
- 角色影响分析：`/api/v1/access/roles/{roleId}/impact`
- 用户组、角色、权限、访问控制资源检索/详情/更新、应用角色绑定和应用生命周期：`/api/v1/access/**`；应用支持 `tenantId`、`enabled` 和 `keyword` 过滤
- 应用 SSO 配置和授权生命周期，支持可选授权过期时间：`/api/v1/access/applications/{applicationId}/**`
- 应用访问决策，支持直接授权、用户组授权和应用角色来源：`/api/v1/access/applications/{applicationId}/access-decisions?userId=...`
- 用户应用门户列表：`/api/v1/access/users/{userId}/applications`
- 当前用户应用门户列表：`/api/v1/access/me/applications`
- 当前用户可申请应用列表：`/api/v1/access/me/requestable-applications`
- 应用访问申请与审批流程：`/api/v1/access/application-access-requests`
- 当前用户应用访问申请：`/api/v1/access/me/application-access-requests`
- 应用访问审查列表：`/api/v1/access/applications/{applicationId}/access-review`
- 用户暂停、锁定和离职时会自动禁用直接应用授权、结束活跃认证会话，并撤销有效 OAuth2 token。
- 身份源详情、检索、更新和生命周期控制：`/api/v1/identity-sources`、`/api/v1/identity-sources/{identitySourceId}`，列表支持 `tenantId`、`type`、`enabled` 和 `keyword` 过滤
- 身份源连接器支持生命周期控制，同步任务支持任务详情、更新和生命周期控制：`/api/v1/identity-sources/{identitySourceId}/connector`、`/api/v1/identity-sources/{identitySourceId}/sync-jobs`、`/api/v1/identity-sources/sync-jobs/{syncJobId}`
- 手动身份同步运行，支持 JSON 目录数据导入和运行详情：`/api/v1/identity-sources/sync-jobs/{syncJobId}/runs`、`/api/v1/identity-sources/sync-runs/{syncRunId}`
- 身份源实时事件回调，支持 HMAC-SHA256 验签：`/api/v1/synchronizer/event_receive/{sourceCode}`
- 邮件发信能力，支持配置 SMTP 服务和模板渲染，并接入邮箱 MFA 挑战
- 文件上传，提供阿里云 OSS、腾讯云 COS、七牛云 Kodo 和 S3 兼容服务的原生适配：`/api/v1/files`
- 基于 MaxMind 数据库的 IP 地理库解析，系统默认模式提供地址段分类
- 认证策略，支持生命周期控制、MFA、注册要求、风险升阶、拒绝、密码最小长度、失败登录锁定、密码过期和密码历史校验：`/api/v1/authentication-policies`
- 认证策略评估：`/api/v1/authentication-policies/evaluations`
- 登录风险规则支持检索/详情/更新/生命周期控制，风险评估支持检索/详情、设备指纹和地理位置上下文：`/api/v1/risk/rules`、`/api/v1/risk/rules/{ruleId}`、`/api/v1/risk/assessments`、`/api/v1/risk/assessments/{assessmentId}`
- 仪表盘摘要、指标和时间范围统计（认证量趋势、应用访问排名、热门认证方式、登录位置分布）：`/api/v1/dashboard/summary`、`/api/v1/dashboard/metrics`、`/api/v1/dashboard/statistics`
- 系统设置支持检索/过滤、详情和删除：`/api/v1/settings`、`/api/v1/settings/{settingKey}`
- 租户和租户设置，租户设置支持检索/详情/删除：`/api/v1/tenants`、`/api/v1/tenants/{tenantId}/settings`、`/api/v1/tenants/{tenantId}/settings/{settingKey}`
- 认证会话活跃/历史筛选、强制登出和可检索认证事件，包含登录风险与登出事件：`/api/v1/authentication/**`
- 密码凭据、MFA 因子详情/更新/生命周期控制和 TOTP 校验：`/api/v1/users/**`
- 密码重置票据支持检索、详情和作废：`/api/v1/users/password-reset-tickets`、`/api/v1/users/password-reset-tickets/{ticketId}`
- 自助修改密码：`/api/v1/users/me/password`
- MFA 挑战支持检索/详情，恢复码支持生成：`/api/v1/users/{userId}/mfa-challenges`、`/api/v1/users/mfa-challenges`、`/api/v1/users/{userId}/mfa-recovery-codes`、`/api/v1/users/mfa-challenge-verifications`
- SCIM 2.0 用户、用户组和组织，支持列表、创建、详情、过滤和分页：`/scim/v2/Users`、`/scim/v2/Groups`、`/scim/v2/Organizations`
- SCIM 2.0 发现接口：`/scim/v2/ServiceProviderConfig`、`/scim/v2/ResourceTypes`、`/scim/v2/Schemas`
- OAuth2 授权端点：`/oauth2/authorize`
- OAuth2 token 端点：`/oauth2/token`
- OIDC discovery 和 userinfo：`/.well-known/openid-configuration`、`/oauth2/userinfo`
- OIDC JWKS 和 RS256 ID token 签名：`/oauth2/jwks`
- 应用 SSO 配置上的 OIDC ID token claim 策略
- JWT 签名密钥管理，支持筛选、详情、轮换和受保护退役：`/api/v1/jwt-signing-keys`、`/api/v1/jwt-signing-keys/{keyId}`
- OAuth2 PKCE 和 refresh token 支持
- OAuth2 refresh token 轮换和撤销：`/oauth2/revocations`
- OAuth2 token introspection 和客户端认证撤销：`/oauth2/introspect`、`/oauth2/revoke`
- OAuth2 access/refresh token 清单、详情和管理员撤销：`/api/v1/oauth/tokens`、`/api/v1/oauth/tokens/{tokenType}/{tokenId}`
- OAuth2 consent 管理，支持筛选、详情和撤销：`/oauth2/consents`、`/oauth2/consents/{consentId}`
- SAML2 metadata 和 XML SSO assertion：`/saml2/metadata`、`/saml2/metadata.xml`、`/saml2/sso`、`/saml2/sso/xml`
- CAS 登录和服务票据校验，支持 XML 响应：`/cas/login`、`/cas/serviceValidate`、`/cas/p3/serviceValidate`
- 审计事件详情、搜索，支持关键字过滤和 CSV 导出：`/api/v1/audit-events`、`/api/v1/audit-events/{auditEventId}`、`/api/v1/audit-events/export`
- 公共能力目录：`/api/v1/catalog`
- OpenAPI JSON 文档，便于集成工具直接读取：`/v3/api-docs`
- Swagger UI 交互式接口文档：`/swagger-ui/index.html`
- 健康检查：`/actuator/health`

## 本地运行

方式一：只用 Docker 启动 PostgreSQL，然后在本机运行 API。

启动 PostgreSQL：

```bash
docker compose up -d postgres
```

运行 API：

```bash
mvnd spring-boot:run
```

如果本机没有 Maven Daemon，也可以使用本地 Maven：

```bash
mvn spring-boot:run
```

方式二：使用 Docker Compose 同时运行 PostgreSQL 和 API。

先构建应用 jar：

```bash
mvnd package -DskipTests
```

再启动 `api` profile：

```bash
docker compose --profile api up --build
```

当 API 容器进入 healthy 状态时，`http://localhost:8080/actuator/health` 会返回 `UP`。

默认 PostgreSQL 配置和 `docker-compose.yml` 保持一致：

```text
ANT_IAM_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ant_iam
ANT_IAM_DATASOURCE_USERNAME=ant_iam
ANT_IAM_DATASOURCE_PASSWORD=ant_iam
```

容器内 API 会使用 `jdbc:postgresql://postgres:5432/ant_iam` 连接 compose 网络中的 PostgreSQL。

账号密码登录会从数据库中的 `user_accounts` 和 `user_credentials` 读取用户与密码凭据。首次安装的空库会初始化默认管理员账号 `admin / admin123456`，应用不再通过配置文件读取默认登录账号。

短信验证码通过 sms4j 通道发送。默认启用 `fixed-code` 通道用于本地安装和联调，验证码为 `666666`，可通过 `ANT_IAM_SMS_FIXED_CODE` 覆盖；生产环境可以配置其它 sms4j 通道，并通过 `ANT_IAM_SMS_BLEND_ID` 切换。

OpenAPI JSON、Swagger UI、健康检查、OIDC discovery、JWKS、SAML metadata、CAS validation 以及 OAuth2 token/introspection/revocation 端点不需要登录，方便协议客户端直接访问；管理类 API 统一使用登录接口签发的 Bearer session token。

## 前端项目

前端项目已放在当前仓库子目录：

- 统一前端：`Ant-IAM-Frontend`

前端使用 Vite、React、TypeScript、Tailwind CSS 和 Ant Design 构建。默认连接 `http://localhost:8080` 后端，通过 `/login` 统一登录后使用 Bearer session token 访问门户和控制台。

安装依赖：

```bash
cd Ant-IAM-Frontend
pnpm install
```

启动开发服务：

```bash
pnpm run dev
```

前端默认运行在 `http://localhost:5174/`，门户路径为 `/portal`，控制台路径为 `/console`。

生产构建：

```bash
pnpm run build
```

## 示例

```bash
TOKEN=$(curl -s \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123456"}' \
  http://localhost:8080/api/v1/authentication/password-login | jq -r '.session.sessionIndex')

curl -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"code":"hq","name":"总部"}' \
  http://localhost:8080/api/v1/organizations
```

身份源连接器配置可以通过 JSON 导入目录数据，钉钉和飞书连接器也支持把同样的结构放入 `payload` 字段用于本地联调：

```json
{
  "organizations": [
    {"code": "engineering", "name": "研发部"}
  ],
  "users": [
    {"username": "alice", "displayName": "Alice", "email": "alice@example.com", "organizationCode": "engineering"}
  ],
  "groups": [
    {"code": "admins", "name": "管理员", "members": ["alice"]}
  ]
}
```

钉钉连接器可配置开放平台凭据，运行同步任务时会拉取部门和部门用户：

```json
{
  "appKey": "ding-app-key",
  "appSecret": "ding-app-secret",
  "rootDeptId": 1,
  "fetchUserDetail": true
}
```

钉钉应用需要开通通讯录部门读取、成员读取权限。连接器优先使用钉钉官方 Java SDK 调用 `oapi` 通讯录能力；如需切换私有化或代理网关，可配置 `endpoint`。

飞书连接器可配置自建应用凭据，运行同步任务时会拉取部门和部门用户：

```json
{
  "appId": "cli_xxx",
  "appSecret": "feishu-app-secret",
  "rootDepartmentId": "0",
  "departmentIdType": "open_department_id"
}
```

飞书应用需要开通通讯录部门和用户读取权限。连接器优先使用飞书官方 Java SDK 调用 `contact/v3` 部门子节点和部门用户能力，按 `has_more` 与 `page_token` 自动翻页；如需切换私有化或代理网关，可配置 `endpoint`。

企业微信连接器可配置企业 ID 和通讯录 Secret，运行同步任务时会拉取部门和部门用户：

```json
{
  "corpId": "wwxxxxxxxx",
  "corpSecret": "wechat-work-contact-secret",
  "rootDeptId": 1
}
```

企业微信应用需要开通通讯录部门和成员读取权限。连接器调用企业微信官方通讯录 API；如需切换私有化或代理网关，可配置 `endpoint`。身份源同步任务填写 `cronExpression` 后会由后台调度器定时执行，默认每 60 秒扫描一次到期任务，可通过 `ANT_IAM_IDENTITY_SYNC_SCHEDULER_DELAY_MS` 对应配置调整扫描间隔。

第三方登录通过公开接口完成授权跳转和授权码回调；回调时应传回 `authorize` 返回的签名 `state`：

```bash
curl 'http://localhost:8080/api/v1/authentication/third-party/wechat/authorize?redirectUri=https://iam.example.com/callback&state=demo'
```

```bash
curl -H 'Content-Type: application/json' \
  -d '{"code":"auth-code","state":"authorize-response-state","redirectUri":"https://iam.example.com/callback"}' \
  http://localhost:8080/api/v1/authentication/third-party/wechat/callback
```

微信、QQ、飞书、钉钉认证源配置统一使用 `appId`、`appSecret`、`redirectUri`。飞书登录优先使用飞书官方 Java SDK 的 `authen/v1` 能力，钉钉登录优先使用钉钉官方 Java SDK 的 `sns/getuserinfo_bycode` 能力；微信和 QQ 当前使用官方 OAuth 接口直连。可选字段包括 `scope`、`usernameClaim`、`usernamePrefix`、`autoCreateUser`、`sessionTtlMinutes`、`stateTtlSeconds` 和各平台 endpoint 覆盖项。

## 路线图

- 强化 SAML2/CAS 适配器，支持 XML 签名和更完整的协议绑定校验。
- 增加面向用户的 MFA 注册和恢复引导页面。
- 增加 LDAP 和 AD 后台连接器。
- 在 JSON 同步执行器基础上继续补齐 LDAP/AD 连接器适配。
- 将 SMS 和 WebAuthn MFA 原型挑战码替换为生产级校验器。
- 扩展风险规则，支持地理速度和更丰富的自适应 MFA 动作。
