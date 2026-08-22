# BCWMS 平台第三阶段：租户生命周期设计交接

> 创建日期：2026-08-21
> 适用代码库：`D:\ERP_WMS\BC warehouse\2G`
> 推荐模型：**GPT-5.6 Sol / High**
> 对话定位：**对话 C，只形成详细方案、数据模型和接口设计**
> 强制限制：本轮不得修改公共路由、Java/TypeScript 业务代码、数据库迁移或数据库数据。

## 1. 本对话的任务性质

本任务是第三阶段“租户生命周期”的设计任务，不是代码实施任务。

对话 C 可以：

- 只读检查当前租户、注册开户、订阅、身份、任务、清理和审计实现；
- 核对已有租户状态机、访问拦截和保留期清理逻辑；
- 形成详细的数据模型、状态转换、接口契约、审批策略、事务边界和验收计划；
- 对暂停、恢复、关闭、保留期恢复和最终清理分别做风险分析；
- 新增一份独立的第三阶段详细设计文档。

对话 C 不可以：

- 修改平台或租户前端公共路由、导航、页面、样式或测试；
- 修改任何 Controller、Service、Repository、Entity、SecurityConfig 或配置；
- 新增、修改或执行数据库迁移；
- 修改任何租户状态或数据库数据；
- 暂停、恢复、关闭、清理或创建真实租户；
- 运行现有租户清理服务或调度器；
- 创建或删除演示租户；
- 撤销真实用户会话、停止真实集成或删除导出文件；
- 启动会与对话 A 共用端口、数据库或导出目录的服务；
- 假定对话 A、B 的设计或代码已经完成。

本轮唯一允许的新文件建议为：

```text
docs/PLATFORM_TENANT_LIFECYCLE_PHASE3_DETAILED_DESIGN_2026-08-21.md
```

如果该文件已存在，不覆盖，先报告冲突并使用带时间或后缀的新文件名。

## 2. 并行开发关系

- 对话 A：正在实施第一阶段“租户管理中心”，可能修改租户目录、租户详情、公共平台路由和平台前端 API。
- 对话 B：只设计第二阶段“平台管理员管理”，会形成管理员权限、角色、再次认证和审计方案。
- 对话 C：只设计第三阶段“租户生命周期”，不得修改 A、B 可能涉及的公共文件。
- 第三阶段真正实施必须等待第一阶段租户详情入口稳定，并核对第二阶段最终确定的超级管理员、再次认证和双人治理规则。
- 第四阶段套餐、配额、计费和任务中心会依赖第三阶段状态与生命周期事件；对话 C 应给出事件契约，但不设计完整计费系统。

推荐依赖顺序：

```text
第一阶段：租户目录与详情
        ↓
第二阶段：平台管理员和安全操作规则
        ↓
第三阶段：租户生命周期实施
        ↓
第四阶段：套餐、配额、任务和计费
```

## 3. 第三阶段总体目标

建立安全、可恢复、可审计的租户生命周期控制面，覆盖：

1. 租户开户与初始化状态可见性。
2. 暂停租户服务。
3. 恢复暂停租户。
4. 关闭租户并进入数据保留期。
5. 保留期内恢复关闭租户。
6. 保留期到期后进入待清理状态。
7. 不可逆最终清理及其重试、证据和告警。
8. 状态变化对登录会话、后台任务、Webhook、集成、导出、订阅和租户域名的明确影响。

第三阶段管理的是**租户控制面状态**。它不得给平台管理员新增直接修改或删除任意租户业务记录的通用能力。

## 4. 当前已有生命周期基线

对话 C 必须先从当前代码验证以下事实，不得重复设计为全新系统。

### 4.1 租户状态和字段

`TenantStatus` 当前包含：

```text
PROVISIONING
ACTIVE
SUSPENDED
CLOSED
PURGE_PENDING
PURGED
```

`tenants`/`Tenant` 当前已有：

- `tenantCode`
- `displayName`
- `slug`
- `status`
- `timezone`
- `locale`
- `closedAt`
- `purgeDueAt`
- 乐观锁 `version`
- `createdAt`
- `updatedAt`

