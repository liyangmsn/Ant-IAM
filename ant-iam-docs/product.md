---
title: 产品介绍
description: 平台定位、核心能力、平台特点与典型应用场景
---

<p class="eyebrow">PRODUCT OVERVIEW</p>

# 企业数字身份管控平台

<p class="lead">本页面向企业决策者、IT 负责人与安全合规负责人，介绍平台的定位、核心特点以及能为企业解决的问题。使用方式见 <a href="./usage.html">使用说明</a>，应用接入步骤见 <a href="./integration.html">对接文档</a>。</p>

## 产品定位 {#positioning}

**为企业内部所有应用提供统一的“账号目录 + 登录认证 + 权限授权 + 行为审计”底座，可私有化部署，数据完全掌握在企业自己手中。**

员工只需一个账号、一次登录即可访问所有授权应用；IT 在一个控制台里管住“谁是员工、谁能进哪个系统、谁在什么时候做了什么”。

<figure class="figure">
<svg viewBox="0 0 760 348" role="img" aria-label="平台整体架构：身份来源同步到平台，平台通过标准协议为业务应用提供登录与授权"><defs><marker id="arr-arch" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 z"/></marker></defs><text class="h" x="105" y="62">身份来源</text><text class="h" x="655" y="62">业务应用</text><rect class="box" x="20" y="80" width="170" height="36" rx="8"/><text class="t" x="105" y="95">钉钉</text><text class="s" x="105" y="111">通讯录同步</text><line class="line" x1="190" y1="98" x2="268" y2="98" marker-end="url(#arr-arch)"/><rect class="box" x="20" y="124" width="170" height="36" rx="8"/><text class="t" x="105" y="139">飞书</text><text class="s" x="105" y="155">通讯录同步</text><line class="line" x1="190" y1="142" x2="268" y2="142" marker-end="url(#arr-arch)"/><rect class="box" x="20" y="168" width="170" height="36" rx="8"/><text class="t" x="105" y="183">企业微信</text><text class="s" x="105" y="199">通讯录同步</text><line class="line" x1="190" y1="186" x2="268" y2="186" marker-end="url(#arr-arch)"/><rect class="box" x="20" y="212" width="170" height="36" rx="8"/><text class="t" x="105" y="227">批量导入</text><text class="s" x="105" y="243">JSON 文件</text><line class="line" x1="190" y1="230" x2="268" y2="230" marker-end="url(#arr-arch)"/><rect class="hub" x="270" y="30" width="220" height="240" rx="12"/><text class="t" x="380" y="60">Js IAM 身份管控平台</text><rect class="accent" x="290" y="80" width="180" height="36" rx="8"/><text class="t" x="380" y="102">统一身份目录</text><rect class="accent" x="290" y="124" width="180" height="36" rx="8"/><text class="t" x="380" y="146">登录认证 · MFA</text><rect class="accent" x="290" y="168" width="180" height="36" rx="8"/><text class="t" x="380" y="190">授权 · RBAC</text><rect class="accent" x="290" y="212" width="180" height="36" rx="8"/><text class="t" x="380" y="234">审计 · 日志</text><rect class="box" x="570" y="80" width="170" height="36" rx="8"/><text class="t" x="655" y="95">自研系统</text><text class="s" x="655" y="111">OIDC / OAuth2</text><line class="line" x1="492" y1="98" x2="568" y2="98" marker-end="url(#arr-arch)"/><rect class="box" x="570" y="124" width="170" height="36" rx="8"/><text class="t" x="655" y="139">商业软件</text><text class="s" x="655" y="155">SAML 2.0</text><line class="line" x1="492" y1="142" x2="568" y2="142" marker-end="url(#arr-arch)"/><rect class="box" x="570" y="168" width="170" height="36" rx="8"/><text class="t" x="655" y="183">开源系统</text><text class="s" x="655" y="199">CAS</text><line class="line" x1="492" y1="186" x2="568" y2="186" marker-end="url(#arr-arch)"/><rect class="box" x="570" y="212" width="170" height="36" rx="8"/><text class="t" x="655" y="227">下游系统</text><text class="s" x="655" y="243">SCIM 2.0 同步</text><line class="line" x1="492" y1="230" x2="568" y2="230" marker-end="url(#arr-arch)"/><rect class="ok" x="200" y="302" width="160" height="36" rx="8"/><text class="t" x="280" y="317">员工</text><text class="s" x="280" y="333">用户门户 · 单点登录</text><line class="line" x1="280" y1="300" x2="280" y2="272" marker-end="url(#arr-arch)"/><rect class="warn" x="400" y="302" width="160" height="36" rx="8"/><text class="t" x="480" y="317">管理员</text><text class="s" x="480" y="333">管理控制台</text><line class="line" x1="480" y1="300" x2="480" y2="272" marker-end="url(#arr-arch)"/></svg>
<figcaption>图 1　平台整体架构：人员数据从通讯录同步进来，通过标准协议为各业务系统提供统一登录</figcaption>
</figure>

## 企业常见的身份管理难题 {#challenges}

| 难题 | 带来的后果 |
| --- | --- |
| 每个业务系统各有一套账号密码 | 员工记不住密码、频繁找 IT 重置；弱密码、共用密码普遍存在 |
| 组织架构分散在钉钉、飞书、企业微信和各业务系统 | 人员信息不一致，新系统上线要重复导入人员 |
| 入职开通靠工单、离职回收靠人工逐个系统操作 | 开通慢，离职后账号仍可登录，形成“僵尸账号”与数据泄露风险 |
| 权限是谁给的、为什么能访问，说不清楚 | 权限只增不减，审计时无法举证 |
| 登录与操作日志散落在各系统 | 安全事件难以追溯，等保测评、内审准备成本高 |
| 采购 SaaS 身份服务 | 员工身份数据托管在第三方，行业监管或数据安全要求难以满足 |

## 平台能为企业做什么 {#capabilities}

### 1. 统一身份目录：一份人员数据，全公司共用

- 组织树、用户、用户组集中管理，支持多租户与租户级配置。
- 对接 **钉钉、飞书、企业微信** 通讯录，支持定时同步与实时事件推送（HMAC-SHA256 验签），也支持 JSON 批量导入。
- 对外提供 **SCIM 2.0** 标准接口，下游业务系统可自动获取人员与组织变更，不再各自维护。

::: tip 提示
价值：组织变动一处修改、处处生效；新系统上线不再重复建人。
:::

### 2. 统一登录与单点登录：一个账号，一次登录

- 支持 **OIDC / OAuth2、SAML 2.0、CAS** 等主流单点登录协议，覆盖自研系统、商业软件与开源系统的接入需求；OAuth2 支持 PKCE、刷新令牌轮换、令牌自省与撤销、用户授权同意。
- 登录方式丰富：用户名密码、短信验证码，以及 **微信、QQ、钉钉、飞书、企业微信、GitHub、Gitee、支付宝** 等第三方账号登录，并可接入自定义认证源。
- 用户门户集中展示“我的应用”，员工点击即进，无需再次输入密码。

::: tip 提示
价值：员工登录体验统一，密码重置类工单显著减少；业务系统无需再开发登录模块。
:::

### 3. 多重安全防护：把风险挡在登录之前

- **多因子认证（MFA）**：支持 TOTP 动态口令、邮件验证码、短信验证码与恢复码。
- **密码策略**：长度、复杂度、连续重复字符、禁止包含个人信息、弱密码字典、连续数字或字母、键盘序列、历史密码不可重用、密码定期过期与到期提醒，全部可在页面上配置。
- **登录保护**：连续失败锁定账号并自动解锁，会话有效期与“记住我”时长可调，可限制同一账号并发在线数，超出时自动踢掉最早的会话。
- **风险识别**：按 IP、User-Agent、失败登录次数等规则判定低 / 中 / 高风险，高风险直接拒绝登录，中风险要求二次认证。
- **Web 安全**：内容安全策略（CSP）可在线配置并即时生效。

::: tip 提示
价值：即使密码泄露，攻击者也难以登录；安全策略由管理员在页面上调整，无需改代码、不必停机。
:::

### 4. 精细化授权：谁能访问什么，一目了然

- **RBAC 权限模型**：权限 → 角色 → 用户组 / 用户，应用授权可设置有效期，到期自动失效。
- **访问决策解释**：可直接回答“这个人为什么能访问这个应用”，列出来自直接授权、用户组还是应用角色。
- **影响面分析**：修改某个角色或权限前，先看清会影响哪些人。
- **访问复核**：导出应用授权清单，支持定期复核、清理多余权限。
- **自助申请与审批**：员工在门户申请应用权限，管理员审批后自动开通。
- **应用内权限下发**：业务应用把自身的操作（如“审批订单”“导出报表”）注册为权限点，平台按用户、用户组或组织授权；应用执行敏感操作前向平台实时校验，权限变更即时生效，业务系统无需自建权限模块。
- **控制台分级管理**：按模块细分“只读 / 可写”管理权限，可把日常运维分给不同管理员，并防止越权提权。

::: tip 提示
价值：权限最小化落到实处；审计时拿得出“谁、因何、何时获得权限”的证据。
:::

### 5. 账号全生命周期自动化：离职即回收

- 标准的激活、停用、锁定、离职四个动作。
- 执行停用、锁定或离职时，系统 **自动停用直接应用授权、结束所有在线会话、撤销已签发的访问令牌**，全过程写入审计记录。

<figure class="figure">
<svg viewBox="0 0 760 96" role="img" aria-label="账号生命周期：入职、授权、使用、复核、离职"><defs><marker id="arr-life" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 z"/></marker></defs><rect class="box" x="10" y="20" width="128" height="56" rx="8"/><text class="t" x="74" y="45">入职</text><text class="s" x="74" y="61">通讯录自动同步</text><line class="line" x1="140" y1="48" x2="161" y2="48" marker-end="url(#arr-life)"/><rect class="box" x="163" y="20" width="128" height="56" rx="8"/><text class="t" x="227" y="45">授权</text><text class="s" x="227" y="61">加入用户组获得应用</text><line class="line" x1="293" y1="48" x2="314" y2="48" marker-end="url(#arr-life)"/><rect class="box" x="316" y="20" width="128" height="56" rx="8"/><text class="t" x="380" y="45">使用</text><text class="s" x="380" y="61">门户单点登录</text><line class="line" x1="446" y1="48" x2="467" y2="48" marker-end="url(#arr-life)"/><rect class="box" x="469" y="20" width="128" height="56" rx="8"/><text class="t" x="533" y="45">复核</text><text class="s" x="533" y="61">定期访问复核</text><line class="line" x1="599" y1="48" x2="620" y2="48" marker-end="url(#arr-life)"/><rect class="danger" x="622" y="20" width="128" height="56" rx="8"/><text class="t" x="686" y="45">离职</text><text class="s" x="686" y="61">一键回收会话与令牌</text></svg>
<figcaption>图 2　账号全生命周期：离职时自动停用授权、结束会话并撤销令牌</figcaption>
</figure>

::: tip 提示
价值：杜绝“人走了账号还能用”，离职回收从逐个系统手工处理变为一键完成。
:::

### 6. 全程审计与可视化运营

- 登录、登出、失败、锁定、风险判定、管理操作全部留痕，记录客户端 IP、地理位置与设备信息。
- 支持关键字检索与 CSV 导出，方便应对等保测评、内部审计与安全事件调查。
- 仪表盘展示认证量趋势、应用访问排名、认证方式分布与登录地点分布。
- 在线会话一览，可一键强制下线可疑会话。
- 员工也能在门户查看自己的登录记录与在线会话，发现异常可自行下线。

::: tip 提示
价值：安全事件可追溯，合规材料可随时导出。
:::

## 平台特点 {#features}

1. **私有化部署，数据自主可控**：部署在企业自己的服务器或 Kubernetes 集群中，身份数据不出企业网络。
2. **贴合国内办公生态**：原生对接钉钉、飞书、企业微信的通讯录同步与扫码登录，以及微信、QQ、支付宝等常用第三方账号。
3. **标准协议开放集成**：OIDC / OAuth2、SAML 2.0、CAS、SCIM 2.0 全覆盖，并提供 OpenAPI 文档与在线调试界面，第三方系统接入成本低。
4. **配置即生效**：密码策略、会话、锁定、风险规则、安全响应头、消息与存储通道均可在控制台在线调整，无需重启。
5. **助力等保合规**：在身份鉴别（唯一标识、口令复杂度与定期更换、登录失败处理、会话超时、多因子认证）和安全审计（日志留存、检索、导出）方面提供开箱即用的能力，帮助企业满足等级保护 2.0 的相关要求。
6. **现代技术栈，易于运维**：后端 Java 25 与 Spring Boot 4，前端 React 与 Ant Design；PostgreSQL 数据库，数据库结构随版本自动迁移；容器化交付，镜像以非 root 用户运行。
7. **多云存储适配**：头像与文件可存放在阿里云 OSS、腾讯云 COS、七牛云 Kodo 或任意 S3 兼容存储（如 MinIO、RustFS）。

## 典型应用场景 {#scenarios}

| 场景 | 平台如何支撑 |
| --- | --- |
| 新员工入职 | 钉钉 / 飞书 / 企业微信中新增人员后自动同步到平台，加入已授权的用户组后即可获得相应应用权限，入职当天即可登录 |
| 员工离职 | 一键离职，所有应用会话与令牌即时失效，操作留痕 |
| 新业务系统上线 | 在控制台创建应用并选择协议，业务系统按 OIDC / SAML / CAS 对接，无需开发登录与账号模块 |
| 员工申请系统权限 | 门户自助申请 → 管理员审批 → 自动开通，可设置到期时间 |
| 等保测评 / 内部审计 | 导出密码策略配置、登录日志与权限清单作为证据 |
| 异常登录防护 | 陌生环境或暴力破解触发风险规则，自动拒绝或要求二次认证，管理员可强制下线 |

## 交付形态 {#delivery}

- **管理控制台**：面向 IT 与安全管理员，覆盖账户、认证、应用、审计、安全设置和系统设置。
- **用户门户**：面向全体员工，提供应用入口、个人资料、账号安全、第三方账号绑定、个人登录记录与会话管理。
- **开放接口**：管理 API、标准协议端点与 SCIM 2.0，配套 OpenAPI 文档。

## 后续规划 {#roadmap}

以下能力已在规划中，将随版本逐步交付：

- 对接 LDAP / Active Directory 目录同步。
- WebAuthn（指纹、人脸、安全密钥）无密码认证。
- 登录风险引擎接入设备指纹与地理位置规则。
- 控制台按租户隔离数据，操作按钮按权限点精细控制。
