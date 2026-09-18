# Ant IAM 待办清单

本清单由「前端入口 / 配置项」与「后端实现」逐项对照得出，每条都标注了证据位置。

图例：`[ ]` 未开始 · `[~]` 仅原型或部分实现 · `[x]` 已完成

## P0 有入口无实现（已完成）

- [x] **身份源实时同步回调**
  - 实现：`POST /api/v1/synchronizer/event_receive/{sourceCode}`，使用连接器 `secretRef` 做 HMAC-SHA256 验签后增量写入组织、用户和用户组。
  - 位置：`src/main/java/com/antiam/web/SynchronizerController.java`、`src/main/java/com/antiam/service/identitysource/RealtimeSyncSignature.java`

- [x] **邮件能力（发信 + 模板渲染）**
  - 实现：读取 `message.mail.service` 构建 SMTP 发送器，按 `message.template.*` 渲染 `${变量}`；已接入 `EMAIL` 类型 MFA 挑战投递，挑战响应不再返回明文验证码。
  - 位置：`src/main/java/com/antiam/service/MailDeliveryService.java`

- [x] **对象存储与文件上传**
  - 实现：`POST /api/v1/files` 上传文件到已配置的对象存储，返回对象键与可访问地址；按 provider 路由到各厂商原生适配器。

- [x] **IP 地理库解析**
  - 实现：`geoip.provider=maxmind` 时用 MaxMind 数据库解析国家与城市，`system` 模式保留本机/内网/公网分类；登录位置统计与审计自动受益。
  - 位置：`src/main/java/com/antiam/service/GeoIpService.java`

## P1 原型待生产化

- [~] **MFA 短信 / WebAuthn 校验器**
  - 现状：邮箱因子已走真实投递；短信仍由本地生成随机码后返回响应，WebAuthn 无任何库或浏览器 API 支持。
  - 位置：`src/main/java/com/antiam/service/UserService.java:534`、`src/main/java/com/antiam/service/UserService.java:624`
  - 验收：短信走真实通道下发且不回传明文；WebAuthn 走标准注册与断言流程。

- [ ] **MaxMind 数据库获取与更新**
  - 现状：仅支持从 `ant-iam.geoip.database-path` 指向的本地 `.mmdb` 文件读取；控制台填写的 MaxMind 注册码 `licenseKey` 未被前端保存，也没有下载逻辑。
  - 位置：`ant-iam-frontend/src/apps/console/pages/system/components/SystemPages.tsx`
  - 验收：注册码可保存，并能按 MaxMind 下载接口获取与更新数据库。

- [~] **SAML2 协议完整性**
  - 现状：元数据自称 `signing`，实际无签名。
  - 缺口：无 XML 签名与证书管理、无 `AuthnRequest` 解析、无 SLO，绑定仅一种。
  - 位置：`src/main/java/com/antiam/service/FederationService.java:47`、`src/main/java/com/antiam/service/FederationService.java:51`
  - 验收：`saml2/sso` 可消费 SP 的 `AuthnRequest`，`Response`/`Assertion` 按配置证书签名。

- [ ] **LDAP / AD 原生连接器**
  - 现状：`IdentitySourceType` 已定义 `LDAP`、`ACTIVE_DIRECTORY`。
  - 缺口：仅由 JSON payload 执行器处理，无 LDAP bind/search、无增量同步。
  - 位置：`src/main/java/com/antiam/service/identitysource/JsonIdentitySourceConnectorAdapter.java:21`
  - 验收：可用连接配置直连目录服务拉取组织与用户，支持分页与变更同步。

- [ ] **风险规则：地理速度与自适应动作**
  - 现状：枚举仅 6 种，地理位置只做字符串相等比较。
  - 缺口：无不可能旅行/速度类规则，决策输出未驱动真实 MFA 或拒绝动作。
  - 位置：`src/main/java/com/antiam/domain/RiskRuleType.java`
  - 验收：新增地理速度规则；`STEP_UP_MFA` 与 `DENY_OR_STEP_UP` 在登录链路真实生效。

## P2 待补全协议

- [ ] **表单代填**
  - 现状：`ApplicationProtocol` 有 `FORM_FILL`，可保存 `formLoginTemplate`。
  - 缺口：无代填提交逻辑；`FORM_FILL` 仅作为本地登录会话的 protocol 标签。
  - 位置：`src/main/java/com/antiam/service/AuthenticationService.java:88`、`src/main/java/com/antiam/service/AuthenticationService.java:127`
  - 验收：模板可渲染并完成代填登录跳转。

- [ ] **通用安全设置部分字段未生效**
  - 现状：`security.password.*` 已被 `AuthenticationPolicyService` 消费，`security.general.login_failure_max_attempts` 也已生效；但同页面的 `user_concurrent_sessions`、`session_ttl_seconds`、`remember_me_ttl_seconds`、`captcha_ttl_minutes`、`login_failure_window_minutes`、`auto_unlock_minutes`、`content_security_policy` 落库后没有消费方。
  - 位置：`src/main/java/com/antiam/service/SecuritySettingService.java`、`src/main/java/com/antiam/service/AuthenticationPolicyService.java:144`
  - 验收：二选一——接入认证与响应头链路真实生效，或从前端移除这些字段。

## 已完成

- [x] JWT 单点登录签发与验签
  - 实现：`GET /jwt/sso?audience=` 按应用 SSO 配置的 `jwtAudience`（缺失时回退 `clientId`）签发 RS256 令牌，`aud` 取配置值、有效期取 `accessTokenTtlMinutes`；`POST /jwt/verify` 按头部 `kid` 匹配签名密钥校验签名与 `exp`/`nbf`，失败以 `failureCode` 说明原因，该端点无需登录即可调用。
  - 位置：`src/main/java/com/antiam/service/FederationService.java`、`src/main/java/com/antiam/service/JwtService.java`、`src/main/java/com/antiam/web/FederationController.java`
  - 控制台：SSO 配置新增「令牌 Audience」输入项（仅 JWT 协议显示），并展示签发、校验与 JWKS 端点；保存时不再清空 SAML/CAS/表单代填等协议字段。
- [x] 短信验证码通道：由 sms4j 承载，本地默认 `fixed-code`，生产切换通道只需配置 `sms.blends` 并设置 `ANT_IAM_SMS_BLEND_ID`。控制台的阿里云/腾讯云/七牛字段仍未接入发送链路，作为渠道元数据保留。
- [x] 对象存储云厂商原生 SDK：`aliyun`（阿里云 OSS）、`tencent`（腾讯云 COS，bucket 自动拼 `-{appId}`）、`qiniu`（七牛云 Kodo）各自原生上传；`s3` 与 `minio` 走 S3 兼容适配器
- [x] OAuth2 / OIDC consent UI 页面
- [x] 企业微信后台连接器（直连 `qyapi.weixin.qq.com` 拉取部门与成员）
- [x] OIDC JWKS 与 RS256 ID Token 签名
- [x] 身份源定时同步调度器