### 4.2 状态领域逻辑

`TenantLifecycleService` 已实现：

- `PROVISIONING` 或 `SUSPENDED` → `ACTIVE`
- `ACTIVE` → `SUSPENDED`
- `ACTIVE` 或 `SUSPENDED` → `CLOSED`
- `CLOSED` 且未到 `purgeDueAt` → `ACTIVE`
- `CLOSED` 且保留期已到 → `PURGE_PENDING`

当前关闭保留期常量为 **30 天**。

### 4.3 租户访问拦截

`TenantRequestGate` 当前按状态处理租户端请求：

- `ACTIVE`：允许租户业务请求，拒绝平台和公开注册面混入。
- `SUSPENDED`：只允许登录、健康检查和 `/api/company-access/**`，其他业务请求返回 423。
- `PROVISIONING`：只允许开户状态和健康检查，其他请求返回 423。
- `CLOSED`、`PURGE_PENDING`、`PURGED`：租户端请求返回 410。

平台面与租户面仍然隔离，第三阶段不得破坏该边界。

### 4.4 当前自动开户

`TenantProvisioningService` 已通过公开注册流程完成：

- 创建 `PROVISIONING` 租户；
- 创建主域名；
- 创建用户身份和首位租户管理员；
- 创建订阅；
- 建立租户业务数据；
- 成功后切换为 `ACTIVE`；
- 创建一次性 session handoff。

第三阶段需决定平台是否需要“运营方代开户”，但不得简单复制一条绕过邮箱验证、租户所有者身份和安全激活的低安全路径。

### 4.5 当前清理骨架

已存在：

- `TenantDataPurgeScheduler`
- `TenantDataPurgeService`
- `TenantDataPurgeExecutor`
- `tenant_purge_jobs`
- 不可变 `tenant_purge_audit_logs`
- `bcwms_purge_company_data(companyId)` 数据库函数

当前行为：

- 调度器会查找保留期已到的 `CLOSED` 或已有 `PURGE_PENDING` 租户；
- 清理前使用行锁和租约避免同一任务并发执行；
- 清理租户业务数据、部分控制面数据和导出文件；
- 清理完成后保留租户墓碑，将状态改为 `PURGED`；
- 平台审计证据和专用清理审计原则上永久保留；
- 失败任务记录错误并可重试。

注意：目前没有面向平台管理员的生命周期管理接口。现有状态机和清理服务不等于已经具备安全可用的运营页面。

## 5. 必须首先解决的风险

### 5.1 “关闭”不是普通状态按钮

当前 `close()` 会立即设置：

```text
status = CLOSED
closedAt = 当前时间
purgeDueAt = 当前时间 + 30 天
```

现有调度器在保留期到期后可能自动进入不可逆清理。因此，未来把“关闭”接到平台页面之前，必须先设计：

- 谁可以发起；
- 是否需要目标租户确认；
- 是否需要第二名超级管理员批准；
- 是否有冷静期或计划执行时间；
- 是否已完成数据导出、合同终止和通知；
- 是否存在法律保留或争议冻结；
- 如何撤销尚未执行的关闭请求；
- 保留期到期前如何显著告警；
- 最终清理前是否再次确认以及由谁确认。

对话 C 不得把现有 `TenantLifecycleService.close()` 直接包装成一个按钮作为设计结论。

### 5.2 暂停后旧会话是否会复活

暂停状态会通过请求闸门阻止业务访问，但设计必须核对：

- 暂停前签发的租户 JWT 是否仍保留到期时间；
- 恢复后，暂停前尚未过期的 JWT 是否可能重新可用；
- 是否需要租户级 `security_version`/session epoch；
- 是否需要批量递增租户用户的安全版本；
- 是否只阻断请求，还是同时撤销所有租户会话和一次性 handoff。

推荐目标：暂停、关闭时不得让旧会话在恢复后无意重新生效；具体实现需评估成本并由用户确认。

### 5.3 后台任务和外部集成

状态切换不仅影响页面登录。设计必须覆盖：

