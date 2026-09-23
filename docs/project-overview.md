# Ant IAM 项目说明

本文面向产品、研发、运维与集成方，讲解系统的定位、作用范围与功能构成。接口清单见 `README.zh-CN.md`，第三方接入步骤见 `docs/integration-guide.md`，未完成事项见 `docs/todo.md`。

## 1. 项目是什么

系统是一套可独立部署的企业身份与访问管理（IAM / IDaaS）方案，由 Spring Boot 4 后端和 React 统一前端组成，为企业提供一套共用的身份底座：

- 一份统一的人员与组织目录：组织、用户、用户组、身份源。
- 一个统一的登录入口：密码、短信、第三方登录、MFA、单点登录。
- 一套统一的授权模型：角色、权限、应用授权、访问申请与审批。
- 一条可追溯的审计链路：登录会话、认证事件、管理操作。

业务系统不再各自维护账号，而是通过 OIDC/OAuth2、SAML 2.0、CAS 把登录交给本系统，通过 SCIM 2.0 或管理 API 读写目录数据。

一句话概括：**给企业内部所有应用提供统一的"账号目录 + 登录认证 + 权限授权 + 行为审计"底座。**

## 2. 它解决什么问题

| 场景痛点 | 对应做法 |
| --- | --- |
| 员工要记多套账号，各系统登录体验不一致 | 统一身份目录 + 单点登录（OIDC/OAuth2、SAML 2.0、CAS） |
| 组织架构散落在钉钉、飞书、企业微信、AD | 身份源连接器 + 定时或实时同步，汇聚成一份目录 |
| 人员入离调转后权限回收靠人工 | 停用、锁定、离职动作自动结束会话、撤销 OAuth2 令牌、停用直接应用授权 |
| 说不清"谁为什么能访问这个应用" | 应用访问决策（直接授权 / 用户组 / 应用角色三类来源）+ 角色与权限影响面分析 |
| 员工自助申请应用权限走线下流程 | 应用访问申请单 + 审批 + 可选的授权有效期 |
| 合规审计缺少证据 | 认证事件、会话记录、管理操作审计，支持关键字检索与 CSV 导出 |
| 异地或异常登录带来风险 | 登录风险规则、风险评分、设备指纹与地理位置上下文 |

## 3. 功能模块

### 3.1 组织与用户目录

- 组织：组织树的创建与维护、组织画像、按组织查询用户，接口位于 `/api/v1/organizations`。
- 用户：账号创建与更新、目录检索、个人资料，接口位于 `/api/v1/users`，列表支持 `tenantId`、`organizationId`、`status`、`keyword` 组合筛选。
- 用户组：组的增删改查与成员关系维护，成员与组的有效权限查询见 `/api/v1/access/groups/{groupId}/members`、`/api/v1/access/groups/{groupId}/effective-access`。
- 账号生命周期：`activate`、`suspend`、`lock`、`depart` 四个标准动作。停用、锁定、离职会自动停用用户的直接应用授权、结束活跃认证会话并撤销活跃 OAuth2 令牌。
- 有效权限：`/api/v1/users/{userId}/effective-access` 汇总用户经由直接授权、用户组、应用角色得到的角色与权限。

### 3.2 身份源与目录同步

- 身份源：`/api/v1/identity-sources` 管理身份源定义，支持 `LOCAL`、`LDAP`、`ACTIVE_DIRECTORY`、`DINGTALK`、`WECHAT_WORK`、`FEISHU`、`SCIM` 类型，列表支持 `tenantId`、`type`、`enabled`、`keyword` 筛选。
- 连接器：每个身份源可配置一个连接器，承载上游凭据与拉取参数。
- 同步任务与执行记录：`/api/v1/identity-sources/{id}/sync-jobs` 定义任务，`/api/v1/identity-sources/sync-jobs/{id}/runs` 手工触发并记录每次运行结果。
- 数据导入：运行同步任务时可提交 JSON 目录数据（组织、用户、用户组），钉钉、飞书、企业微信连接器则会调用各自开放平台接口拉取部门和成员。
- 定时同步：同步任务配置 `cronExpression` 后由后台调度器执行，默认每 60 秒扫描一次到期任务。
- 实时回调：`POST /api/v1/synchronizer/event_receive/{sourceCode}` 接收上游增量事件，使用连接器密钥做 HMAC-SHA256 验签。

