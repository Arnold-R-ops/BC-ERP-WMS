# BCWMS 平台管理端后端开发方案

> 文档状态：开发基线与实施方案
> 更新日期：2026-08-16
> 适用范围：`platform.bcwms.com` 平台管理端后端，不包含公司业务端 WMS 页面
> 当前基线：Java 17、Spring Boot 3.2.11、PostgreSQL、Flyway、Spring Security、JWT、PostgreSQL RLS

## 1. 目标

平台管理端应成为 BCWMS 的运营与治理控制面，负责：

- 平台管理员身份与权限；
- 公司（租户）目录、状态和生命周期；
- 套餐、订阅、功能资格与资源配额；
- 经授权的跨公司读取、导出、修改和删除；
- 平台审计、安全事件、后台任务和运行状态；
- 后续客服、计费、公告和开放平台能力。

平台管理端不应成为公司 WMS 的“万能后台”。平台账号不得模拟公司用户登录，不得绕过公司许可直接修改或删除公司业务数据。

## 2. 已有后端能力盘点

### 2.1 已实现

| 能力 | 当前实现 | 主要位置 |
| --- | --- | --- |
| 平台独立身份 | `platform_users`、`platform_roles`、平台 JWT，与公司身份隔离 | `platform/model`、`security/Platform*` |
| 平台首次开户 | 仅空表时通过一次性环境变量创建首个超级管理员 | `PlatformDeveloperBootstrap` |
| TOTP MFA | 首次登录强制登记、验证码校验、10 个恢复码、失败锁定 | `PlatformMfaService`、`V4_60` |
| 公司目录 | 公司列表和单公司基础信息查询 | `PlatformCompanyDataController` |
| 跨公司只读 | 固定数据集、分页读取、RLS 公司上下文、访问审计 | `PlatformCompanyDataService` |
| 数据导出 | 异步 ZIP/CSV、清单、摘要、过期清理、下载审计 | `PlatformExportService`、`PlatformExportWorker` |
| 平台审计查询 | 时间、公司、操作类型和分页查询；查询行为本身留痕 | `PlatformAuditController` |
| 公司授权操作 | 平台申请、公司管理员批准/拒绝、限时授权、幂等执行 | `PlatformOperationAuthorizationService` |
| 有限写删目录 | `product`、`customer` 的固定字段写入和固定资源删除 | `PlatformOperationCatalog` |
| 公司生命周期领域逻辑 | 激活、暂停、关闭、保留期内恢复、进入待清理 | `TenantLifecycleService` |
| 到期数据清理 | 定时扫描、租约、防重试冲突、RLS 上下文、追加式审计 | `TenantDataPurgeService`、`TenantDataPurgeScheduler` |
| 多租户隔离 | Host 解析、公司 JWT 校验、Hibernate TenantId、PostgreSQL RLS | `tenant` 包、`V4_52` |
| 注册与开户 | FREE/TRIAL、邮箱验证、幂等开户、一次性交接码 | `signup`、`subscription` 包 |

### 2.2 已有但不完整

| 能力 | 当前缺口 |
| --- | --- |
| MFA | 恢复码重新生成、挑战记录清理、独立平台令牌时长和管理员受控 MFA 重置已完成；仍缺生产密钥、轮换及离线 break-glass 验收 |
| 公司目录 | 仅基础字段；缺少套餐、管理员、域名、用量、最近活跃、异常状态和任务摘要 |
| 公司生命周期 | 有领域服务和清理任务，但没有完整的平台生命周期 Controller、幂等命令、二次确认和统一审计 API |
| 套餐订阅 | 已有模型和 Repository，缺少平台管理服务、API、资格判定、配额计量及业务入口强制执行 |
| 平台管理员 | 已有角色表和首个账号引导；缺少管理员目录、创建、停用、解锁、撤销会话和委派授权 |
| 授权操作 | 后端骨架已存在，但操作目录只覆盖产品和客户；缺少申请列表、撤销、过期处理、风险级别和双人复核 |
| 运行监控 | 业务健康检查存在，缺少平台汇总、任务运行记录、告警事件和租户影响范围 |
| 审计 | 已支持只读查询，但筛选维度、关联 ID、保留策略、完整性校验和安全告警仍需补充 |
| 导出 | 后端目录支持更多数据集，当前平台前端只开放六类；缺少平台任务列表、取消、限流和并发配额 |

### 2.3 尚未建设

