---
title: 身份源同步
description: 第三方系统通过 SCIM 2.0 把组织架构、人员和用户组同步到平台
---

<p class="eyebrow">DIRECTORY SYNC</p>

# 身份源同步

<p class="lead">面向需要把现有组织架构同步到平台的第三方系统（HR、OA、ERP 等）。第三方系统作为一个“身份源”，按 SCIM 2.0 协议推送组织、人员和用户组，平台据此维护统一身份目录。</p>

## 工作方式 {#overview}

<figure class="figure">
<svg viewBox="0 0 760 230" role="img" aria-label="身份源同步流程：第三方系统用同步令牌调用 SCIM 接口，平台按身份源隔离写入组织、人员和用户组"><defs><marker id="arr-sync" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 z"/></marker></defs><rect class="box" x="20" y="40" width="200" height="150" rx="10"/><text class="t" x="120" y="70">第三方系统</text><text class="s" x="120" y="92">HR / OA / ERP</text><rect class="box" x="45" y="108" width="150" height="26" rx="6"/><text class="s" x="120" y="125">组织架构变更时推送</text><rect class="box" x="45" y="142" width="150" height="26" rx="6"/><text class="s" x="120" y="159">或定时全量对比</text><line class="line" x1="222" y1="115" x2="318" y2="115" marker-end="url(#arr-sync)"/><text class="lbl" x="270" y="100">SCIM 2.0</text><text class="lbl" x="270" y="137">Bearer 同步令牌</text><rect class="accent" x="320" y="40" width="200" height="150" rx="10"/><text class="t" x="420" y="70">平台 SCIM 接口</text><text class="s" x="420" y="92">/scim/v2/sources/{编码}</text><text class="s" x="420" y="122">校验令牌 → 确定身份源</text><text class="s" x="420" y="144">按 externalId 定位数据</text><text class="s" x="420" y="166">只能读写本身份源的数据</text><line class="line" x1="522" y1="115" x2="578" y2="115" marker-end="url(#arr-sync)"/><rect class="ok" x="580" y="30" width="160" height="46" rx="8"/><text class="t" x="660" y="52">组织</text><text class="s" x="660" y="68">部门树</text><rect class="ok" x="580" y="92" width="160" height="46" rx="8"/><text class="t" x="660" y="114">人员</text><text class="s" x="660" y="130">账号、所属组织</text><rect class="ok" x="580" y="154" width="160" height="46" rx="8"/><text class="t" x="660" y="176">用户组</text><text class="s" x="660" y="192">成员关系</text></svg>
<figcaption>图 1　第三方系统用同步令牌调用 SCIM 接口，平台按身份源隔离写入组织、人员和用户组</figcaption>
</figure>

- **数据隔离**：每个身份源只能读写自己推送的数据，看不到也改不了本地账号和其他身份源的数据。
- **用自己的 ID**：组织、上级组织、人员所属组织都用第三方系统自己的 ID（`externalId`）引用，不需要记录平台内部 ID。
- **删除可恢复**：删除人员时平台停用账号（结束会话、撤销令牌、回收授权），账号保留，可再次启用。

## 接入前准备 {#prepare}

请管理员在控制台完成以下操作，并把结果交给第三方系统：

1. 进入 **账户管理 → 身份源管理**，点击「添加身份源」，提供商选择 **通用 SCIM（第三方系统推送）**，填写编码（例如 `hr`）和名称。
2. 打开该身份源的详情，在「同步配置」页点击 **生成令牌**，复制令牌（只显示一次）。
3. 同一页可以复制 **SCIM 基础地址**，形如：

```http
https://iam.example.com/scim/v2/sources/hr
```

| 信息 | 用途 |
| --- | --- |
| SCIM 基础地址 | 所有接口的前缀，下文记为 `{Base}` |
| 同步令牌 | 放在请求头 `Authorization: Bearer <令牌>` 中 |

令牌长期有效，泄露后请在控制台 **重新生成**（旧令牌立即失效）或 **吊销**。身份源停用后，令牌也随之失效。

## 调用约定 {#conventions}

- 请求体使用 JSON，`Content-Type` 可以是 `application/scim+json` 或 `application/json`。
- 资源 `id` 是平台内部 UUID，创建时返回；后续更新、删除都使用它。建议第三方系统保存“自己的 ID ↔ 平台 id”的映射，或用 `externalId` 过滤查询。
- 列表接口支持 `filter`、`startIndex`（从 1 开始）、`count`（最大 500）。最常用的是按 externalId 判断是否已同步：

```http
GET {Base}/Users?filter=externalId eq "E10086"
Authorization: Bearer <令牌>
```

- 过滤运算符支持 `eq`、`ne`、`co`、`sw`、`ew`、`pr`，可用 `and`、`or`、`not (...)` 组合，匹配不区分大小写。

## 同步顺序 {#order}

组织、人员、用户组之间存在引用关系，首次同步请按顺序推送：

1. **组织**：先上级、后下级。
2. **人员**：引用已存在的组织。
3. **用户组**：成员引用已存在的人员。

删除时顺序相反：先调走或停用（`DELETE`）人员，再从下级到上级删除组织。

## 组织 {#organizations}

| 属性 | 必填 | 说明 |
| --- | --- | --- |
| `externalId` | 是 | 第三方系统内的组织 ID，本身份源内唯一，创建后不可修改 |
| `displayName` | 是 | 组织名称 |
| `parent` | 否 | 上级组织。`{"value": "<上级 externalId>"}`；省略表示根组织 |

创建组织：

```http
POST {Base}/Organizations
Authorization: Bearer <令牌>
Content-Type: application/scim+json

{
  "schemas": ["urn:antiam:params:scim:schemas:extension:2.0:Organization"],
  "externalId": "D200",
  "displayName": "华东销售部",
  "parent": { "value": "D100" }
}
```

返回 `201 Created`，响应中的 `id` 即平台组织 ID。

::: tip 挂到平台已有组织下
如果希望把第三方系统的整棵部门树挂到平台某个现有组织节点下，可以让根组织的 `parent` 使用该节点的平台 ID：`{"value": "<平台组织 UUID>", "type": "id"}`。不能挂到其他身份源的组织下。
:::

- 修改：`PUT {Base}/Organizations/{id}`，请求体同创建，可改名称和上级；`externalId` 必须与原值一致。
- 删除：`DELETE {Base}/Organizations/{id}`。组织下还有子组织或在职人员时返回 `409`；只剩已停用（离职）人员时，这些人员的所属组织会被清空，组织随之删除。

## 人员 {#users}

| 属性 | 必填 | 说明 |
| --- | --- | --- |
| `userName` | 是 | 登录用户名，全平台唯一，创建后不可修改 |
| `externalId` | 建议 | 第三方系统内的人员 ID，本身份源内唯一 |
| `displayName` | 否 | 姓名，为空时使用 `userName` |
| `emails` | 否 | 取 `primary` 为 `true` 的一项，没有则取第一项 |
| `phoneNumbers` | 否 | 同上；手机号全平台唯一 |
| `active` | 否 | `false` 停用账号；`PUT` 时省略视为 `true` |
| 扩展属性 `organization` | 否 | 所属组织，`{"value": "<组织 externalId>"}` |

所属组织写在扩展 schema `urn:antiam:params:scim:schemas:extension:2.0:User` 下：

```http
POST {Base}/Users
Authorization: Bearer <令牌>
Content-Type: application/scim+json

{
  "schemas": [
    "urn:ietf:params:scim:schemas:core:2.0:User",
    "urn:antiam:params:scim:schemas:extension:2.0:User"
  ],
  "externalId": "E10086",
  "userName": "zhangsan",
  "displayName": "张三",
  "emails": [{ "value": "zhangsan@example.com", "primary": true }],
  "phoneNumbers": [{ "value": "13800000000" }],
  "active": true,
  "urn:antiam:params:scim:schemas:extension:2.0:User": {
    "organization": { "value": "D200" }
  }
}
```

- 修改：`PUT {Base}/Users/{id}` 整体替换资料、组织和启用状态；或用 `PATCH` 只改部分属性：

```http
PATCH {Base}/Users/{id}
Authorization: Bearer <令牌>
Content-Type: application/scim+json

{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations": [
    { "op": "replace", "path": "displayName", "value": "张三（销售）" },
    { "op": "replace", "path": "urn:antiam:params:scim:schemas:extension:2.0:User:organization", "value": { "value": "D300" } }
  ]
}
```

- 离职或删除：`DELETE {Base}/Users/{id}` 停用账号，返回 `204`。账号保留，之后 `GET` 返回 `"active": false`；再用 `PATCH` 把 `active` 改为 `true` 即可恢复。
- 用户名已被平台其他账号占用时返回 `409 uniqueness`，请与管理员确认是否为同一人。

## 用户组 {#groups}

| 属性 | 必填 | 说明 |
| --- | --- | --- |
| `externalId` | 是 | 第三方系统内的用户组 ID，本身份源内唯一 |
| `displayName` | 是 | 用户组名称 |
| `members` | 否 | 成员，`value` 为本身份源人员的平台 `id` |

```http
POST {Base}/Groups
Authorization: Bearer <令牌>
Content-Type: application/scim+json

{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
  "externalId": "G-SALES",
  "displayName": "销售团队",
  "members": [{ "value": "<张三的平台 id>" }]
}
```

- 增减成员：`PATCH`，`op` 为 `add`、`remove` 或 `replace`，`path` 为 `members`；也可以用 `members[value eq "<id>"]` 移除单个成员。
- 删除：`DELETE {Base}/Groups/{id}`，解除成员关系后删除。

## 推荐的同步策略 {#strategy}

**增量推送**：第三方系统在组织或人员变更时立即调用对应接口，适合有变更事件的系统。

**定时全量对比**：定时（例如每天凌晨）执行一次，保证最终一致：

1. 用 `GET {Base}/Organizations`、`/Users`、`/Groups` 分页读出平台当前数据，按 `externalId` 建索引。
2. 与第三方系统的数据比较：平台缺少的 `POST`，有差异的 `PUT`，第三方已不存在的 `DELETE`。
3. 组织按“先上级后下级”创建、按“先下级后上级”删除。

两种方式可以同时使用。

## 错误处理 {#errors}

错误按 SCIM 标准格式返回：

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "409",
  "scimType": "uniqueness",
  "detail": "userName already exists: zhangsan"
}
```

| 状态码 | scimType | 原因 |
| --- | --- | --- |
| 400 | `invalidValue` | 字段不合法，或引用的组织、成员不存在 |
| 400 | `mutability` | 试图修改 `userName` 或 `externalId` |
| 400 | `invalidFilter` | 过滤表达式不支持 |
| 401 | — | 令牌缺失、错误或已吊销 |
| 403 | — | 身份源已停用 |
| 404 | — | 资源不存在，或不属于本身份源 |
| 409 | `uniqueness` | `userName`、手机号或 `externalId` 已存在 |
| 409 | — | 组织下还有子组织或在职人员，不能删除 |

## 能力发现 {#discovery}

- `GET {Base}/ServiceProviderConfig`：支持的能力（支持 PATCH、过滤；不支持 Bulk、排序、ETag）。
- `GET {Base}/ResourceTypes`、`GET {Base}/Schemas`：资源类型与属性定义。

## 接入自查清单 {#checklist}

- 令牌只保存在第三方系统服务端，没有写进前端代码或日志。
- 首次同步按“组织 → 人员 → 用户组”的顺序推送。
- 保存了第三方 ID 与平台 `id` 的对应关系，或改用 `externalId` 过滤查询。
- 人员离职走 `DELETE`（停用），没有在平台侧另建账号。
- 处理了 `409`（用户名冲突、组织非空）和 `401`（令牌失效）。
- 配置了定时全量对比，用于补偿漏推的变更。
