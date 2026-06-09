# Ant IAM

Ant IAM 是一套基于 Spring Boot 4 全新开发的企业级 IAM / IDaaS 后端项目，功能范围参考 TOPIAM，覆盖组织目录、用户生命周期、用户组、RBAC、应用访问、身份源同步和审计等能力。

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
- 认证策略，支持生命周期控制、MFA、注册要求、风险升阶、拒绝、密码最小长度、失败登录锁定、密码过期和密码历史校验：`/api/v1/authentication-policies`
- 认证策略评估：`/api/v1/authentication-policies/evaluations`
- 登录风险规则支持检索/详情/更新/生命周期控制，风险评估支持检索/详情、设备指纹和地理位置上下文：`/api/v1/risk/rules`、`/api/v1/risk/rules/{ruleId}`、`/api/v1/risk/assessments`、`/api/v1/risk/assessments/{assessmentId}`
- 仪表盘摘要和指标：`/api/v1/dashboard/summary`、`/api/v1/dashboard/metrics`
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
- OpenAPI JSON 文档：`/v3/api-docs`
- 健康检查：`/actuator/health`

## 本地运行

启动 PostgreSQL：

```bash
docker compose up -d postgres
```

运行 API：

```bash
mvnd spring-boot:run
```

如果本机没有 Maven Daemon，也可以使用 Maven Wrapper 或本地 Maven：

```bash
./mvnw spring-boot:run
mvn spring-boot:run
```

默认 Basic Auth 账号：

```text
admin / admin123456
```

可以通过 `ANT_IAM_ADMIN_USERNAME` 和 `ANT_IAM_ADMIN_PASSWORD` 覆盖默认账号密码。

## 示例

```bash
curl -u admin:admin123456 \
  -H 'Content-Type: application/json' \
  -d '{"code":"hq","name":"总部"}' \
  http://localhost:8080/api/v1/organizations
```

身份源连接器配置可以通过 JSON 导入目录数据：

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

## 路线图

- 强化 OAuth2/OIDC 端点，补充 consent UI 页面。
- 强化 SAML2/CAS 适配器，支持 XML 签名和更完整的协议绑定校验。
- 增加面向用户的 MFA 注册和恢复引导页面。
- 增加钉钉、企业微信、飞书、LDAP 和 AD 后台连接器。
- 在 JSON 同步执行器基础上增加原生 LDAP/AD/钉钉/企业微信/飞书连接器适配。
- 将 SMS、email 和 WebAuthn MFA 原型挑战码替换为生产级校验器。
- 扩展风险规则，支持地理速度和更丰富的自适应 MFA 动作。