### 3.3 认证与登录

系统自带认证方式与第三方认证源并存：

- 用户名密码登录：`POST /api/v1/authentication/password-login`，密码使用 BCrypt 存储，登录成功返回 Bearer session token。
- 短信快捷登录：`POST /api/v1/authentication/sms-codes` 下发验证码，`POST /api/v1/authentication/mobile-login` 完成登录。
- 第三方登录：微信、QQ、钉钉、飞书、企业微信、GitHub、Gitee、支付宝及自定义认证源，流程为 `authorize` 跳转 + `callback` 换取会话，`state` 带签名校验，可选自动建号。
- MFA 因子：`TOTP`、`SMS`、`EMAIL`、`WEBAUTHN`、`RECOVERY_CODE`，支持因子注册、启停、更新与恢复码生成。
- MFA 挑战：挑战创建、查询与校验，投递走已配置的邮件或短信通道。
- 单点登录会话：`/api/v1/authentication/**` 提供会话查询、活跃会话筛选、强制下线与认证事件查询。

### 3.4 认证策略、密码与安全设置

- 认证策略：`/api/v1/authentication-policies` 维护策略生命周期，可配置 MFA、MFA 注册引导、step-up 二次认证、拒绝登录、密码最小长度、失败登录锁定、密码过期、密码历史等要求；`/evaluations` 用于评估一次登录是否符合策略。
- 密码能力：自助改密 `/api/v1/users/me/password`，管理员设置密码，改密历史留痕。
- 找回密码：重置票据的创建、查询与撤销 `/api/v1/users/password-reset-tickets`，以及公开的票据核销端点。
- 安全设置：`/api/v1/settings` 与租户级 `/api/v1/tenants/{tenantId}/settings` 管理系统参数，支持字符串、布尔、数字、JSON 四类值。

### 3.5 应用管理与访问控制

- 应用：应用创建、更新、启用、停用、删除，支持应用分组；协议类型覆盖 `OIDC`、`OAUTH2`、`SAML2`、`CAS`、`JWT`、`FORM_FILL`。
- 应用 SSO 配置：`/api/v1/access/applications/{id}/sso` 维护单点登录参数，OIDC 应用还可配置 ID token 声明策略。
- 授权模型：权限（Permission）→ 角色（Role）→ 用户组（Group）→ 用户，角色可绑定权限，用户组可绑定角色，用户可加入用户组或直接获得角色。
- 应用授权：`/api/v1/access/applications/{id}/assignments` 维护应用授权，可为授权设置有效期。
- 访问决策：`/api/v1/access/applications/{id}/access-decisions?userId=...` 给出"该用户为什么能/不能访问该应用"，区分直接授权、用户组授权与应用角色三种来源。
- 访问复核：`/api/v1/access/applications/{id}/access-review` 输出应用下的授权清单，便于定期复核。
- 影响面分析：`/api/v1/access/permissions/{permissionId}/impact`、`/api/v1/access/roles/{roleId}/impact` 回答"改动这个权限或角色会影响谁"。
- 用户门户视图：`/api/v1/access/users/{userId}/applications`、`/api/v1/access/me/applications`、`/api/v1/access/me/requestable-applications`。

### 3.6 应用访问申请与审批

- 申请：用户对可申请的应用发起访问申请，管理员也可代用户发起；单据状态包含待审、通过、驳回、取消。
- 审批：通过或驳回申请单，结果直接体现为应用授权。
- 查询：管理员视角 `/api/v1/access/application-access-requests`，用户视角 `/api/v1/access/me/application-access-requests`。

### 3.7 标准协议与集成能力