- 平台首页汇总 API；
- 完整的套餐、资格和配额管理；
- 平台管理员委派授权；
- 平台安全事件与告警中心；
- 统一后台任务中心；
- 客服工单和平台公告；
- 订阅计费、支付、发票和退款；
- API Key、OAuth2 和开放平台。

## 3. 必须保持的安全边界

1. 平台 JWT 只能访问 `/api/platform/**`，公司 JWT 不能访问平台 API。
2. 平台账号不得出现在公司成员、角色、席位或公司审计中。
3. `PLATFORM_SUPER_ADMIN` 永久拥有读取和导出能力，但不自动拥有公司数据写入、删除权限。
4. 公司数据修改和删除必须同时满足：
   - 平台角色允许该操作；
   - 目标公司管理员已批准；
   - 授权未撤销、未过期、未消费；
   - 资源类型、资源 ID、字段和请求指纹完全匹配；
   - 执行请求携带 `Idempotency-Key`。
5. 跨公司读取必须进入目标公司的数据库事务和 RLS 上下文，不允许使用超级数据库账号旁路。
6. 密码、JWT、MFA 密钥、恢复码、验证码、API 密钥、Access Token 和数据库凭证不得进入平台查询、导出或日志。
7. 审计日志和清理审计必须追加写入，禁止业务 API 修改或删除。
8. 生命周期、套餐、权限、导出和任务重试等控制面写操作必须记录操作者、原因、请求 ID、结果和时间。

## 4. 目标模块结构

建议继续保留模块化单体，不在当前阶段拆微服务。

```text
com.wms.system.platform
├─ auth                 平台登录、MFA、恢复、会话撤销
├─ administrator        平台管理员、角色、委派授权
├─ dashboard            平台首页聚合
├─ company              公司目录、详情、生命周期命令
├─ subscription         套餐、订阅、资格、配额、用量
├─ dataaccess           固定数据集读取、脱敏、导出
├─ operation            公司许可的写入/删除申请与执行
├─ audit                平台审计、安全事件、完整性检查
├─ operations           健康状态、后台任务、失败重试、告警
├─ support              工单、公告（P1）
└─ billing              账单、支付、发票（P2）
```

控制面实体不得继承租户业务 `BaseEntity`，不得添加 `@TenantId`。跨公司业务读取必须由服务显式建立目标公司上下文。

## 5. P0 后端开发范围

### 5.1 平台身份安全收尾

#### API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/platform/auth/login` | 已有；密码通过后只返回 MFA 挑战 |
| `POST` | `/api/platform/auth/mfa/enroll/confirm` | 已有；首次登记并一次性返回恢复码 |
| `POST` | `/api/platform/auth/mfa/verify` | 已有；TOTP 或单次恢复码验证 |
| `POST` | `/api/platform/auth/logout-all` | 新增；递增 `security_version`，撤销已有 JWT |
| `GET` | `/api/platform/auth/me` | 2026-08-18 已完成；只返回邮箱、角色、MFA 状态和 JWT 到期时间 |
| `POST` | `/api/platform/auth/logout-all/challenge` | 2026-08-18 已完成；创建当前账号专用的 5 分钟再次 MFA 挑战 |
| `POST` | `/api/platform/auth/logout-all` | 2026-08-18 已完成；只接受认证器 6 位 TOTP，成功后撤销该账号全部平台 JWT |
| `GET` | `/api/platform/auth/recovery-codes/status` | 2026-08-18 已完成；只返回剩余未使用数量，不返回恢复码 |
| `POST` | `/api/platform/auth/recovery-codes/challenge` | 2026-08-18 已完成；创建当前账号专用的 5 分钟重新生成挑战 |
| `POST` | `/api/platform/auth/recovery-codes/regenerate` | 2026-08-18 已完成；必须同时验证当前密码与 6 位 TOTP，新码只返回一次 |
| `POST` | `/api/platform/admins/{targetUserId}/mfa-reset/challenge` | 2026-08-19 已完成；仅超级管理员，挑战绑定执行者与唯一目标，禁止自重置 |
| `POST` | `/api/platform/admins/{targetUserId}/mfa-reset` | 2026-08-19 已完成；验证执行者密码、TOTP 和原因，撤销目标 MFA 与全部平台会话 |

#### 实施要求