- 定时任务是否只遍历 `ACTIVE` 租户；
- 已经开始运行的任务如何停止、完成或取消；
- Shopify/Webhook/ERP/电商渠道入站事件如何响应；
- 邮件、导出、同步、对账、库存计算如何处理；
- 暂停期间是否允许保存外部事件以待恢复后重放；
- 关闭时是否禁用域名、Webhook 路由和集成密钥；
- 清理前是否等待在途任务完成。

### 5.4 最终清理覆盖范围

对话 C 必须列出全部包含 `company_id` 或 `tenant_id` 的表和外部文件，核对当前清理函数是否覆盖：

- 租户业务表；
- 用户身份和成员关系；
- 注册、验证和 session handoff；
- 订阅、套餐关联；
- 租户域名；
- Webhook/集成路由；
- 委派访问授权；
- 平台操作授权；
- 导出任务及 ZIP 文件；
- 对象存储、附件、报表和未来外部资源；
- 审计证据中应保留与应删除/匿名化的字段。

不能只依赖“所有 `company_id` 表”的动态发现，因为部分控制面表使用 `tenant_id`，外部文件也不在数据库内。

## 6. 生命周期状态机设计要求

详细设计必须提供状态转换表，至少包含：

| 当前状态 | 候选动作 | 目标状态 | 可恢复 | 主要影响 |
|---|---|---|---|---|
| `PROVISIONING` | 开户完成 | `ACTIVE` | 是 | 开放租户业务访问 |
| `PROVISIONING` | 开户失败 | 待设计 | 视策略 | 不得留下半成品活跃租户 |
| `ACTIVE` | 暂停 | `SUSPENDED` | 是 | 阻止业务访问、处理会话和任务 |
| `SUSPENDED` | 恢复 | `ACTIVE` | 是 | 重新开放服务，旧会话策略需明确 |
| `ACTIVE/SUSPENDED` | 关闭 | `CLOSED` | 保留期内可恢复 | 开始保留期倒计时 |
| `CLOSED` | 恢复关闭 | `ACTIVE` 或原状态 | 是 | 取消清理并恢复服务 |
| `CLOSED` | 保留期到期 | `PURGE_PENDING` | 原则上停止普通恢复 | 等待最终清理 |
| `PURGE_PENDING` | 执行清理 | `PURGED` | 否 | 删除租户数据，仅留墓碑和必要证据 |
| `PURGED` | 无 | `PURGED` | 否 | 永久终态 |

必须额外回答：

- 关闭后恢复应统一回到 `ACTIVE`，还是恢复到关闭前状态；
- `PURGE_PENDING` 是否允许紧急阻止/法律保留；
- 开户失败是否需要 `PROVISIONING_FAILED` 状态，还是由 provisioning job 表表达；
- 暂停是否区分安全暂停、欠费暂停和人工暂停；
- 是否允许定时暂停或定时关闭；
- 状态变化是否需要 `effectiveAt`；
- 所有转换如何使用乐观锁/悲观锁避免并发覆盖。

## 7. 生命周期动作分级

详细设计需按风险划分，建议：

### 7.1 中风险：暂停与恢复暂停

候选规则：

- 只有超级管理员可执行；
- 操作者当前密码 + TOTP + 必填原因；
- 挑战绑定操作者、目标租户和动作，5 分钟单次有效；
- 暂停立即阻断业务请求；
- 租户所有者收到通知（正式邮件系统可后置，但事件和通知任务需设计）；
- 不删除任何数据；
- 可恢复；
- 记录状态前后、原因和影响摘要。

需要用户确认：安全事件紧急暂停是否可单人立即执行；欠费/合同暂停是否需要预告和第二人复核。

### 7.2 高风险：关闭与保留期内恢复

候选规则：

- 关闭必须是“请求 → 审批 → 计划执行”，不直接执行；
- 操作者当前密码 + TOTP + 原因；
- 原则上需要另一名超级管理员批准；
- 不允许同一人发起并批准；
- 需要目标租户管理员确认、合同/工单证据，或明确记录合法例外；
- 关闭前显示用户、会话、任务、集成、导出和预计清理时间影响；
- 关闭后保留 30 天，页面持续显示倒计时；
- 保留期恢复要求再次认证和原因，并取消所有未执行清理任务；
- 恢复不能复活已清理数据。

