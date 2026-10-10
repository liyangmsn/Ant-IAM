# 通用身份源同步（SCIM 2.0 推送）· 设计方案

> 状态：已实现（分支 `feat/scim-identity-source`）。日期：2026-10-10。

## 1. 背景与目标

系统已内置钉钉、飞书、企业微信三种身份源连接器，由 IAM 主动拉取。其他业务系统（自建 HR、OA、ERP 等）想把自己的组织架构同步过来时，目前只有两条路：

| 现有途径 | 问题 |
| --- | --- |
| `/scim/v2/**` | 只接受控制台管理员的会话令牌（几小时过期，且权限是全局的）；写入的数据不归属任何身份源，可以改动、删除任意用户和组织，包括本地账号和其他身份源同步的数据；用户不能指定组织；用户组成员和组织父级只能用 IAM 内部 UUID 引用 |
| `/api/v1/synchronizer/event_receive/{code}` | 私有 JSON 格式、HMAC 签名，只能新增和更新，不能删除；没有文档，接入方需要自己理解内部结构 |

**目标**：任何第三方系统都可以作为一个"身份源"，用标准 SCIM 2.0 协议把组织、人员、用户组推送进来，并且：

1. 每个第三方有独立的长期凭据，只能读写**自己推送的数据**，不能碰本地账号和其他身份源的数据。
2. 第三方用自己的 ID（`externalId`）引用组织、上级组织、人员所属组织、组成员，不需要记住 IAM 的 UUID。
3. 上游删除人员时停用账号（可恢复），删除组织、用户组时只在为空时删除。
4. 控制台能看到每个身份源推送了什么、什么时候、成功还是失败。
5. 提供对外对接文档和可运行的示例。

**非目标**：SCIM Bulk 操作、ETag 并发控制、`sort`、IAM 主动拉取第三方（这一期只做第三方推送）。

## 2. 总体方案

新增身份源类型 **`SCIM`（通用 SCIM 推送）**。管理员在控制台创建这种身份源后，生成一个**同步令牌**交给第三方；第三方用它调用**身份源专属的 SCIM 端点**：

```
https://{IAM 地址}/scim/v2/sources/{身份源编码}/Users
                                            /Groups
                                            /Organizations
                                            /ServiceProviderConfig、/ResourceTypes、/Schemas
Authorization: Bearer <同步令牌>
```

现有的 `/scim/v2/**`（管理员令牌、全局视图）保持不变，供 IAM 自己的管理工具和导出使用。

```
 第三方系统（HR / OA / ERP）                         IAM
 ┌───────────────────────┐   SCIM 2.0 over HTTPS   ┌──────────────────────────────────┐
 │ 组织 / 人员 / 用户组   │ ──────────────────────▶ │ /scim/v2/sources/{code}/...       │
 │ 变更时推送，或定时全量 │   Bearer 同步令牌        │  1. 令牌 → 身份源（只能是这一个）  │
 └───────────────────────┘                         │  2. 按 externalId 定位本源的数据   │
                                                   │  3. 写入组织 / 用户 / 用户组        │
                                                   │  4. 审计 + 同步事件统计             │
                                                   └──────────────────────────────────┘
```

## 3. 认证与隔离

### 3.1 同步令牌

- 每个 SCIM 身份源最多一个有效令牌，格式 `isk_<43 位随机串>`，数据库只存 SHA-256 哈希，明文只在生成时返回一次（与客户端密钥的做法一致）。
- 管理员可以随时**重新生成**（旧令牌立即失效）或**吊销**。
- 令牌不过期；身份源停用后令牌随之不可用。
- 存储：`identity_source_connectors.secret_ref` 已是密钥字段，SCIM 身份源复用它保存令牌哈希，不新增表。

### 3.2 数据归属

在组织、用户组上新增 `identity_source_id` 和 `external_id` 两列（用户表已有 `identity_source_id`，新增 `external_id`）。归属规则：

| 资源 | 身份源能看到 / 改到的 | 冲突处理 |
| --- | --- | --- |
| 用户 | `identity_source_id = 本源` | 创建时 `userName` 已被本地账号或其他身份源占用 → `409 uniqueness` |
| 组织 | `identity_source_id = 本源` | 本源内 `externalId` 唯一；组织编码统一生成为 `{源编码}:{externalId}`，不会与其他组织冲突 |
| 用户组 | `identity_source_id = 本源` | 同上，编码为 `{源编码}:{externalId}` |

- 本源的组织可以挂在 IAM 已有组织下面（`parent` 指向一个**非本源**组织时，用该组织的 IAM UUID，见 4.3），方便把第三方的部门树挂到 IAM 的某个节点下；反过来不允许改动非本源组织。
- 列表接口只返回本源数据，`GET /Users/{id}` 查非本源的 ID 返回 404，不泄露其他数据的存在。

