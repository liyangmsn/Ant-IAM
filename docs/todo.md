# 系统待办清单

本清单由「前端入口 / 配置项」与「后端实现」逐项对照得出，每条都标注了证据位置。最近一次对照：2026-10-08。

图例：`[ ]` 未开始 · `[~]` 仅原型或部分实现 · `[x]` 已完成

## 构建注意

- `pom.xml` 曾存在重复依赖声明（`spring-boot-starter-mail`、`s3`、`geoip2`），会导致 Maven 4 内核的工具（如 mvnd 2.0）直接拒绝构建，已于 2026-10-08 清理。
- `FixedCodeSmsSpringBootTest` 是 `@SpringBootTest`，需要本地 PostgreSQL（`localhost:5432`，库 `ant_iam`）可用，否则测试失败。

## P1 待生产化

- [~] **控制台多租户隔离与按钮级权限**
  - 现状：后端已按 `iam:<模块>:<read|write>` 校验接口；前端仅通用列表页（`CrudListPage`，如风险规则、认证策略）按 `write` 权限点隐藏操作按钮，其余页面（含应用详情的【应用权限】页）没有写权限时仍显示按钮，点击后返回 403。模块管理员仍能看到全部租户的数据。
  - 位置：`ant-iam-frontend/src/apps/console/components/CrudListPage.tsx`、`src/main/java/com/antiam/config/IamAuthorizationService.java`
  - 验收：管理员只能访问所属租户的数据；所有控制台页面按 `write` 权限点隐藏或禁用操作按钮。

- [~] **WebAuthn 校验器**
  - 现状：TOTP、短信（经 `SmsVerificationService` 真实下发）、邮件、恢复码均已生产化，挑战响应不回传明文；WebAuthn 仍生成数字挑战码并直接返回给客户端。
  - 位置：`src/main/java/com/antiam/service/MfaVerificationService.java`
  - 验收：WebAuthn 走标准注册与断言流程。

- [~] **SAML2 协议完整性**
  - 现状：断言已按签名密钥做 RSA-SHA256 enveloped 签名，元数据携带签名证书。
  - 缺口：不解析 SP 的 `AuthnRequest`，无 SLO，绑定仅一种。
  - 位置：`src/main/java/com/antiam/service/FederationService.java`、`src/main/java/com/antiam/common/SamlSignatures.java`
  - 验收：`saml2/sso` 可消费 SP 的 `AuthnRequest`，支持 SLO。

- [ ] **LDAP / AD 原生连接器**
  - 现状：`IdentitySourceType` 已定义 `LDAP`、`ACTIVE_DIRECTORY`。
  - 缺口：仅由 JSON payload 执行器处理，无 LDAP bind/search、无增量同步。
  - 位置：`src/main/java/com/antiam/service/identitysource/JsonIdentitySourceConnectorAdapter.java`
  - 验收：可用连接配置直连目录服务拉取组织与用户，支持分页与变更同步。

- [~] **风险规则：登录链路上下文与地理速度**
  - 现状：登录时按规则实时评估，高风险拒绝、中风险要求 MFA（用户未绑定 MFA 时放行）；IP、User-Agent、失败次数规则在登录时生效。
  - 缺口：登录调用 `evaluateLogin(..., null)`，不带设备指纹和地理位置，`DEVICE_FINGERPRINT_*`、`GEO_LOCATION_NOT_ALLOWED` 只在 `POST /api/v1/risk/assessments` 时生效；无不可能旅行/速度类规则。
  - 位置：`src/main/java/com/antiam/service/AuthenticationService.java`、`src/main/java/com/antiam/domain/RiskRuleType.java`
  - 验收：登录链路采集设备指纹与地理位置；新增地理速度规则。

- [ ] **失败次数规则可被滥用锁定他人**
  - 现状：`FAILED_LOGIN_COUNT` 统计最近 1 小时的失败事件，攻击者故意输错即可让正确密码的用户在 1 小时内被拒绝（若规则为高风险）。
  - 验收：按 IP + 账号维度统计，或命中后改为要求二次认证而非直接拒绝。

## P2 待补全

- [ ] **应用权限委派：存量导入与范围限制**
  - 现状：委派管理 P1–P3 已完成。
  - 缺口：P4 存量"人员—角色"数据导入（API + CSV + 试运行）；P5 授权管理员只能授予本部门人员。
  - 位置：`docs/application-permission-delegation-design.md` 第 4、6 节。


- [ ] **表单代填**
  - 现状：`ApplicationProtocol` 有 `FORM_FILL`，可保存 `formLoginTemplate`。
  - 缺口：无代填提交逻辑；`FORM_FILL` 仅作为本地登录会话的 protocol 标签。
  - 位置：`src/main/java/com/antiam/service/AuthenticationService.java`
  - 验收：模板可渲染并完成代填登录跳转。