### 7.3 极高风险：最终清理

候选规则：

- 不提供“立即删除租户数据”按钮；
- 只能对到达保留期且无有效法律保留的租户执行；
- 由后台任务执行，不在 HTTP 请求事务内删除全部数据；
- 清理前生成只包含安全统计的影响清单，不输出敏感业务数据；
- 建议再次由两名不同超级管理员确认，或由审批通过的策略自动执行；
- 最终执行使用租约、幂等键、重试上限和告警；
- 成功后永不可恢复；
- 保留不可变平台审计、清理审计和匿名化租户墓碑；
- 失败时保持 `PURGE_PENDING`，不把部分清理误报为完成。

用户尚未确认关闭、双人审批和最终清理的具体治理规则。对话 C 只能提出推荐与选项，不能进入实施。

## 8. 数据模型设计要求

### 8.1 当前核心表

至少盘点：

- `tenants`
- `tenant_domains`
- `tenant_subscriptions`
- `tenant_memberships`
- `user_identities`
- `signup_requests`
- `tenant_provisioning_jobs`
- `session_handoff_codes`
- `tenant_purge_jobs`
- `tenant_purge_audit_logs`
- `platform_access_grants`
- `platform_operation_authorizations`
- `platform_export_jobs`
- `platform_audit_logs`

### 8.2 候选新增模型

详细设计必须评估是否需要：

#### `tenant_lifecycle_requests`

用于保存请求和审批流程，候选字段：

```text
id
tenant_id
action
status
requested_by_platform_user_id
reason
current_tenant_version
requested_at
effective_at
expires_at
approved_by_platform_user_id
approved_at
rejected_by_platform_user_id
rejected_at
cancelled_by_platform_user_id
cancelled_at
execution_started_at
execution_completed_at
failure_code
idempotency_key
created_at
updated_at
version
```

设计需判断审批是否放在同表，还是使用独立 `tenant_lifecycle_approvals` 子表。若未来需要双人或多方审批，优先评估独立审批表。

#### `tenant_retention_holds`

用于法律保留、争议、调查或人工停止清理，候选字段：

```text
id
tenant_id
hold_type
reason
created_by_platform_user_id
effective_from
expires_at
released_at
released_by_platform_user_id
created_at
```

设计必须明确谁能创建/解除、是否需要二次审批、到期行为和审计。

#### 租户级会话版本

评估在 `tenants` 增加 `security_version`/`session_epoch`，或通过其他控制表实现租户级会话撤销。必须说明 JWT 校验、缓存、恢复启用和并发更新影响。

#### 生命周期事件/Outbox

评估使用可靠 outbox 发布：

- `TENANT_SUSPENDED`
- `TENANT_RESUMED`
- `TENANT_CLOSED`
- `TENANT_RESTORED`
- `TENANT_PURGE_SCHEDULED`
- `TENANT_PURGED`

事件用于停止任务、禁用集成、发送通知和更新订阅，但本阶段不实现完整任务中心。

### 8.3 迁移限制

- 可以在设计文档写候选表结构和伪 SQL。
- 不得创建实际迁移文件。
- 不得预占 `V4_xx` 版本，统一使用 `NEXT_AVAILABLE`。
- 实施前必须以第一、第二阶段合并后的最新迁移目录重新编号。
- 不得修改当前租户状态、保留期或清理任务。
- 迁移必须兼容已有租户和演示数据，不能把现有 `ACTIVE` 租户重新开户。

## 9. API 设计要求

当前平台内部租户接口仍使用 `/api/platform/companies`。第三阶段设计优先保持兼容，不为术语统一进行破坏性路径改名。页面继续显示“租户”。

候选接口：

