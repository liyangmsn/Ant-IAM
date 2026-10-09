# 应用权限委派管理 · 设计方案

> 状态：P1–P3 已实现（分支 `feat/app-permission-delegation`），P4、P5 未开始。日期：2026-10-09。
> 前置：应用内权限（权限点 → 应用内角色 → 用户 / 用户组 / 组织）已上线，见 `docs/integration-guide.md` 5.10 节。

## 1. 背景与问题

应用内权限的数据已经放在 IAM 里，但"谁来管理这些权限"还停留在 IAM 管理员层面：

| 现状 | 问题 |
| --- | --- |
| 只有持有 `iam:application:write` 的控制台管理员能维护应用内角色和授予关系 | 这个权限点覆盖**所有应用**，无法只把"订单系统的权限分配"交给订单系统的负责人 |
| 业务应用原本自带"人员权限管理"页面 | 应用接入 IAM 后，这个页面要么废弃（业务方失去自主管理能力），要么继续在应用本地存一份（与 IAM 形成两套数据，鉴权结果不一致） |
| 用户门户只有"我的应用"、访问申请等自助功能 | 业务负责人没有一个不进控制台、又能管理自己应用权限的入口 |

**目标**：IAM 是应用权限的唯一数据源；每个应用的权限管理可以委派给该应用自己的管理员，他们既可以在 IAM 里操作，也可以在业务应用自己的页面里操作（底层调 IAM 接口），而不需要获得 IAM 全局管理权限。

**非目标**：

- 不改变应用内权限的鉴权方式（`/oauth2/permissions/check`、introspect、userinfo 保持不变）。
- 不做跨应用的统一角色模板（每个应用的角色仍然独立）。
- 不替代 IAM 控制台的全局管理能力。

## 2. 核心设计：用"保留权限点"表达委派

### 2.1 两个管理级别

| 级别 | 能做什么 | 典型人选 |
| --- | --- | --- |
| **应用权限负责人** | 维护本应用的权限点与应用内角色；把任意角色授予或撤销；任命或撤销"授权管理员" | 业务系统负责人、产品负责人 |
| **授权管理员** | 只能把**已有的普通角色**授予或撤销给人员，不能改角色内容，不能授予管理类角色 | 部门主管、业务管理员 |

### 2.2 实现方式（推荐方案 B）

**方案 A：新建独立的委派表** `application_delegations(application_id, subject, level)`。
模型直观，但等于在应用内角色之外再造一套授权关系，鉴权、审计、门户展示都要另写一遍。

**方案 B（已采用）：每个应用自动内置两个保留权限点，委派就是普通的角色授予。**

| 保留权限点编码 | 含义 |
| --- | --- |
| `iam:app:permission:manage` | 应用权限负责人 |
| `iam:app:grant:manage` | 授权管理员 |

- 创建应用时自动生成这两个权限点，以及两个内置角色「应用权限负责人」（`iam:app-owner`）「授权管理员」（`iam:app-grant-manager`），各自只包含对应的保留权限点。
- 任命管理员 = 把内置角色授予用户 / 用户组 / 组织，完全复用现有的授予关系、组织继承、审计和权限查询。
- 管理员身份天然受应用访问决策约束：失去应用访问授权的人，委派权限同时失效，离职回收无需额外处理。
- 业务应用通过 userinfo / check 拿到 `iam:app:grant:manage` 后，可以直接决定是否在自己的界面里显示"权限管理"菜单。

方案 B 需要的保护规则：

1. **保留前缀**：`iam:` 前缀的权限点编码只能由系统创建，控制台新增和应用同步（`PUT /oauth2/permissions`）都拒绝使用；同步时跳过保留权限点，不会把它们当作"清单外"删除。
2. **内置角色不可编辑**：两个内置角色的权限组成不可修改、不可删除；普通角色也不能勾选保留权限点。
3. **防提权**：包含保留权限点的角色，只能由应用权限负责人或 IAM 管理员授予；授权管理员不能把这些角色授给任何人，包括自己。
4. **最后一名负责人保护**：委派管理员不能撤销应用权限负责人角色的最后一条授予，避免应用变成只有 IAM 管理员能管；IAM 管理员不受此限制。

数据库变更（迁移 042）：

- `application_permissions` 增加 `reserved boolean not null default false`；`application_permission_roles` 增加 `built_in boolean not null default false`。
- 为存量应用补建保留权限点与内置角色。

### 2.3 权限判定矩阵