## 已完成

- [x] 身份源实时同步回调：`POST /api/v1/synchronizer/event_receive/{sourceCode}`，HMAC-SHA256 验签后增量写入组织、用户和用户组。
- [x] 邮件能力：SMTP 发信与 `${变量}` 模板渲染，接入邮箱 MFA；`POST /api/v1/settings/message/mail/test` 发送测试邮件。
- [x] 短信能力：sms4j 承载，接入短信登录与短信 MFA；`POST /api/v1/settings/message/sms/test` 发送测试短信。
- [x] 对象存储：阿里云 OSS、腾讯云 COS、七牛云 Kodo 原生适配，S3 / MinIO / RustFS 走 S3 兼容适配（path-style）；移除了本地存储选项；`POST /api/v1/settings/storage/validate` 校验连通性；用户头像上传 `POST /api/v1/users/me/avatar`。
- [x] IP 地理库：MaxMind 解析国家与城市；注册码可保存，`POST /api/v1/settings/geo-ip/update` 在线下载 GeoLite2-City，`GET /api/v1/settings/geo-ip/lookup` 解析测试。
- [x] 控制台细粒度授权：`iam:<模块>:<read|write>` 权限点、URL 按模块校验、写操作防提权；`GET /api/v1/users/me/console-access` 供前端裁剪菜单。
- [x] 应用内权限：应用注册时声明权限点（控制台创建应用时填写，或以客户端凭据 `PUT /oauth2/permissions` 全量同步），权限点组合为应用内角色后授予用户、用户组或组织；应用通过 `POST /oauth2/permissions/check` 实时鉴权，`/oauth2/introspect` 与 `/oauth2/userinfo` 返回 `permissions`；控制台在应用详情【应用权限】维护，`/permission-decisions?userId=` 排查有效权限。数据使用独立的权限点与角色表，与全局角色权限体系隔离。
- [x] 通用 SCIM 身份源：第三方系统作为身份源，用同步令牌经 `/scim/v2/sources/{code}/**` 推送组织、人员和用户组，按身份源隔离数据，以 `externalId` 引用组织；控制台可生成、吊销令牌；对外文档见文档站「身份源同步」。设计见 `docs/scim-identity-source-design.md`。
- [x] 应用权限委派管理：每个应用内置「应用权限负责人」「授权管理员」两个角色（基于 `iam:` 保留权限点），委派管理员可在门户「我管理的应用」或业务应用自己的页面（`/oauth2/permission-admin/**`，客户端凭据 + 操作人令牌）管理本应用权限，无需控制台权限；含防提权、保留编码保护、最后一名负责人保护。设计见 `docs/application-permission-delegation-design.md`。
- [x] 前端页面不缓存：`deploy/rancher/nginx.conf` 的 `location /` 与 `/docs/` 均已设置 `Cache-Control: no-cache`，带 hash 的静态资源走 `/assets/`。
- [x] 应用访问策略：授权范围 `MANUAL` / `ALL_ACCESS`，授权对象支持用户、用户组、组织（下级组织继承），批量授权与取消；OIDC、SAML、CAS、JWT 单点登录签发前校验授权。
- [x] 应用访问申请与审批：门户自助申请，管理员审批后自动生成授权，可设到期时间。
- [x] 客户端密钥重置：`POST /api/v1/access/applications/{id}/client-secret`，明文只返回一次。
- [x] 通用安全设置全部生效：并发会话数（超出踢最早会话）、会话有效期、“记住我”时长、验证码有效期、失败统计窗口、自动解锁、内容安全策略（CSP 响应头）。
- [x] 密码策略：长度、复杂度、连续重复字符、个人信息、弱密码字典、连续数字/字母、键盘序列、历史密码、过期与提醒；最小长度以安全设置为准，提示信息为中文。
- [x] 账号锁定提示：锁定账号登录时提示“账号已被锁定，请稍后重试或联系管理员解锁”。
- [x] SCIM 2.0：用户、用户组、组织支持 `PUT` / `DELETE`，用户与用户组支持 `PATCH`；过滤支持 `eq/ne/co/sw/ew/pr` 与 `and/or/not`。
- [x] SAML 断言签名：RSA-SHA256 enveloped 签名，元数据携带证书。
- [x] 审计记录客户端 IP 与 User-Agent；登录错误信息中文化。
- [x] OAuth 访问令牌记录所属刷新令牌（`refresh_token_id`），刷新令牌轮换与撤销时联动处理。
- [x] JWT 单点登录签发与验签：`GET /jwt/sso?audience=`、`POST /jwt/verify`。
- [x] OAuth2 / OIDC consent 页面、JWKS 与 RS256 ID Token 签名。
- [x] 企业微信、钉钉、飞书后台连接器与身份源定时同步调度器。