```text
GET  /api/platform/companies/{tenantId}/lifecycle
GET  /api/platform/companies/{tenantId}/lifecycle/requests

POST /api/platform/companies/{tenantId}/lifecycle/challenge
POST /api/platform/companies/{tenantId}/lifecycle/requests
POST /api/platform/companies/{tenantId}/lifecycle/requests/{requestId}/approve
POST /api/platform/companies/{tenantId}/lifecycle/requests/{requestId}/reject
POST /api/platform/companies/{tenantId}/lifecycle/requests/{requestId}/cancel

GET  /api/platform/companies/{tenantId}/retention-holds
POST /api/platform/companies/{tenantId}/retention-holds
POST /api/platform/companies/{tenantId}/retention-holds/{holdId}/release

GET  /api/platform/companies/{tenantId}/purge-job
```

详细设计须为每个接口提供：

- 方法和路径；
- 权限角色；
- 请求/响应 DTO；
- 状态和字段校验；
- 是否要求密码 + TOTP；
- 挑战如何绑定操作者、租户、动作和请求；
- 幂等键；
- 乐观锁版本；
- 成功和错误状态码；
- 稳定错误键；
- 会话、任务、集成和数据影响；
- 审计动作；
- 明确不返回的敏感字段。

不要为每个动作随意创建完全不同的安全流程；应评估统一生命周期请求模型，同时保持不同风险级别的审批差异。

## 10. 开户设计要求

第三阶段需设计“平台侧开户”是否存在，至少比较：

### 方案 A：继续以租户自助注册为唯一开户方式

- 平台管理员只能查看 provisioning 状态、重试安全步骤或取消失败申请；
- 租户所有者自行验证邮箱、设置密码并完成激活；
- 平台不接触用户密码。

### 方案 B：平台发起受控开户邀请

- 平台管理员填写租户名称、计划、区域和租户所有者邮箱；
- 系统向所有者发送单次链接；
- 所有者自行验证邮箱、设置密码和激活；
- 后端复用同一 provisioning pipeline；
- 平台管理员不能替所有者设置或查看密码。

推荐优先方案 B 作为未来平台运营开户能力，但必须复用现有安全开户管线，不直接插入 `ACTIVE` 租户和默认密码。是否纳入第三阶段首版需用户确认。

## 11. 会话、权限和访问影响

详细设计必须逐状态说明：

| 状态 | 租户登录 | 已有 JWT | 平台只读概览 | 平台数据读取/导出 | 后台任务/集成 |
|---|---|---|---|---|---|
| `PROVISIONING` | 仅开户流程 | 不应有完整会话 | 可查看安全状态 | 原则上不开放业务数据 | 仅开户任务 |
| `ACTIVE` | 允许 | 正常 | 允许 | 按平台权限 | 正常 |
| `SUSPENDED` | 仅申诉/状态入口 | 业务访问阻断，恢复后的旧会话策略待定 | 允许 | 是否允许支持读取需用户确认 | 暂停或受控 |
| `CLOSED` | 拒绝 | 立即阻断/撤销 | 允许 | 默认禁止或仅受控导出，待确认 | 停止 |
| `PURGE_PENDING` | 拒绝 | 全部无效 | 允许查看清理状态 | 原则上禁止读取/新导出 | 仅清理任务 |
| `PURGED` | 拒绝 | 全部无效 | 仅墓碑和审计 | 禁止 | 无 |

特别需要用户确认：

- 暂停/关闭期间超级管理员是否仍可读取或导出租户数据；
- 客户请求关闭后是否允许在保留期内创建最后一次数据导出；
- 该导出由客户自己、平台支持人员还是自动任务生成；
- 最终清理前是否必须确认不存在有效导出下载窗口。

## 12. 审计与证据设计

详细设计至少评估以下平台审计动作：

- `TENANT_PROVISIONING_REQUESTED`
- `TENANT_PROVISIONING_COMPLETED`
- `TENANT_PROVISIONING_FAILED`
- `TENANT_SUSPEND_REQUESTED`
- `TENANT_SUSPENDED`
- `TENANT_RESUMED`
- `TENANT_CLOSE_REQUESTED`
- `TENANT_CLOSE_APPROVED`
- `TENANT_CLOSE_REJECTED`
- `TENANT_CLOSED`
- `TENANT_RESTORED`
- `TENANT_RETENTION_HOLD_CREATED`
- `TENANT_RETENTION_HOLD_RELEASED`
- `TENANT_PURGE_PENDING`
- `TENANT_PURGE_STARTED`
- `TENANT_PURGE_COMPLETED`
- `TENANT_PURGE_FAILED`