- 平台 JWT 使用独立配置，不得继续直接复用租户端 24 小时策略。**2026-08-18 已实施最长 12 小时，并保留平台前端连续 30 分钟无操作退出。**
- MFA 挑战默认 5 分钟、最多 5 次，消费后不可复用。
- 2026-08-18 已增加定时任务清理已过期或已消费的 MFA 挑战。
- MFA 加密密钥只从生产秘密管理来源读取；缺失或长度不合规时生产启动失败。
- 恢复码只在生成当次返回明文，数据库继续保存慢哈希。
- 受控 MFA 重置允许超级管理员操作其他超级管理员，但操作者不得是目标本人；目标的角色和委派授权保持不变。
- MFA 重置不得删除业务数据或已持久化后台任务；任务结果后续查看／下载仍需目标重新登录并通过当时权限核验。
- 暂不自动创建第二个平台账号或 break-glass 账号；需产品和保管流程单独确认。

### 5.2 平台首页汇总

#### API

`GET /api/platform/dashboard/summary`

建议响应：

```json
{
  "generatedAt": "2026-08-16T10:00:00Z",
  "companies": {
    "total": 120,
    "active": 96,
    "trialing": 18,
    "suspended": 3,
    "closed": 3,
    "purgePending": 0
  },
  "attention": {
    "provisioningFailed": 1,
    "exportsFailed": 2,
    "purgesFailed": 0,
    "operationRequestsPending": 4,
    "trialsExpiringIn7Days": 6
  }
}
```

约束：

- 首页只查询控制面汇总，不自动读取任何公司的业务数据。
- 汇总结果使用短时缓存；缓存键属于平台命名空间。
- 每个指标必须标注统计口径，并有 Repository 查询测试。

### 5.3 公司目录和详情增强

#### API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/platform/companies` | 增加 `keyword`、`status`、`plan`、`createdFrom`、`createdTo` 和排序白名单 |
| `GET` | `/api/platform/companies/{companyId}` | 返回公司基础资料和生命周期信息 |
| `GET` | `/api/platform/companies/{companyId}/overview` | 返回订阅、域名、管理员摘要、配额用量和任务摘要 |
| `GET` | `/api/platform/companies/{companyId}/domains` | 查询登记域名和验证状态 |
| `GET` | `/api/platform/companies/{companyId}/usage` | 查询用户、仓库、SKU、集成和存储用量 |

`overview` 不应包含完整用户、订单或库存数据；详细数据继续通过主动选择的数据集接口读取并审计。

### 5.4 公司生命周期管理

#### 新增角色

- `PLATFORM_TENANT_LIFECYCLE`：暂停和恢复；
- `PLATFORM_TENANT_CLOSE`：关闭公司；
- `PLATFORM_PURGE_OPERATOR`：只允许查看和重试已到期清理任务，不允许提前清理。

超级管理员是否包含这些角色应通过明确角色分配决定，不应在代码中隐式继承。

#### API

| 方法 | 路径 | 状态变化 |
| --- | --- | --- |
| `POST` | `/api/platform/companies/{id}/lifecycle/activate` | `PROVISIONING/SUSPENDED -> ACTIVE` |
| `POST` | `/api/platform/companies/{id}/lifecycle/suspend` | `ACTIVE -> SUSPENDED` |
| `POST` | `/api/platform/companies/{id}/lifecycle/close` | `ACTIVE/SUSPENDED -> CLOSED` |
| `POST` | `/api/platform/companies/{id}/lifecycle/restore` | 保留期内 `CLOSED -> ACTIVE` |
| `GET` | `/api/platform/companies/{id}/purge-job` | 查询清理任务和追加式审计摘要 |
| `POST` | `/api/platform/companies/{id}/purge-job/retry` | 仅重试已经失败且已到期的清理任务 |

所有命令请求统一包含：

```json
{
  "reason": "客户申请暂停服务",
  "expectedVersion": 7,
  "confirmation": "company-slug"
}
```

要求：

- 必须使用 `Idempotency-Key`；
- 使用租户记录 `version` 做乐观并发控制；
- 关闭操作要求输入公司 slug 二次确认；
- 不提供“立即永久清理”普通 API；
- 清理失败只能安全重试，不能跳过保留期；
- 每次状态变化写平台审计，记录前后状态、原因和请求 ID。

### 5.5 套餐、订阅、资格和配额

当前已有 `SubscriptionPlan`、`PlanLimit`、`TenantSubscription` 等模型，应在此基础上补服务层，不重复建模。

