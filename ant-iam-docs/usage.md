---
title: 使用说明
description: 登录、门户应用、账号安全、会话管理和常见问题
---

<p class="eyebrow">USER GUIDE</p>

# 使用说明

<p class="lead">本页帮助普通用户完成登录、访问应用和维护账号安全。管理员分配应用后，用户即可在门户中心使用对应应用。</p>

## 登录系统 {#login}

1. 打开企业提供的系统登录地址。
2. 输入用户名和登录密码，点击“登录”。
3. 也可以切换到“验证码登录”，使用已绑定手机号完成验证。

<figure class="figure">
<svg viewBox="0 0 760 250" role="img" aria-label="登录流程：输入账号、身份校验、风险判定，根据风险等级进入门户、二次认证或拒绝"><defs><marker id="arr-login" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 z"/></marker></defs><rect class="box" x="10" y="95" width="130" height="50" rx="8"/><text class="t" x="75" y="117">输入账号</text><text class="s" x="75" y="133">用户名 / 手机号</text><line class="line" x1="140" y1="120" x2="178" y2="120" marker-end="url(#arr-login)"/><rect class="box" x="180" y="95" width="140" height="50" rx="8"/><text class="t" x="250" y="117">身份校验</text><text class="s" x="250" y="133">密码或短信验证码</text><line class="line" x1="320" y1="120" x2="358" y2="120" marker-end="url(#arr-login)"/><line class="line" x1="250" y1="145" x2="250" y2="188" marker-end="url(#arr-login)"/><rect class="danger" x="180" y="190" width="140" height="46" rx="8"/><text class="t" x="250" y="210">连续失败</text><text class="s" x="250" y="226">临时锁定，到时解锁</text><rect class="accent" x="360" y="95" width="130" height="50" rx="8"/><text class="t" x="425" y="117">风险判定</text><text class="s" x="425" y="133">IP · 设备 · 失败次数</text><rect class="ok" x="580" y="15" width="170" height="50" rx="8"/><text class="t" x="665" y="37">进入门户</text><text class="s" x="665" y="53">直接登录成功</text><path class="line" d="M490 120 C535 120 535 40 578 40" marker-end="url(#arr-login)"/><text class="lbl" x="535" y="32">低风险</text><rect class="warn" x="580" y="95" width="170" height="50" rx="8"/><text class="t" x="665" y="117">二次认证</text><text class="s" x="665" y="133">动态口令 / 短信 / 邮件</text><path class="line" d="M490 120 C535 120 535 120 578 120" marker-end="url(#arr-login)"/><text class="lbl" x="537" y="113">中风险 / MFA</text><rect class="danger" x="580" y="190" width="170" height="50" rx="8"/><text class="t" x="665" y="212">拒绝登录</text><text class="s" x="665" y="228">记录审计日志</text><path class="line" d="M490 120 C535 120 535 215 578 215" marker-end="url(#arr-login)"/><text class="lbl" x="535" y="233">高风险</text></svg>
<figcaption>图 1　登录流程：系统根据登录风险决定直接放行、要求二次认证或拒绝登录</figcaption>
</figure>

::: tip 提示
连续输错密码会被临时锁定，到时自动解锁，也可联系管理员解锁；账号停用或密码过期时请联系管理员处理。临时密码登录后应立即修改密码。开启了二次认证或登录环境存在风险时，登录后还需输入动态口令、短信或邮件验证码。
:::

## 门户中心 {#portal}

登录后进入“我的应用”，这里展示当前账号已经获得授权的应用。

- 使用应用名称或编码搜索目标应用。
- 点击应用卡片打开应用登录地址。
- 没有目标应用时，提交访问申请并等待管理员审批。

## 账号安全 {#account}

在“我的账户”中维护个人信息和登录安全设置。

- 确认姓名、邮箱、手机号等基础信息准确，可上传个人头像。
- 定期修改密码，不要与其他系统共用密码。新密码需满足管理员设置的密码策略，不符合时页面会给出具体原因。
- 建议开启二次认证（动态口令、短信或邮件验证码），密码泄露时也能保护账号。
- 按需绑定企业微信、飞书、钉钉等第三方身份。
- 妥善保存恢复码，恢复码不应发送给他人。

## 会话与日志 {#sessions}

“会话管理”可以查看仍然有效的登录会话；“操作日志”可以查看账号相关的关键操作。

::: warning 注意
发现陌生设备、异常时间或异常地点的会话时，立即结束该会话并联系管理员。
:::

## 常见问题 {#faq}

### 为什么看不到某个应用？

应用可能尚未授权给当前账号，或账号不满足自助申请条件。请提交申请或联系管理员。

### 登录后页面提示会话失效怎么办？

重新登录即可。会话超过有效期、被管理员强制下线，或同一账号在线设备数超过上限（最早的会话会被挤下线）时都会出现该提示。如果频繁发生，请联系管理员检查会话设置和系统时间。

### 需要接入新的业务系统怎么办？

请阅读[对接文档](./integration)，并向管理员申请应用标识、密钥和回调地址登记。