当前 `platform_audit_logs` 和 `tenant_purge_audit_logs` 都有数据库 CHECK/不可变约束。设计需说明：

- 哪些进入通用平台审计；
- 哪些进入专用清理审计；
- 是否允许两个日志记录不同层次的同一操作；
- 如何避免重复或含义冲突；
- 业务操作和审计的事务边界；
- 清理失败时如何保留足够诊断但不泄露业务数据。

不得记录：密码、JWT、TOTP、MFA 密钥、恢复码、挑战令牌、邀请/确认原始令牌、完整业务数据或导出文件内容。

## 13. 事务、并发和幂等设计

详细设计必须说明：

- 同一租户并发暂停和关闭如何裁决；
- 两名管理员同时批准/拒绝同一请求时如何保证单一终态；
- 发起人不能批准自己请求如何由后端和数据库共同保证；
- 乐观锁 `Tenant.version` 与生命周期请求版本如何配合；
- 同一幂等键重复请求如何返回原结果；
- 关闭和恢复与清理调度器竞争时如何防止“刚恢复又被清理”；
- retention hold 与清理任务竞争时如何保证 hold 优先；
- 清理租约过期、进程崩溃和重试如何避免重复外部删除；
- 数据库删除成功但对象存储删除失败如何处理；
- 部分删除时如何保持失败证据并阻止错误标记 `PURGED`；
- 状态变更、会话撤销、outbox 和审计是同一事务还是最终一致；
- 如何避免在 HTTP 请求中执行长时间清理。

## 14. 前端信息架构设计要求

本轮只设计，不修改页面。

建议在第一阶段租户详情中未来增加“生命周期”页签或受控操作区，设计包括：

- 当前状态和状态说明；
- 创建、暂停、关闭和预计清理时间；
- 状态影响摘要；
- 当前进行中的生命周期请求；
- 审批人和执行进度；
- 保留期倒计时；
- retention hold；
- 清理任务状态、尝试次数和安全错误摘要；
- 可执行操作及不可执行原因。

危险操作界面必须：

- 使用明确动词，不写含义模糊的“删除”；
- 分开“暂停服务”“关闭租户”“最终清理”；
- 展示租户名称、编码和当前状态；
- 要求输入原因、当前密码和 TOTP；
- 高风险操作要求再次输入租户编码或专用确认短语；
- 展示会话、用户、任务、集成、数据和恢复期限影响；
- 明确“最终清理不可恢复”；
- 不允许同一人发起并审批自己的关闭请求；
- 不在前端提供绕过保留期的隐藏快捷方式。

## 15. 自动化与任务影响清单

详细设计必须检查并分类当前所有：

- `@Scheduled` 任务；
- 异步任务执行器；
- outbox/事件消费者；
- Webhook 接收器；
- 导入导出工作器；
- 邮件发送；
- 对账、同步和报表任务；
- 对象存储文件；
- 缓存和搜索索引。

每个组件要标记：

```text
ACTIVE 才运行
SUSPENDED 暂停/拒绝/排队
CLOSED 停止
PURGE_PENDING 仅允许清理
PURGED 永不运行
```

对话 C 只生成清单和建议，不修改这些组件。

## 16. 测试与验收设计要求

### 16.1 状态机单元测试

- 所有合法转换成功；
- 所有非法转换失败；
- 关闭时间和 30 天保留期准确；
- 到期前可恢复、到期后普通恢复失败；
- 乐观锁冲突不会静默覆盖。

### 16.2 权限和安全测试

- 非超级管理员不能发起生命周期操作；
- 发起人不能批准自己的高风险请求；
- 挑战绑定目标租户和动作；
- 密码/TOTP/挑战失败不改变状态；
- 审计和日志不泄露敏感字段；
- 平台 JWT 与租户 JWT 仍完全隔离。

### 16.3 会话与访问测试