#### API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/platform/plans` | 套餐目录及启用状态 |
| `GET` | `/api/platform/plans/{planCode}` | 功能资格和配额详情 |
| `PUT` | `/api/platform/plans/{planCode}` | 修改未来适用的套餐配置，要求版本号 |
| `GET` | `/api/platform/companies/{id}/subscription` | 当前订阅、试用期限和到期策略 |
| `POST` | `/api/platform/companies/{id}/subscription/change-plan` | 人工变更套餐，要求原因和幂等键 |
| `GET` | `/api/platform/companies/{id}/entitlements` | 生效后的功能资格 |
| `GET` | `/api/platform/companies/{id}/quotas` | 配额、当前用量和剩余额度 |

#### 服务接口

```java
public interface EntitlementService {
    boolean isEnabled(long companyId, String featureCode);
    void requireEnabled(long companyId, String featureCode);
}

public interface QuotaService {
    QuotaSnapshot snapshot(long companyId, String quotaCode);
    void requireCapacity(long companyId, String quotaCode, long increment);
}
```

要求：

- 功能资格和资源配额分开；
- 用量从权威数据计算，必要时使用可重建汇总表；
- 配额检查与资源创建必须处于同一业务事务，防止并发超额；
- 首期至少覆盖 `USERS`、`WAREHOUSES`、`SKUS`、`INTEGRATIONS`；
- TRIAL 到期策略在产品确认前不得自动删除数据；
- 套餐和人工额度调整全部写平台审计。

### 5.6 平台后台任务中心

首期纳入以下任务：

- 公司开户任务；
- 平台导出任务；
- 公司数据清理任务；
- 邮件发送任务；
- Shopify 同步与对账任务摘要；
- Outbox 积压摘要。

#### API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/platform/jobs` | 按任务类型、状态、公司和时间筛选 |
| `GET` | `/api/platform/jobs/{type}/{publicId}` | 安全摘要，不直接返回原始敏感报文 |
| `POST` | `/api/platform/jobs/{type}/{publicId}/retry` | 仅允许目录声明为可幂等重试的失败任务 |

禁止提供任意类名、方法名、SQL、Shell 或脚本执行入口。任务重试必须通过服务端固定目录分发。

### 5.7 公司授权操作完善

保留现有平台申请、公司审批和平台执行流程，并补充：

- 平台申请分页列表；
- 公司申请分页列表；
- 平台主动撤销；
- 定时将过期申请标记为 `EXPIRED`；
- 风险等级 `LOW/MEDIUM/HIGH`；
- 高风险删除要求第二位公司审批人或另行确认；
- 申请和执行的关联请求 ID；
- 执行前后快照摘要；
- 明确的失败分类和可否重试标志。

建议新增 API：

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/platform/companies/{id}/operations` |
| `POST` | `/api/platform/companies/{id}/operations/{authorizationId}/revoke` |
| `GET` | `/api/company-access/operations/{authorizationId}` |

操作目录仍必须由服务端固定定义；不得接受任意表名、字段名或表达式。

### 5.8 审计与安全事件

扩展审计查询条件：

- `actorPlatformUserId`；
- `requestId`；
- `resourceType`；
- `result`；
- `from/to`，继续限制最大查询跨度；
- 固定排序白名单。

新增安全事件模型 `platform_security_events`，用于记录：

- 连续登录或 MFA 失败；
- 平台账号锁定；
- 大批量跨公司读取；
- 短时间大量导出；
- 非常用 IP 或设备变化；
- 生命周期高风险操作；
- 审计写入失败。

安全事件与审计日志分开：审计记录“发生了什么”，安全事件记录“需要处理什么”。

## 6. 平台管理员与委派授权（P1）

当前唯一超级管理员继续保留。开发本模块不等于自动创建第二个平台账号。

### 6.1 管理员 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/platform/administrators` | 平台管理员目录 |
| `POST` | `/api/platform/administrators` | 创建邀请或待激活账号 |
| `PATCH` | `/api/platform/administrators/{id}/status` | 启用、停用或解锁 |
| `POST` | `/api/platform/administrators/{id}/roles` | 分配平台角色 |
| `DELETE` | `/api/platform/administrators/{id}/roles/{roleCode}` | 撤销角色 |
| `POST` | `/api/platform/administrators/{id}/mfa/reset` | 双人确认后重置 MFA |
| `POST` | `/api/platform/administrators/{id}/sessions/revoke` | 撤销全部会话 |

### 6.2 委派授权模型

建议新增 `platform_access_grants`：