- OIDC / OAuth2：浏览器授权入口 `/oidc/authorize`，Bearer 授权 API `/oauth2/authorize`，以及 `/oauth2/token`、`/oauth2/userinfo`、`/.well-known/openid-configuration`、`/oauth2/jwks`；支持授权码、PKCE、刷新令牌轮换、令牌自省与撤销、同意（consent）管理。
- OIDC 签名：RS256 ID token，签名密钥支持查询、轮换与带保护的退役 `/api/v1/jwt-signing-keys`。
- SAML 2.0：`/saml2/metadata`、`/saml2/metadata.xml`、`/saml2/sso`、`/saml2/sso/xml`。
- CAS：`/cas/login`、`/cas/serviceValidate`、`/cas/p3/serviceValidate`，支持 XML 响应。
- SCIM 2.0：`/scim/v2/Users`、`/scim/v2/Groups`、`/scim/v2/Organizations` 提供列表、创建、详情，支持 filter 与分页；`/scim/v2/ServiceProviderConfig`、`/scim/v2/ResourceTypes`、`/scim/v2/Schemas` 提供发现能力。
- 令牌运维：`/api/v1/oauth/tokens` 查看 access/refresh token 清单，管理员可强制撤销。
- 开放文档：`/v3/api-docs`（OpenAPI JSON）、`/swagger-ui/index.html`（可交互调试）。

### 3.8 风险控制

- 风险规则：`/api/v1/risk/rules` 维护规则，类型包括 IP 包含、User-Agent 包含、失败登录次数、设备指纹包含、设备指纹变化、地理位置不允许，输出低/中/高三级风险。
- 风险上下文：规则命中记录与评估结果中包含设备指纹与地理位置，用于登录决策与事后排查。
- 审计联动：认证事件会记录登录风险结果，便于按风险维度复盘。

### 3.9 审计与运营视图

- 审计事件：`/api/v1/audit-events` 支持画像、关键字检索与 CSV 导出，覆盖认证、授权、管理与同步等操作。
- 仪表盘：`/api/v1/dashboard/summary`、`/metrics`、`/statistics` 提供认证趋势、应用访问排名、认证方式分布与登录地点分布。
- 在线用户：查看有效会话并支持会话下线。
- 健康检查：`/actuator/health`。

### 3.10 消息、存储与地理库

- 邮件：可配置 SMTP 服务与模板，按 `${变量}` 渲染，已接入 EMAIL 类型 MFA 挑战投递。
- 短信：基于 sms4j 的短信通道，默认提供固定验证码通道（`sms.blends.fixed-code.code`）。
- 文件存储：`POST /api/v1/files` 上传文件，按配置路由到阿里云 OSS、腾讯 COS、七牛 Kodo 或 S3 兼容存储。
- IP 地理库：基于 MaxMind 数据库解析 IP 的国家与城市，缺省为 system 模式的地址分类（本机 / 内网 / 公网）。

### 3.11 多租户

项目内置租户实体与租户级设置（`/api/v1/tenants`、`/api/v1/tenants/{tenantId}/settings`），用户、应用、身份源等主要列表接口支持 `tenantId` 过滤，可在同一套部署内隔离多个租户的数据与配置。

## 4. 系统组成

### 4.1 后端

后端位于仓库根目录，按 `web → service → repository → domain` 分层，`dto` 与 `mapper` 负责出入参映射：

- `web`：约 25 个 Controller，覆盖管理 API 与标准协议端点。
- `service`：24 个服务类，承载认证、访问控制、同步、联邦协议、风险、审计等领域逻辑。其中访问控制与用户管理是体量最大的两块。
- `repository` / `domain`：Spring Data JPA 仓储与实体，业务枚举包括账号状态、应用协议、身份源类型、MFA 因子类型、风险规则类型等。
- `config`：安全过滤链、会话令牌认证过滤器、OpenAPI、调度、默认管理员初始化。
- 数据库迁移：Liquibase，共 32 个 changelog，由 `db/changelog/db.changelog-master.yaml` 汇总。

安全模型：管理接口统一使用 Bearer session token；OIDC 发现文档、JWKS、SAML 元数据、CAS 校验、token/introspect/revoke、注册登录入口、SCIM 实时回调等端点按协议标准免鉴权放行，其余请求一律要求认证。

初始化：首次启动且用户表为空时创建默认管理员 `admin` / `admin123456`，正式环境必须立即修改。

### 4.2 前端

前端位于 `ant-iam-frontend`，同一套代码内置两个应用：