- `ACTIVE/SUSPENDED/CLOSED/PURGE_PENDING/PURGED` 的请求闸门行为；
- 暂停/关闭后已有 JWT 不能访问；
- 恢复后旧会话是否可用符合最终决定；
- 平台概览和平台数据访问符合状态与授权组合。

### 16.4 清理集成测试

- 只清理目标租户，不影响另一租户；
- 所有 `company_id` 和 `tenant_id` 资源覆盖；
- 外部文件路径安全；
- 失败记录和重试；
- retention hold 阻止清理；
- 并发调度不重复执行；
- 租户墓碑和不可变审计保留；
- 清理完成后无残留会话、域名、集成凭据或可下载导出。

### 16.5 人工验收

真正实施时只能使用专门创建的演示租户，且最终清理测试必须再次取得用户明确授权。不得对默认租户或真实客户数据执行关闭/清理验收。

## 17. 需要用户确认的关键问题

对话 C 完成设计后，必须逐项提出推荐选项：

1. 暂停是否允许单一超级管理员在再次 MFA 后立即执行？推荐：安全紧急暂停允许；计划/欠费暂停需预告和工单证据。
2. 关闭是否要求另一名超级管理员批准？推荐：要求，发起人与批准人不得相同。
3. 关闭是否还需要目标租户管理员确认？推荐：客户主动关闭需要；安全、违法或合同终止使用带证据的例外流程。
4. 保留期是否固定 30 天？推荐：首版保持 30 天，未来按套餐/地区策略扩展。
5. `PURGE_PENDING` 是否允许法律保留阻止清理？推荐：必须允许。
6. 最终清理是否提供“立即执行”按钮？推荐：不提供，只允许到期后的后台受控任务。
7. 最终清理前是否需要第二次双人确认？推荐：首版需要，直到自动化治理成熟。
8. 暂停/关闭时是否撤销租户全部会话？推荐：撤销，防止恢复后旧 JWT 复活。
9. 暂停期间平台支持是否可读取/导出？推荐：仅按已有精确授权，且需新增状态原因审计；关闭后默认禁止新读取。
10. 是否在第三阶段首版加入平台发起开户邀请？推荐：可以设计，实施与生命周期危险操作拆成独立子任务。
11. 关闭后恢复回到 `ACTIVE` 还是关闭前状态？推荐：首版回到 `ACTIVE`，但恢复前检查订阅、集成和安全条件。
12. 保留期内是否允许客户生成最后一次导出？推荐：允许受控、限时、全审计导出，具体执行者需确认。

## 18. 本轮必须交付的详细设计结构

对话 C 最终文档至少包含：

1. 执行摘要；
2. 当前实现盘点与缺口；
3. 生命周期状态机；
4. 动作风险分级与审批矩阵；
5. 平台开户方案比较；
6. 数据模型和 ER 关系；
7. 候选迁移结构（仅设计，`NEXT_AVAILABLE`）；
8. 完整 API 契约；
9. 再次认证和双人审批流程；
10. 暂停、恢复、关闭、保留期恢复和最终清理时序；
11. 租户会话撤销方案；
12. 后台任务、Webhook、集成和导出影响矩阵；
13. retention hold 设计；
14. 清理资源覆盖清单；
15. 审计和日志脱敏；
16. 事务、并发、幂等和失败恢复；
17. 前端信息架构；
18. 自动测试和人工验收计划；
19. 与第一、第二、第四阶段的依赖；
20. 实施拆分、合并顺序、上线门禁和回滚方案；
21. 待用户确认问题及推荐选项。

文档完成后停止，不进入代码实施。

## 19. 明确不在第三阶段设计中的内容

- 修改第一阶段租户目录和数据浏览实现；
- 实施第二阶段管理员目录、普通邀请和账号启停；
- 完整套餐、计费、发票、付款和配额系统；
- 完整任务中心 UI；
- SSO、SCIM、数据区域、专属实例和对外平台 API；
- 普通平台管理员的租户写入或删除能力；
- 租户 ERP 业务数据编辑功能；
- 绕过保留期立即删除租户；
- 删除平台审计或清理审计；
- 真实租户状态修改或数据清理。