## 4. 资源映射

### 4.1 User

| SCIM 属性 | IAM 字段 | 说明 |
| --- | --- | --- |
| `id` | 用户 UUID | 只读 |
| `externalId` | `external_id` | 第三方的人员 ID，本源内唯一；推荐必填 |
| `userName` | 用户名 | 必填，全局唯一，创建后不可改 |
| `displayName` / `name.formatted` | 显示名 | 都为空时用 `userName` |
| `emails[primary]` | 邮箱 | |
| `phoneNumbers[primary]` | 手机号 | 全局唯一 |
| `active` | 状态 | `false` → 停用并回收会话、令牌、直接授权；`true` → 恢复 |
| 扩展 `urn:antiam:params:scim:schemas:extension:2.0:User` 的 `organization` | 所属组织 | `{"value": "<组织 externalId>"}`，或 `{"value": "<组织 UUID>", "type": "id"}` |

### 4.2 Group

| SCIM 属性 | IAM 字段 | 说明 |
| --- | --- | --- |
| `externalId` | `external_id` | 必填，本源内唯一 |
| `displayName` | 名称 | 必填 |
| `members[].value` | 成员 | 用户的 IAM `id`（标准做法）；只能是本源用户 |

### 4.3 Organization（扩展资源）

| SCIM 属性 | IAM 字段 | 说明 |
| --- | --- | --- |
| `externalId` | `external_id` | 必填，本源内唯一 |
| `displayName` | 名称 | 必填 |
| `parent` | 上级组织 | `{"value": "<上级 externalId>"}`；挂到 IAM 已有组织下时用 `{"value": "<UUID>", "type": "id"}`；省略表示根 |

保留旧的 `parentId`（UUID）字段兼容现有调用方。

### 4.4 删除语义

| 操作 | 结果 |
| --- | --- |
| `DELETE /Users/{id}` | **停用**账号（回收会话、令牌、直接授权），保留账号和审计关联，返回 204；之后 `GET` 返回 `active: false`，再次 `PUT active: true` 可恢复 |
| `DELETE /Organizations/{id}` | 存在子组织或成员时 `409`，否则删除 |
| `DELETE /Groups/{id}` | 解除成员关系后删除用户组（组内的角色授予随之解除） |

## 5. 协议细节

- 请求、响应 `Content-Type` 同时接受 `application/scim+json` 与 `application/json`。
- 错误使用 SCIM 错误格式：`{"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"status":"409","scimType":"uniqueness","detail":"..."}`。
- 过滤：`userName`、`externalId`、`displayName`、`active`、`emails.value` 等，`eq/ne/co/sw/ew/pr` + `and/or/not`，与现有实现一致；对接方最常用的是 `externalId eq "..."` 判断是否已存在。
- 分页：`startIndex`（从 1 开始）、`count`，单页上限 500。
- PATCH：User 支持 `active`、`displayName`、`emails`、`phoneNumbers`、`externalId`、组织扩展；Group 支持 `displayName`、`members` 增删替换。

## 6. 运维与可观测

- 每次写操作记审计：`actor = scim:{源编码}`，`targetType = identity_source`，便于在身份源详情的「事件记录」页查看。
- 身份源上记录最近一次推送时间（`last_synced_at`），控制台显示"最近同步"。
- 限流沿用网关配置；请求体上限沿用 nginx 的 25 MB。

## 7. 控制台

- 新增身份源时可选择「通用 SCIM」。
- SCIM 身份源详情的「同步配置」页显示：SCIM 基础地址（可复制）、同步令牌状态、「生成令牌 / 重新生成 / 吊销」，生成后一次性展示明文；并给出对接文档链接。
- 「同步历史」页对 SCIM 源展示推送事件（来自审计）。

## 8. 数据库变更（迁移 043）

```sql
alter table organizations add column identity_source_id uuid references identity_sources(id) on delete set null,
                          add column external_id varchar(256);
alter table user_groups   add column identity_source_id uuid references identity_sources(id) on delete set null,
                          add column external_id varchar(256);
alter table user_accounts add column external_id varchar(256);
alter table identity_sources add column last_synced_at timestamptz;
-- 本源内 externalId 唯一（部分唯一索引，external_id 为空不参与）
```

删除身份源时，数据转为本地数据（`identity_source_id` 置空），与现有的用户处理一致。

## 9. 交付物

1. 后端：迁移 043、令牌管理、身份源专属 SCIM 端点、测试。
2. 控制台：通用 SCIM 身份源的创建与配置页。
3. 文档：公开文档站新增「身份源同步」页（对接说明、字段映射、示例请求、常见错误），内部接入指南同步更新。
4. 示例：本地 `local-samples/scim-push/` 下一个 Node.js 脚本，从 JSON 读取组织架构并推送（全量对比 + 增删改），不入库。