- 管理控制台 `/console`：总览、账户管理（组织与用户、用户组、身份源）、认证管理（身份提供商）、应用管理（应用列表、创建应用、应用分组）、行为审计、在线用户、安全设置（通用安全、密码策略）、系统设置（消息设置、IP 地理库、存储配置）。
- 用户门户 `/portal`：我的应用、账号（基本信息、账号安全、第三方绑定）、我的审计、我的会话。

两者通过 `/login` 统一登录，共享同一个 Bearer session token 会话。

### 4.3 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端语言 | Java 25 |
| 后端框架 | Spring Boot 4.0.6、Spring WebMVC、Spring Security、Spring Data JPA |
| 数据库 | PostgreSQL 18，Liquibase 管理结构，JPA `ddl-auto=validate` |
| 关键依赖 | MapStruct、Lombok、sms4j、钉钉/飞书 SDK、AWS S3 SDK、阿里云 OSS、腾讯 COS、七牛 Kodo、MaxMind GeoIP2、springdoc-openapi |
| 前端 | Vite、React、TypeScript、React Router 7、Ant Design / Pro Components、Tailwind CSS 4 |

## 5. 典型流程

**新应用接入单点登录**：控制台创建应用并选择协议模板 → 配置 SSO 参数与回调地址 → 为应用绑定角色 → 把用户或用户组授权到应用 → 业务系统按 OIDC / SAML 2.0 / CAS 对接，登录统一跳转到本系统完成。

**上游通讯录同步**：新建身份源并配置连接器（钉钉、飞书、企业微信凭据或 JSON 导入）→ 建立同步任务并设置 `cronExpression` → 后台调度器定时拉取，或由上游通过实时回调推送增量 → 组织、用户、用户组自动落库。

**用户自助申请应用权限**：用户在门户看到"可申请应用" → 提交访问申请 → 管理员审批 → 系统生成应用授权（可设有效期）→ 用户即时在门户看到该应用。

**员工离职后的访问回收**：管理员执行离职动作 → 账号停用 → 直接应用授权停用 → 活跃认证会话结束 → 活跃 OAuth2 令牌撤销 → 全过程写入审计事件。

## 6. 运行与部署

本地开发：

```bash
docker compose up -d postgres
mvn spring-boot:run          # 或 mvnd spring-boot:run
```

常用环境变量：`ANT_IAM_DATASOURCE_URL`、`ANT_IAM_DATASOURCE_USERNAME`、`ANT_IAM_DATASOURCE_PASSWORD`、`ANT_IAM_PORT`。

容器化运行：`docker compose --profile api up -d`；或先 `mvn package`，再用根目录 `Dockerfile` 构建镜像（分层解包，以非 root 用户运行，输出 `target/ant-iam-0.1.0-SNAPSHOT.jar`）。

前端：

```bash
cd ant-iam-frontend
pnpm install
pnpm run dev                 # http://localhost:5174，/console 与 /portal
pnpm run build               # 生产构建
```

## 7. 当前边界与已知限制

依据 `docs/todo.md` 与 `README.zh-CN.md` 路线图，以下能力尚未达到生产完备状态：

- SMS 与 WebAuthn 的 MFA 校验器仍属原型：短信由本地生成随机码，WebAuthn 尚无标准注册与断言流程；EMAIL 因子已接入真实投递。
- MaxMind 地理库目前只从本地 `.mmdb` 文件读取，控制台中的注册码尚未落地下载与自动更新。
- SAML 2.0 协议完整性有限：元数据自称 `signing` 但实际未做 XML 签名，缺少证书管理、`AuthnRequest` 解析与 SLO。
- LDAP / Active Directory 已定义身份源类型，连接器适配仍在路线图上，当前以 JSON 导入和钉钉、飞书、企业微信为主。
- 风险规则暂不支持地理速度等更复杂的自适应策略，MFA 动作的丰富度仍在演进。
- 测试覆盖集中在服务层与 Web 层，共 23 个测试文件。

## 8. 相关文档

- `README.zh-CN.md`：端点清单与示例调用。
- `docs/integration-guide.md`：第三方应用接入指南，覆盖接入方式选择、单点登录与通讯录同步流程、管理 API 能力清单与常见问题。
- `docs/todo.md`：待办清单，逐项标注证据位置与验收标准。
- 运行时文档：`/swagger-ui/index.html`、`/v3/api-docs`。