## 20. 必读文档和代码

文档按顺序完整阅读：

1. `docs/PLATFORM_TENANT_MANAGEMENT_CENTER_HANDOFF_2026-08-21.md`
2. `docs/PLATFORM_ADMIN_MANAGEMENT_PHASE2_DESIGN_HANDOFF_2026-08-21.md`
3. `docs/PLATFORM_ADMIN_PORTAL_HANDOFF_2026-08-16.md`
4. `docs/PLATFORM_ADMIN_BACKEND_DEVELOPMENT_PLAN_2026-08-16.md`

至少核对：

- `Tenant.java`
- `TenantStatus.java`
- `TenantLifecycleService.java`
- `TenantRequestGate.java`
- `TenantProvisioningService.java`
- `TenantDataPurgeService.java`
- `TenantDataPurgeExecutor.java`
- `TenantDataPurgeScheduler.java`
- `V4_53__Signup_Verification_Provisioning_And_Handoff.sql`
- `V4_55__Tenant_Expired_Data_Purge.sql`

若文档与代码不同，以当前代码和最新迁移为事实，记录差异，不修改代码使其迎合旧文档。

## 21. 新对话 C 可直接复制的启动提示词

```text
请在 D:\ERP_WMS\BC warehouse\2G 中进行 BCWMS 平台第三阶段“租户生命周期”的设计任务。

推荐模型：GPT-5.6 Sol / High。

开始前完整阅读：
1. docs/PLATFORM_TENANT_LIFECYCLE_PHASE3_DESIGN_HANDOFF_2026-08-21.md
2. docs/PLATFORM_TENANT_MANAGEMENT_CENTER_HANDOFF_2026-08-21.md
3. docs/PLATFORM_ADMIN_MANAGEMENT_PHASE2_DESIGN_HANDOFF_2026-08-21.md
4. docs/PLATFORM_ADMIN_PORTAL_HANDOFF_2026-08-16.md
5. docs/PLATFORM_ADMIN_BACKEND_DEVELOPMENT_PLAN_2026-08-16.md

对话 A 正在实施第一阶段租户管理中心；对话 B 只设计第二阶段平台管理员管理。本对话 C 只进行只读代码盘点，并新增一份详细设计文档：
docs/PLATFORM_TENANT_LIFECYCLE_PHASE3_DETAILED_DESIGN_2026-08-21.md

强制限制：
- 不修改公共路由、导航、PlatformApp.tsx、api.ts、页面、样式或测试。
- 不修改任何 Java 业务代码、配置、安全链、调度器或清理逻辑。
- 不新增或修改数据库迁移，不占用迁移版本号。
- 不修改数据库数据，不创建、暂停、恢复、关闭或清理任何租户。
- 不运行清理调度器，不删除导出文件或外部资源。
- 不启动会与对话 A 冲突的服务。
- 除上述详细设计文档外，不创建或修改其他文件。

当前已有基线：租户状态包含 PROVISIONING、ACTIVE、SUSPENDED、CLOSED、PURGE_PENDING、PURGED；已有状态领域逻辑、租户请求闸门、公开注册开户、30 天关闭保留期、清理任务/租约、不可变清理审计和数据库清理函数。当前没有平台生命周期管理接口和安全审批页面。

特别注意：现有 close() 会设置 30 天 purgeDueAt，调度器到期后可能进入不可逆清理。不得把它直接包装成按钮。设计必须覆盖再次 MFA、双人审批、目标租户确认或合法例外、会话撤销、任务/集成停止、retention hold、清理覆盖范围、事务并发、失败恢复和审计。

设计文档必须包含：现状与差距、完整状态机、风险/审批矩阵、开户方案、数据模型、候选迁移结构、API 契约、生命周期时序、租户级会话撤销、任务/集成影响、法律保留、清理资源清单、审计、并发幂等、前端交互、测试计划、阶段依赖、实施拆分和回滚方案。

完成盘点后先用通俗中文告诉我关键风险和主要缺口；然后完成详细设计文档。文档完成即停止，不进入代码实施，并逐项与我确认待定产品细节。
```