| 字段 | 说明 |
| --- | --- |
| `public_id` | 对外不可猜测 ID |
| `grantee_platform_user_id` | 被授权管理员 |
| `capability` | `READ` 或 `EXPORT`；写删仍走公司许可 |
| `tenant_scope` | 单公司、公司集合或明确的全公司范围 |
| `dataset_scope` | 固定数据集代码集合 |
| `valid_from/valid_until` | 授权有效期 |
| `granted_by/revoked_by` | 授权和撤销人 |
| `reason` | 必填原因 |
| `version` | 并发控制 |

约束：

- 管理员不能自我授权；
- 委派授权不能转授权；
- 授权、撤销、实际读取、导出和下载全部审计；
- 后端授权是最终边界，前端隐藏菜单不能代替后端校验；
- 未配置授权时，普通平台管理员默认不能查看租户目录或导出。

## 7. 建议的数据迁移

当前最新迁移为 `V4_60__Platform_MFA.sql`。实际开发前先同步分支并确认没有其他人占用版本号，再创建后续迁移。建议按能力拆分，不把全部平台功能放入单个迁移：

1. 平台安全事件、平台会话或令牌撤销相关字段；
2. 生命周期命令幂等记录与审计动作扩展；
3. 套餐资格、配额和用量快照补充；
4. 平台后台任务统一索引或投影；
5. 平台委派授权；
6. 客服、公告和计费模块后续独立迁移。

迁移要求：

- 可重复在生产结构副本完成全链演练；
- 新增约束前先检查历史数据；
- 控制面外键不得错误指向公司业务复合主键；
- 追加式审计表必须有数据库级 UPDATE/DELETE 阻止机制；
- 迁移脚本不得写入真实邮箱、密码、密钥或令牌。

## 8. API 通用规范

### 8.1 错误响应

统一返回稳定错误码，不向前端暴露堆栈、SQL、表名或内部路径。至少覆盖：

- `PLATFORM_AUTH_REQUIRED`
- `PLATFORM_MFA_REQUIRED`
- `PLATFORM_ACCESS_DENIED`
- `PLATFORM_COMPANY_NOT_FOUND`
- `PLATFORM_INVALID_STATE_TRANSITION`
- `PLATFORM_CONCURRENT_MODIFICATION`
- `PLATFORM_IDEMPOTENCY_CONFLICT`
- `PLATFORM_APPROVAL_REQUIRED`
- `PLATFORM_APPROVAL_EXPIRED`
- `PLATFORM_QUOTA_EXCEEDED`
- `PLATFORM_JOB_NOT_RETRYABLE`

### 8.2 分页和排序

- `page` 从 0 开始；
- 默认 `size=20`，最大 `size=100`；
- 排序字段必须使用服务端白名单；
- 不接受任意实体字段名；
- 大数据导出走异步任务，不允许通过提高分页上限实现。

### 8.3 幂等和并发

- 生命周期、套餐变更、授权执行、任务重试必须携带 `Idempotency-Key`；
- 同一键、同一请求返回第一次结果；
- 同一键、不同请求指纹返回冲突；
- 可编辑控制面实体使用 `@Version`；
- 状态机变更使用行锁或带状态条件的原子更新。

### 8.4 请求追踪

- 接收或生成 `X-Request-Id`；
- 将请求 ID 写入结构化日志和平台审计；
- 不信任客户端提供的操作者、公司 ID 或权限信息；
- 真实客户端 IP 只从受信任反向代理链解析。

## 9. 测试策略

### 9.1 单元测试

- 每个公司生命周期合法和非法状态转换；
- MFA 过期、失败次数、锁定、恢复码单次消费；
- 角色、委派范围和数据集范围判定；
- 套餐资格合并和配额边界；
- 操作目录字段白名单；
- 审计安全摘要和敏感字段排除。

### 9.2 Repository 与迁移测试

- 平台账号、公司、订阅和授权唯一约束；
- 追加式审计不可更新、不可删除；
- 迁移链在生产结构副本通过；
- 平台控制面查询不受租户 RLS 错误过滤；
- 跨公司业务读取必须受 RLS 保护。

### 9.3 集成测试

至少建立公司 A、公司 B、平台超级管理员、普通平台管理员和公司管理员五类主体，验证：

- 公司 A 的 JWT 不能访问平台 API；
- 平台 JWT 不能访问公司业务 API；
- 未授权平台管理员不能读取公司目录；
- 只读角色不能导出；
- 导出角色不能写入；
- 超级管理员不能绕过公司批准直接修改或删除；
- 公司 A 管理员不能批准公司 B 的申请；
- 已批准操作无法改变申请之外的字段或资源；
- 已消费、撤销或过期的授权不能再次执行；
- 生命周期操作在重复请求和并发请求下只生效一次；
- 清理任务不会提前执行，失败可重试且不误标记成功；
- MFA 密钥、恢复码、密码和 JWT 不进入日志或审计详情。