| 操作 | IAM 管理员（`iam:application:write` 或超级管理员） | 应用权限负责人 | 授权管理员 | 普通用户 |
| --- | --- | --- | --- | --- |
| 查看本应用权限点、角色、授予关系 | ✔（`iam:application:read` 只读即可） | ✔ | ✔ | ✘ |
| 新增 / 修改 / 删除普通权限点 | ✔ | ✔ | ✘ | ✘ |
| 新增 / 修改 / 删除普通角色 | ✔ | ✔ | ✘ | ✘ |
| 授予 / 撤销普通角色 | ✔ | ✔ | ✔ | ✘ |
| 授予 / 撤销内置管理角色 | ✔ | ✔ | ✘ | ✘ |
| 修改应用基础信息、协议配置、密钥、访问授权 | ✔ | ✘ | ✘ | ✘ |

后端在 `IamAuthorizationService` 增加 `canReadApplicationPermissions(applicationId, auth)`、`canManageApplicationPermissions(...)`、`canGrantApplicationRole(applicationId, roleId, auth)`，判定逻辑为"全局管理员权限 OR 本应用内的保留权限点"。

## 3. 三个管理入口

```
                    ┌──────────────────────────────┐
                    │      IAM：应用权限数据（唯一）     │
                    └──────────────────────────────┘
                      ▲              ▲              ▲
   IAM 控制台（全局管理员）   IAM 门户「我管理的应用」   业务应用自己的权限管理页
   现有接口，不变           委派管理员，会话令牌        委派管理员，客户端凭据 + 用户令牌
```

### 3.1 IAM 控制台（不变）

全局管理员继续在应用详情的【应用权限】页管理所有应用。改动只有：保留权限点和内置角色带「系统」标签、不可编辑。

### 3.2 IAM 门户：「我管理的应用」

委派管理员不需要控制台权限，在用户门户新增一个入口：

- `GET /api/v1/access/me/managed-applications`：返回当前用户持有保留权限点的应用及其管理级别。
- 应用权限管理接口复用控制台的 `/api/v1/access/applications/{id}/permissions`、`/permission-roles`、`/permission-roles/{roleId}/members`，但这些路径要从 URL 级的 `iam:application:*` 规则中拆出来，改为方法级的 `@PreAuthorize` 按上面的矩阵判定。
- 前端复用 `ApplicationPermissionPanel`，按管理级别隐藏不可用的按钮。

### 3.3 业务应用内嵌管理（代表用户调用）

业务应用保留自己的"人员权限管理"页面，但数据读写全部走 IAM：

```http
POST /oauth2/permission-admin/roles/{roleCode}/members
Authorization: Basic base64(client_id:client_secret)
X-Acting-User-Token: <操作人的 access_token>
Content-Type: application/json

{ "subjectType": "USER", "subjectIds": ["<用户 UUID>"] }
```

规则：

1. 客户端凭据确定"哪个应用"，只能操作本应用的数据。
2. `X-Acting-User-Token` 必须是本应用签发、仍然有效的 access_token；IAM 按 2.3 的矩阵判断这个人能不能做这个操作。
3. 不接受只有客户端凭据、没有用户令牌的管理请求，避免应用后台绕过人员直接改权限。
4. 审计记录操作人（用户）和来源客户端（`via client=order-admin-client`）。