### 9.4 发布门禁

- Java 全量测试通过；
- 前端契约类型与实时 OpenAPI 一致；
- Flyway 全链迁移和回滚预案完成演练；
- 双公司串租和越权测试通过；
- 导出目录、对象存储和清理任务经过真实文件验证；
- 生产数据库账号确认不是 `SUPERUSER` 或 `BYPASSRLS`；
- 平台 MFA 加密密钥、JWT 密钥、数据库凭证来自生产秘密管理；
- 备份恢复和 PITR 演练有证据；
- 未经单独批准，不开启生产多租户运行时开关、不部署、不创建额外平台账号。

## 10. 实施批次

### 批次 A：上线安全闭环（P0）

- 平台 JWT 独立有效期；
- MFA 管理收尾和挑战清理；
- `/auth/me`、全会话撤销；
- 平台审计动作扩展；
- 生产秘密与日志安全验收。

完成标准：平台账号不能绕过 MFA 获得 JWT，旧令牌可撤销，敏感信息不落日志。

### 批次 B：公司运营闭环（P0）

- 公司目录筛选和详情聚合；
- 生命周期 API；
- 清理任务查询和安全重试；
- 平台首页基础汇总。

完成标准：平台可以安全地查找、暂停、恢复和关闭公司，并能看到清理结果；所有操作可审计、可防重。

### 批次 C：套餐与配额（P0）

- 套餐管理 API；
- 订阅变更；
- 资格判定；
- 用户、仓库、SKU、集成配额；
- 试用到期策略实现。

完成标准：资源创建入口无法绕过配额，套餐变更有版本控制和审计。

### 批次 D：平台操作与任务中心（P0/P1）

- 公司授权操作列表、撤销和过期；
- 后台任务统一查询；
- 固定目录安全重试；
- 安全事件初版。

完成标准：高风险操作和失败任务都有明确负责人、状态、原因、结果和审计链。

### 批次 E：多管理员委派（P1）

- 平台管理员管理；其中第二名正式 `PLATFORM_SUPER_ADMIN` 的 24 小时单次邀请、强密码、首次 MFA 绑定与审计已于 2026-08-19 完成，普通管理员及账号生命周期仍未开发；
- 读取/导出委派授权；
- MFA 重置和会话撤销；
- 授权范围后端强制执行。

完成标准：普通平台管理员默认零跨公司权限，授权不能自授、转授或越过租户/数据集/有效期范围。

## 11. 暂不纳入本轮

- 不自动创建第二个平台账号或 break-glass 账号；第二名正式超级管理员的受控邀请能力已完成，但实际账号仍须由现有超级管理员明确填写另一名真实人员的独立邮箱后创建；
- 不实现任意 SQL、脚本或远程命令执行；
- 不实现平台模拟公司用户登录；
- 不实现公司数据全库一键导出；
- 不开放跳过 30 天保留期的在线永久删除；
- 不在套餐规则未确认前自动删除试用到期公司的数据；
- 不在 P0 中引入微服务、Kafka 或复杂计费引擎；
- 客服工单、支付、发票、开放平台和 AI 运营分析留到后续阶段。

## 12. 开发前需要产品确认的决策

1. FREE 最终包含哪些功能，用户、仓库、SKU、集成和文件容量分别是多少；
2. TRIAL 防滥用配额以及到期后降级、只读或升级提示策略；
3. 谁有权暂停、恢复和关闭公司，关闭是否需要第二位平台人员复核；
4. 在线数据保留期是否最终确认为 30 天；
5. 平台写入/删除许可的默认有效期和高风险双人审批范围；
6. 第二名正式超级管理员的邀请能力已确认并完成；后续仍需决定普通平台管理员的首批角色、账号生命周期和委派授权范围；
7. 是否启用 break-glass 账号及其保管、MFA、轮换和启用流程；
8. 正式域名是否使用 `bcwms.com`，以及反向代理和邮件域名方案；
9. 首期是否接入计费，还是仅完成套餐和人工订阅管理。

在以上决策未全部确认时，可以先完成批次 A 和不涉及商业规则的批次 B 基础能力；套餐数值、试用到期动作、第二管理员和 break-glass 账号不得由开发人员自行假设。