接口清单：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/oauth2/permission-admin/me` | 操作人的管理级别，用于决定是否展示权限管理入口 |
| GET | `/oauth2/permission-admin/roles` | 本应用的角色及其权限点 |
| GET | `/oauth2/permission-admin/roles/{roleCode}/members` | 角色的授予对象 |
| POST | `/oauth2/permission-admin/roles/{roleCode}/members` | 授予 |
| DELETE | `/oauth2/permission-admin/roles/{roleCode}/members/{memberId}` | 撤销 |
| GET | `/oauth2/permission-admin/subjects?keyword=&type=` | 搜索可授予的用户 / 用户组 / 组织（只返回 ID、名称、所属组织等最少字段） |
| GET | `/oauth2/permission-admin/users/{userId}/permissions` | 某人在本应用内的有效权限，用于业务页面展示 |

角色用编码而不是 UUID 寻址，业务应用可以把角色编码写进自己的代码和配置。

## 4. 存量数据迁移

业务应用接入前通常已有自己的"人员—角色"数据，提供一次性导入：

- `POST /oauth2/permission-admin/import`（客户端凭据 + 负责人令牌）或控制台上传 CSV。
- 格式：`roleCode, roleName, permissionCodes, subjectType, subjectKey`，其中 `subjectKey` 支持用户名、邮箱、手机号、用户组编码、组织编码，按顺序匹配。
- 先演练（dry-run）返回匹配结果和未匹配清单，确认后再写入；整个导入在一个事务内完成。

## 5. 安全与审计

- **防提权**：见 2.2 的规则 3；授权管理员的操作在服务端逐条校验目标角色是否含保留权限点。
- **令牌绑定**：代表用户调用时，用户令牌必须属于发起请求的客户端，防止 A 应用拿 B 应用的令牌操作。
- **审计**：沿用 `application.permission_role.grant/revoke` 等动作，`detail` 增加 `via=portal|console|client:<clientId>`；控制台审计页可按应用筛选。
- **限流**：`/oauth2/permission-admin/**` 按客户端限流，防止批量误操作。
- **自动失效**：委派依赖应用访问决策，人员停用、离职或失去应用访问授权时，管理能力同步失效。

## 6. 分阶段交付

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| P1 | 迁移 042、保留权限点与内置角色、保护规则、权限判定矩阵、控制台标识 | 3–4 人日 |
| P2 | 门户「我管理的应用」及对应接口鉴权改造 | 3–4 人日 |
| P3 | `/oauth2/permission-admin/**` 代表用户调用接口、示例应用改造 | 3–4 人日 |
| P4 | 存量导入（API + CSV + dry-run） | 2–3 人日 |
| P5（可选） | 授权管理员的组织范围限制（只能授予本部门人员） | 2–3 人日 |

每个阶段都同时交付单元测试、数据库集成测试和接入文档更新。

## 7. 已确认的决策

1. 委派模型采用方案 B（保留权限点 + 内置角色），两级管理（负责人 + 授权管理员）。
2. 本期不做授权管理员的组织范围限制（P5）。
3. 人员搜索只返回在职用户；应用属于某个租户时限定在该租户内；每类最多 20 条，只返回 ID、名称与少量补充信息。
4. 门户与业务应用内嵌两个入口一起交付。
5. 应用的访问授权（谁能进入应用）不委派，仍只归 IAM 管理员。

## 8. 实现说明

- 迁移 `042-application-permission-delegation-schema.sql` 为存量应用补建保留权限点与内置角色。
- 管理级别判定：`ApplicationDelegationService.levelFor`，`iam:application:write` 或超级管理员为 GLOBAL，其次看操作人在应用内的保留权限点，最后 `iam:application:read` 兜底为只读。
- 控制台 / 门户接口 `/api/v1/access/applications/{id}/permissions|permission-roles|permission-decisions|admin-access|grantable-subjects` 从 URL 级模块授权中拆出，改由方法级 `@PreAuthorize`（`canReadApplicationPermissions`、`canManageApplicationPermissions`、`canGrantApplicationRole`）判定；应用的其他配置接口仍按控制台模块授权。
- 门户新增 `GET /api/v1/access/me/managed-applications` 与「我管理的应用」页面；只有当前用户管理至少一个应用时才显示该菜单。
- 业务应用接口 `ClientPermissionAdminController`，操作人令牌解析见 `OAuthService.resolveActingUser`。审计 `detail` 带 `via=client:<clientId>`，`actor` 为操作人用户名。
- 控制台权限面板与门户共用 `ApplicationPermissionPanel`，可授予对象改为服务端搜索（委派管理员没有读取完整目录的权限）。

## 9. 原待确认的问题（已按第 7 节决策）

1. **委派模型**：采用方案 B（保留权限点 + 内置角色），还是方案 A（独立委派表）？
2. **管理级别**：两级（负责人 + 授权管理员）是否够用？是否需要第 2.3 节矩阵以外的权限，例如授权管理员可以新建角色？
3. **授权管理员的范围限制**：是否需要"只能把角色授给本部门 / 本组织的人"（P5）？如果需要，P1 就要在授予关系上预留组织范围字段。
4. **人员搜索的可见范围**：业务应用搜索可授予对象时，返回全部在职用户，还是只返回已有本应用访问授权的用户？
5. **入口优先级**：门户（P2）和业务应用内嵌（P3）先做哪个？
6. **访问授权是否一并委派**：负责人能否管理本应用的"访问授权"（谁能进入应用）？当前矩阵中这一项仍只归 IAM 管理员。
