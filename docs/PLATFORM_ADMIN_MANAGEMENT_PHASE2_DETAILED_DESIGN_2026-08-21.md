# BCWMS 平台管理员管理第二阶段详细设计（待确认草案）

> 日期：2026-08-21
> 状态：**待用户逐项确认，尚未冻结，不得据此直接实施**
> 适用仓库：`D:\ERP_WMS\BC warehouse\2G`
> 本文只记录设计；本轮未修改 Java/TypeScript、公共路由、数据库迁移或数据库数据。
> 实施迁移编号统一使用 `NEXT_AVAILABLE`，必须在第一阶段合并后重新确认。

## 0. 已确认设计决定

### D-01：平台管理员按岗位管理（2026-08-22 已确认）

用户已确认以下调整，本决定优先于本文后续尚未清理的旧草案描述：

1. `PLATFORM_TENANT_READ/EXPORT` 只是底层租户数据访问能力，不再作为邀请页面展示的“管理员类型”。
2. 平台管理员应按实际岗位建模，例如平台超级管理员、平台运营管理员、安全审计员；岗位控制可进入的平台管理模块。
3. 具体租户数据访问仍由现有“委派授权”单独控制：目标管理员、租户、数据集、READ/EXPORT 和有效期。
4. 新平台账号默认没有任何具体租户数据权限。
5. 当前第二阶段不建设“邀请时选择 READ/EXPORT 组合”和“账号创建后修改 READ/EXPORT 组合”功能。
6. 普通平台管理员邀请暂缓实施，直到平台岗位权限矩阵完成确认；现有第二名超级管理员邀请流程继续保留。
7. 不通过平台岗位或委派授权获得租户业务数据直接写入、删除或模拟租户用户登录能力。

T0 完成前将根据全部确认结果统一清理后文与 D-01 冲突的旧草案内容；在此之前不得依据冲突段落实施。

### D-02：另一名超级管理员受控重置目标 MFA（2026-08-22 已确认）

用户确认保持现有受控 MFA 重置机制，并补充正式运营规程：

1. 目标管理员手机遗失或认证器不可用时，先通过电话、视频或内部身份核验流程确认本人；身份核验结论和原因必须进入操作记录。
2. 只有另一名 `PLATFORM_SUPER_ADMIN` 可以执行；严格禁止操作者重置自己的 MFA。
3. 操作者必须使用自己的当前密码、自己的认证器 TOTP、5 分钟单次且绑定操作者与目标的挑战，并填写必填原因。
4. 操作者不能查看目标旧 MFA 秘密、恢复码或密码，也不能替目标绑定新认证器或模拟目标登录。
5. 成功后清除目标旧认证器、恢复码、锁定和未完成挑战，推进 `security_version` 并撤销目标全部旧平台会话；保留密码、岗位、委派授权、任务、业务数据和历史审计。
6. 目标下次使用自己的邮箱和原密码登录，重新扫描二维码、验证新认证器，并保存只显示一次的新恢复码；完成前不签发平台 JWT。
7. 当前最多两名超级管理员的治理条件下，首版不增加“两个重置审批人”的第三人审批机制；操作者与目标必须是不同人员。
8. 继续使用独立 `MFA_RESET` 审计语义，记录请求、失败或成功，但绝不记录密码、TOTP、MFA 秘密、恢复码或挑战令牌。

### D-03：会话撤销、账号停用与恢复启用（2026-08-22 已确认）

用户确认三个安全操作必须独立建模和展示：

1. **撤销目标全部会话**：用于办公电脑遗失或怀疑会话泄露；推进目标 `security_version`，使全部旧 JWT 失效，但目标账号仍启用，密码、MFA、岗位和授权不变，可在安全设备重新登录。
2. **重置目标 MFA**：按 D-02 处理手机或认证器遗失；清除旧认证器和恢复码，目标下次登录重新绑定。
3. **停用账号**：用于离职、停职或禁止继续访问；立即撤销全部旧会话并拒绝后续登录，但保留密码、MFA、岗位、委派授权、任务、数据和历史审计。
4. **恢复启用**：旧 JWT 不恢复，目标必须重新登录，通常无需重新绑定 MFA；页面必须提示操作者复查目标仍未过期的委派授权。
5. 撤销目标会话、停用和恢复启用均要求操作者自己的当前密码、认证器 TOTP、5 分钟单次且绑定操作者/目标/动作的挑战，以及 1–500 字必填原因。
6. 恢复码不能替代上述高风险操作的 TOTP。
7. 操作者不能通过目标接口撤销自己的会话，也不能停用自己；本人会话撤销继续使用已有“退出所有设备”。
8. 不能停用最后一名启用的超级管理员；所有保护必须由后端在最终事务中重新验证，不能只依赖前端按钮。
9. 审计分别使用 `ADMIN_SESSIONS_REVOKED`、`ADMIN_DISABLED` 和 `ADMIN_ENABLED`，不得合并为含义模糊的“重置账号”。

### D-04：管理员账号治理规则（2026-08-22 已确认）

1. 首版管理员目录仅 `PLATFORM_SUPER_ADMIN` 可见和操作；未来安全审计员岗位可以另行获得只读目录能力，但不能执行邀请、启停、会话撤销或 MFA 重置。
2. 平台管理员账号不能物理删除；离职或不再使用时停用，保留完整身份和审计历史。
3. 邮箱作为登录身份和审计归属，首版不允许编辑。
4. MFA 连续 5 次验证失败锁定 15 分钟，时间到后自动解除；首版不提供人工解除临时锁定按钮，临时输错不得用 MFA 重置代替。
5. 超级管理员上限采用“启用账号 + 尚未过期且未撤销的超级管理员邀请，合计最多 2 个”的活动席位口径。
6. 已停用但仍持有超级管理员岗位的账号不占活动席位且不能登录；恢复启用时必须在同一受控事务中重新校验仍有可用席位。
7. 停用离职超级管理员后允许邀请继任者；原账号继续保留且可在目录和审计中查询。

### D-05：T1 管理员目录契约与边界（2026-08-22 已确认）

1. 目录字段：ID、姓名、邮箱、现有平台岗位/角色、enabled、MFA 派生状态、MFA 绑定时间、当前临时锁定到期、创建/更新时间、当前有效委派授权数量、当前账号标记和最后启用超级管理员保护标记。
2. 当前没有可靠最后登录数据源，首版不返回 `lastLoginAt`。
3. 支持姓名/邮箱关键词、enabled、平台岗位/角色、MFA 状态筛选；后端分页默认 20、最大 100，固定按创建时间和 ID 倒序。
4. 输入筛选条件不自动请求，只有显式查询、翻页或重试才读取。
5. 每次目录分页查询只写一条 `ADMIN_DIRECTORY_READ` 汇总审计，不按返回账号逐条审计。
6. 严禁返回密码/散列、MFA 秘密/密文、恢复码/散列、JWT、挑战令牌、邀请原始令牌或租户业务数据。
7. T1 只实施 `GET /api/platform/admins` 和 `GET /api/platform/admins/{targetUserId}` 的后端只读闭环及测试。
8. T1 不修改前端公共文件，不启停账号、不撤销会话、不重置 MFA、不创建邀请，也不修改真实数据库账号数据。

### D-06：现有技术角色与未来平台岗位的过渡（2026-08-22 已确认）

1. T1 目录如实返回数据库当前角色，不虚构尚不存在的平台运营管理员或安全审计员岗位。
2. `PLATFORM_SUPER_ADMIN` 显示为“平台超级管理员”。
3. `PLATFORM_TENANT_READ/EXPORT` 分别显示为“租户数据读取能力/租户数据导出能力”，明确标注为底层技术能力而非岗位。
4. 发现其他现有角色时原样安全返回并标记为“未分类角色”，不得静默隐藏或自动修改。
5. T1 不提供角色修改入口；普通平台账号不能通过普通角色修改提升为超级管理员，超级管理员也不能通过普通角色修改降级。
6. 新增超级管理员继续使用现有独立邀请流程和活动席位上限。
7. 平台运营管理员、安全审计员等岗位在 T5 单独冻结模块权限矩阵和迁移设计；岗位确认前不开放普通平台管理员邀请。

## 1. 执行摘要

第二阶段把现有“第二名超级管理员邀请页”扩展为独立的平台管理员生命周期管理中心，但仍只管理 `platform_users` 中的平台账号，不接触租户 ERP 用户、租户角色、租户 JWT 或租户业务数据。

本草案推荐：

1. 管理员目录首版即使用后端分页、关键词搜索和状态/角色/MFA 筛选，仅超级管理员可见。
2. 普通管理员邀请复用现有 24 小时单次链接、强密码、首次 MFA 绑定和恢复码一次性展示流程，只允许 READ、EXPORT 或二者组合；激活后默认零具体租户授权。
3. 启停、角色变更和目标会话撤销均采用 5 分钟单次挑战、当前密码、认证器 TOTP、必填原因和服务端幂等控制。
4. 首版允许普通管理员在激活后调整 READ/EXPORT 组合；不允许普通管理员升为超级管理员，也不允许超级管理员降级。
5. 停用保留角色、MFA、恢复码和授权记录，授权因账号停用立即失效；恢复启用前强提醒复查仍有效授权。
6. 移除 READ/EXPORT 角色时，推荐把对应未撤销授权批量标记为撤销，避免以后重新加回角色时旧授权静默复活。
7. 增加独立“撤销目标全部会话”；它只推进目标 `security_version`，不改变账号、角色、MFA 或授权。
8. 首版不做账号删除、邮箱编辑、手工解除短期 MFA 锁定、超级管理员升降级、写删角色分配或复杂 MFA 密钥轮换。

## 2. 当前实现盘点与差距

### 2.1 已确认可复用基线

- 平台账号、角色和租户账号完全隔离，平台账号位于 `platform_users`。
- 平台密码登录后必须完成 TOTP MFA 才签发平台 JWT。
- 平台 JWT 最长 12 小时，前端连续 30 分钟无操作退出。
- 本人“退出所有设备”、恢复码重新生成、管理员受控 MFA 重置均已有 5 分钟挑战基础。
- 第二名超级管理员已有 24 小时单次邀请、强密码、MFA 和恢复码激活流程。
- 普通管理员读取/导出必须同时具备候选角色和精确到租户、数据集、能力、有效期的 `platform_access_grants`。
- `platform_audit_logs` 已有数据库触发器阻止 UPDATE/DELETE。
- 平台 JWT 每次请求会重新加载启用状态和角色，并比对 JWT 中的角色与 `security_version`。

### 2.2 代码核对后的真实差距

| 领域 | 当前事实 | 第二阶段缺口 |
|---|---|---|
| 管理员目录 | 页面复用 `GET /api/platform/access-grants/users` | 缺正式分页目录、完整角色、时间、MFA 锁定和授权摘要 |
| 查询性能 | `findAll()` 后逐账号查询角色 | 存在 N+1，不能作为正式目录 |
| 启停 | `platform_users.enabled` 已存在 | 无正式 API、并发保护、二次验证、审计动作 |
| 角色变更 | `platform_user_roles` 有唯一约束 | 无受控 READ/EXPORT 替换接口；无角色变更审计 |
| 会话撤销 | 只有本人退出所有设备和 MFA 重置附带撤销 | 无超级管理员对目标账号的独立撤销接口 |
| 普通邀请 | 父表只有一个 `role_code`，数据库只允许超级管理员 | 无 READ+EXPORT 快照表达能力 |
| 有效邀请唯一 | 仅先查询 `hasActiveInvitation()` | 并发时可能插入同邮箱的多个有效邀请 |
| 超级管理员上限 | 仅先统计再写入 | 并发创建/接受/启停间仍有竞态窗口 |
| 二次验证 | 挑战可绑定操作者、目标和 purpose | 不能绑定目标状态、目标角色集合或目标版本 |
| 幂等 | 邀请接受依靠邀请行锁；其他高风险命令无统一记录 | 网络重试和按钮双击无法稳定重放结果 |
| 错误契约 | 多处抛出 `IllegalArgumentException`/`NoSuchElementException` | 会落入通用 500，缺稳定平台错误键 |
| 审计事务 | 默认 `record()` 使用 `REQUIRES_NEW` | 成功审计可能先提交而外层业务随后回滚；新业务需区分成功/失败事务 |
| 数据库角色 | 还存在历史 `PLATFORM_TENANT_WRITE/DELETE` | 本阶段不得把它们列为普通管理员可分配角色，也不得擅自删除 |

### 2.3 不得虚构的字段

当前没有可靠、脱敏且已定义口径的管理员最后登录时间，因此首版目录和详情均不返回 `lastLoginAt`。未来若增加，必须先设计独立登录成功事件或安全聚合来源。

## 3. 第二阶段范围与明确不做

### 3.1 本阶段范围

- 独立管理员目录和详情。
- 普通管理员邀请、邀请历史、撤销和激活。
- 单账号启用/停用。
- 普通管理员 READ/EXPORT 角色组合变更。
- 目标管理员全部平台会话撤销。
- 现有 MFA 重置入口整合到管理员安全操作区。
- 新增审计动作、稳定错误键、并发与幂等保护。
- 对应前后端、迁移契约、并发和人工验收设计。

### 3.2 明确不做

- 不删除平台账号，不编辑管理员邮箱。
- 不允许普通管理员提升为超级管理员，不允许超级管理员降级。
- 不向普通管理员分配 `PLATFORM_TENANT_WRITE`、`PLATFORM_TENANT_DELETE` 或未知角色。
- 不增加租户业务数据写入/删除能力。
- 不提供普通 MFA 临时锁定手工解锁。
- 不修改租户 ERP 用户、角色、JWT、会话或业务数据。
- 不实现 SSO、SAML、OIDC、SCIM、Passkey、邮件自动发送、break-glass 自动化或复杂 MFA 密钥轮换。

## 4. 角色与能力矩阵

| 角色 | 管理管理员 | 租户目录 | 数据读取 | 数据导出 | 直接写入/删除 |
|---|---:|---:|---:|---:|---:|
| `PLATFORM_SUPER_ADMIN` | 是 | 全部未清除租户 | 固定数据集 | 固定数据集 | 否 |
| `PLATFORM_TENANT_READ` | 否 | 仅有效 READ 授权涉及租户 | 仅精确 READ 授权范围 | 否，除非另有 EXPORT | 否 |
| `PLATFORM_TENANT_EXPORT` | 否 | 仅有效 EXPORT 授权涉及租户 | 否，除非另有 READ | 仅精确 EXPORT 授权范围 | 否 |

说明：数据库中历史存在的 `PLATFORM_TENANT_WRITE/DELETE` 继续由既有“租户批准的固定操作目录”控制。本阶段角色选择器、普通邀请和角色变更 API 必须拒绝这两个角色；不得因为它们存在于数据库就把它们开放为管理员管理能力。

## 5. 操作者/目标权限矩阵

`允许（受控）`表示仍需最终接口的密码、TOTP、挑战、原因和并发校验。

| 操作者与目标 | 目录 | 邀请 | 撤销邀请 | 启用 | 停用 | 改角色 | 撤销目标会话 | MFA 重置 | 委派授权 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 超级管理员 → 自己 | 允许 | 允许 | 允许 | 不适用 | 禁止 | 禁止 | 禁止，走本人退出所有设备 | 禁止 | 禁止自授权 |
| 超级管理员 → 另一超级管理员 | 允许 | 允许 | 允许 | 允许，受上限约束 | 允许，但不得留下 0 名启用超级管理员 | 禁止升降级 | 允许（受控） | 允许（受控） | 超级管理员不走委派 |
| 超级管理员 → 普通管理员 | 允许 | 允许 | 允许 | 允许（受控） | 允许（受控） | 允许 READ/EXPORT（受控） | 允许（受控） | 已绑定 MFA 时允许（受控） | 允许，仍不得自授权 |
| 普通管理员 → 自己 | 禁止看目录 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止，走本人退出所有设备 | 禁止 | 只能读本人有效授权摘要 |
| 普通管理员 → 其他管理员 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| 未认证请求 | 401/403 | 401/403 | 401/403 | 401/403 | 401/403 | 401/403 | 401/403 | 401/403 | 401/403 |
| 已停用账号持旧 JWT | 401，安全链拒绝 | 同左 | 同左 | 同左 | 同左 | 同左 | 同左 | 同左 | 同左 |

普通管理员仍可使用其已有的本人会话管理、恢复码管理和授权范围内租户能力，但不能进入管理员目录。

## 6. 数据模型与 ER 关系

### 6.1 当前核心关系

```text
platform_users 1 ── * platform_user_roles * ── 1 platform_roles
platform_users 1 ── * platform_mfa_challenges
platform_users 1 ── * platform_access_grants
platform_users 1 ── * platform_admin_invitations（inviter/accepted/revoker）
platform_users 1 ── * platform_audit_logs（actor）
```

### 6.2 推荐新增/扩展关系

```text
platform_admin_invitations 1 ── * platform_admin_invitation_roles
platform_admin_invitations 1 ── 0..1 platform_admin_invitation_active_emails
platform_users 1 ── * platform_admin_commands（actor/target）
platform_users 1 ── * platform_audit_logs（target_platform_user_id，可空）
platform_mfa_challenges ── action_context_hash + target_security_version
```

### 6.3 邀请角色：方案比较

#### 方案 A：扩展父表单个 `role_code`

可把列改成 JSON、逗号字符串或继续只保存一个角色，但 READ+EXPORT 会导致解析、唯一性、外键和白名单约束变弱；查询和审计也难以保证角色快照不可歧义。

#### 方案 B：标准化子表（推荐）

新增 `platform_admin_invitation_roles(invitation_id, invitation_type, role_code, created_at)`：

- 主键/唯一：`(invitation_id, role_code)`。
- 外键：`invitation_id -> platform_admin_invitations(id)`，`role_code -> platform_roles(role_code)`。
- `invitation_type` 为 `SUPER_ADMIN` 或 `DELEGATED_ADMIN`。
- 数据库检查：超级邀请只能有 `PLATFORM_SUPER_ADMIN`；普通邀请只能有 READ/EXPORT。
- 普通邀请至少 1 个、最多 2 个互异角色；超级邀请恰好 1 个角色。
- 激活时锁定邀请行并重新读取角色子表，再次执行白名单和数量校验。

兼容迁移采用 expand/contract：先给父表增加 `invitation_type`，创建子表并把历史 `role_code` 回填；父表原 `role_code` 暂时保留为可空兼容列，但新代码只把子表作为事实来源。稳定一个发布周期后再单独评估删除旧列，第二阶段首个迁移不做破坏性删除。

### 6.4 同邮箱有效邀请唯一

PostgreSQL 部分唯一索引不能安全使用 `now()` 判断过期。推荐新增活动邮箱占位表：

```text
platform_admin_invitation_active_emails
- normalized_email PK
- invitation_id UNIQUE FK
- created_at
```

创建邀请时在同一事务插入占位；并发重复邮箱由主键唯一约束裁决。若占位指向已过期邀请，事务先锁定并清理旧占位再重试。接受或撤销邀请时与终态更新同事务删除占位。这样无需安装 `btree_gist` 扩展，也不依赖定时任务及时清理。

### 6.5 二次验证挑战扩展

继续复用 `platform_mfa_challenges`，候选迁移：

- `purpose` 扩为 `VARCHAR(40)`。
- 新增 `action_context_hash CHAR(64)`：保存规范化目标状态或排序后角色集合的 SHA-256，不保存密码、TOTP 或挑战原文。
- 新增 `target_security_version BIGINT`：创建挑战时记录目标版本，执行时检测目标是否在弹窗期间发生变化。
- 新 purpose：`ADMIN_STATUS_CHANGE`、`ADMIN_ROLES_CHANGE`、`ADMIN_SESSIONS_REVOKE`；保留现有值。

最终请求必须同时匹配操作者、目标、purpose、上下文散列、目标版本、未过期、未消费和尝试次数。

### 6.6 高风险命令幂等记录

推荐新增 `platform_admin_commands`：

| 字段 | 约束/用途 |
|---|---|
| `id` | 主键 |
| `actor_platform_user_id` | 非空外键 |
| `target_platform_user_id` | 可空外键；邀请创建时可空 |
| `idempotency_key` | 1–80 字符 |
| `action` | 固定白名单 |
| `request_fingerprint` | 不含密码/TOTP/令牌的规范化意图 SHA-256 |
| `status` | `IN_PROGRESS/SUCCEEDED`；失败事务回滚后可重试 |
| `safe_response_json` | 只保存安全响应摘要，不保存邀请原始链接 |
| `created_at/completed_at` | 时间 |

唯一约束：`(actor_platform_user_id, idempotency_key)`。同键同指纹成功后重放安全响应；同键不同指纹返回 409。邀请链接仍只在首次成功响应出现一次，重放不返回原始链接。

### 6.7 审计扩展

给 `platform_audit_logs` 增加可空 `target_platform_user_id` 外键和索引。`platform_user_id` 继续表示操作者；邀请尚未激活时目标 ID 可空，使用目标邮箱安全快照。

### 6.8 保持的现有约束

- `platform_users.normalized_email` 已有 lower/trim 检查和唯一约束。
- `platform_user_roles(platform_user_id, platform_role_id)` 已有唯一约束。
- 邀请和 MFA 挑战只存令牌 SHA-256 散列。
- 密码继续只存 BCrypt；TOTP 秘密继续 AES-GCM 加密；恢复码继续慢哈希。
- 本阶段不删除既有两名超级管理员、历史邀请、MFA、恢复码、授权、任务或审计。

## 7. 候选迁移设计（仅设计）

建议在实施时以一个 `NEXT_AVAILABLE__Platform_Admin_Management_Phase2.sql` 为最小原子迁移起点；若实际审查认为过大，再拆成连续两个 `NEXT_AVAILABLE`，但不得现在占号。

候选内容：

1. 扩展 `platform_admin_invitations`：增加 `invitation_type`，历史记录回填为 `SUPER_ADMIN`，旧 `role_code` 改为兼容可空列。
2. 创建 `platform_admin_invitation_roles`，回填历史角色，增加外键、唯一和白名单检查。
3. 创建 `platform_admin_invitation_active_emails`，迁移前检测同邮箱多条未终止且未过期记录；发现冲突则迁移失败并输出安全计数，不自动删除历史。
4. 扩展 `platform_mfa_challenges` 的 purpose、上下文散列和目标版本。
5. 创建 `platform_admin_commands` 及幂等唯一约束。
6. 给 `platform_audit_logs` 增加 `target_platform_user_id` 和索引，扩展审计 action CHECK。
7. 增加目录所需索引：`platform_users(enabled, created_at, id)`、角色关联查询索引，以及有效授权按 grantee/有效期的批量统计索引（先 EXPLAIN 再决定具体顺序）。
8. 迁移契约测试必须验证历史超级邀请完成回填，且原有两名超级管理员和 MFA 字段完全不变。

不在迁移中写真实邮箱、密码、令牌、密钥或演示账号；不执行实际账号启停。

## 8. API 契约

### 8.1 通用规则

- 所有管理接口仅 `PLATFORM_SUPER_ADMIN`。
- `page` 从 0 开始，默认 `size=20`，最大 100。
- 邮箱输入统一 `trim + lower(Locale.ROOT)`。
- 所有高风险最终命令要求 `Idempotency-Key` 请求头，1–80 字符。
- 原因 `reason`：trim 后 1–500 字符。
- 密码：非空但不进入日志；TOTP：严格 6 位数字，恢复码不可替代二次验证。
- 成功响应和 DTO 的 `toString()` 均不得包含秘密。
- 下面列出的 401/403/409 均返回稳定 `errorKey`，不返回堆栈、SQL、表名或内部路径。

### 8.2 管理员目录

```http
GET /api/platform/admins?page=0&size=20&keyword=&enabled=&role=&mfaStatus=
```

筛选：

- `keyword` 最长 100；匹配标准化邮箱或显示名称，不在每次输入时自动请求。
- `enabled`：`true/false`。
- `role`：固定平台角色代码；目录可查询历史 WRITE/DELETE 持有者，但 UI 只把 READ/EXPORT 作为可编辑项。
- `mfaStatus`：`PENDING_ENROLLMENT/ENROLLED/TEMPORARILY_LOCKED`。
- 固定排序：`createdAt DESC, id DESC`；首版不接受任意排序字段。

`200 PlatformPage<PlatformAdminSummaryResponse>`：

```text
id, email, displayName, enabled, roles[],
mfaStatus, mfaEnrolledAt, mfaLockedUntil,
createdAt, updatedAt, activeGrantCount,
currentUser, lastEnabledSuperAdmin
```

MFA 派生规则：锁定时间晚于当前时间为 `TEMPORARILY_LOCKED`；完整启用且存在登记时间为 `ENROLLED`；其他为 `PENDING_ENROLLMENT`。任何字段不一致只按安全降级状态返回并触发内部告警，不暴露密文。

查询实现使用分页主查询 + 当前页 ID 的批量角色聚合 + 当前页 ID 的有效授权分组统计，禁止逐用户查询。

审计：`ADMIN_DIRECTORY_READ`，一次分页请求一条汇总，记录安全筛选摘要、页码、页大小、返回数和总数。

### 8.3 管理员详情

```http
GET /api/platform/admins/{targetUserId}
```

`200` 返回目录字段，并增加 `activeGrantCounts: {READ, EXPORT}`。不返回完整授权业务数据；需要查看授权明细时继续进入现有委派授权页。

失败：`PLATFORM_ADMIN_NOT_FOUND` 404、`AUTH_ACCESS_DENIED` 403。

### 8.4 启停挑战

```http
POST /api/platform/admins/{targetUserId}/status-change/challenge
Content-Type: application/json

{ "desiredEnabled": false }
```

校验：不能为自己创建停用挑战；目标存在；若目标为最后一名启用超级管理员立即返回冲突；操作者已绑定 MFA 且未临时锁定。

`200`：`{ challengeToken, expiresIn, targetSecurityVersion }`。挑战绑定操作者、目标、`desiredEnabled` 和目标版本。

### 8.5 停用/启用

```http
POST /api/platform/admins/{targetUserId}/disable
POST /api/platform/admins/{targetUserId}/enable
Idempotency-Key: <opaque-key>

{
  "challengeToken": "...",
  "password": "...",
  "code": "123456",
  "reason": "..."
}
```

`200 PlatformAdminMutationResponse`：

```text
targetUserId, action, changed, enabled, roles[], securityVersion, completedAt
```

停用事务：

1. 重新验证权限和挑战绑定。
2. 按统一锁顺序锁定必要的超级管理员角色互斥行和目标账号。
3. 再次检查非本人、最后超级管理员、目标版本和当前状态。
4. 若需变更，设置 `enabled=false`，原子推进 `security_version`，删除目标未完成 MFA/再次验证挑战。
5. 保留角色、MFA、恢复码、授权、任务和审计。
6. 在同一事务写 `ADMIN_DISABLED/SUCCESS` 后提交。

启用事务类似；不会减少 `security_version`，旧 JWT 永不恢复。目标必须重新完成密码+MFA 登录。响应同时返回有效授权计数，前端必须提醒复查。

重复目标状态返回 `changed=false`，不重复推进版本；同一幂等键重放首次安全响应。

### 8.6 角色变更挑战

```http
POST /api/platform/admins/{targetUserId}/roles/change/challenge

{ "roles": ["PLATFORM_TENANT_READ", "PLATFORM_TENANT_EXPORT"] }
```

角色必须是 1–2 个互异值，且只来自 READ/EXPORT。目标必须是已启用的普通管理员；超级管理员、历史 WRITE/DELETE 持有者或未知角色持有者首版拒绝在线变更。

挑战上下文散列绑定排序后的角色集合和目标版本。

### 8.7 替换普通管理员角色

```http
PUT /api/platform/admins/{targetUserId}/roles
Idempotency-Key: <opaque-key>

{
  "challengeToken": "...",
  "password": "...",
  "code": "123456",
  "reason": "...",
  "roles": ["PLATFORM_TENANT_READ"]
}
```

`200 PlatformAdminMutationResponse`，附 `beforeRoles`、`afterRoles`、`revokedGrantCount`。

规则：

- 这是完整替换，不是逐条追加，避免部分成功。
- 相同角色集合返回 `changed=false`。
- 变更成功原子推进目标 `security_version`，旧 JWT 下一请求失败。
- 推荐规则：移除 READ 时撤销所有仍未撤销的 READ 授权；移除 EXPORT 时同理。授权行保留，只填 `revoked_at/revoked_by`，不物理删除。
- 新增角色不会自动创建任何授权。
- 审计 `ADMIN_ROLES_CHANGED` 记录前后角色和批量撤销数量；关联授权撤销写汇总安全摘要。

### 8.8 目标会话撤销

```http
POST /api/platform/admins/{targetUserId}/sessions/revoke/challenge
POST /api/platform/admins/{targetUserId}/sessions/revoke
Idempotency-Key: <opaque-key>
```

最终 body 与启停相同。操作者不能以此撤销自己的会话；本人继续使用现有“退出所有设备”。目标必须启用；停用目标返回 `PLATFORM_ADMIN_DISABLED`，因为其旧会话已经失效。

成功仅原子推进目标 `security_version` 并清理目标未完成挑战，不修改 enabled、角色、MFA、恢复码、授权或任务。审计为 `ADMIN_SESSIONS_REVOKED`，不能复用 `SESSIONS_REVOKED`。

### 8.9 管理员邀请

保留现有路径：

```text
POST /api/platform/admins/invitations/challenge
POST /api/platform/admins/invitations
GET  /api/platform/admins/invitations?page=0&size=20&keyword=&status=&invitationType=
POST /api/platform/admins/invitations/{id}/revoke
```

创建请求在现有字段上增加：

```json
{
  "challengeToken": "...",
  "password": "...",
  "code": "123456",
  "email": "delegate@example.com",
  "displayName": "Named Person",
  "reason": "Operational read access",
  "roles": ["PLATFORM_TENANT_READ", "PLATFORM_TENANT_EXPORT"]
}
```

兼容策略：同一路径继续使用；实施发布时前端一并显式发送 `roles[]`。为兼容升级瞬间的旧前端，请求缺少 `roles` 时可暂时按旧语义视为仅 `PLATFORM_SUPER_ADMIN`，并记录弃用日志；普通管理员邀请绝不依赖默认值。

创建成功 `201`，原始激活路径仍只返回一次。普通邀请激活后角色写入同一事务，但不创建任何 `platform_access_grants`。

列表响应改为分页，角色字段为 `roles[]`；单角色时可在一个兼容周期保留只读 `roleCode`。

撤销请求增加 `{ "reason": "..." }`，不要求再次密码/TOTP，因为撤销只降低权限；必须锁定邀请行、幂等处理并与活动邮箱占位删除同事务。若已接受返回 409，已撤销返回 `changed=false`。

公开接口继续保留：

```text
POST /api/platform/auth/invitations/status
POST /api/platform/auth/invitations/activate
```

公开失败统一返回现有的泛化无效邀请语义，不区分不存在、过期、撤销或已接受，防止令牌探测。激活响应在 MFA 完成前不签发 JWT。

### 8.10 现有 MFA 重置

现有路径不重命名：

```text
POST /api/platform/admins/{targetUserId}/mfa-reset/challenge
POST /api/platform/admins/{targetUserId}/mfa-reset
```

只在管理员详情安全操作区整合。继续禁止自重置，目标未绑定 MFA 时不显示按钮；成功影响范围维持现状。

## 9. 稳定错误键与 HTTP 状态

| HTTP | errorKey | 语义 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | 字段、长度、角色组合或查询参数非法 |
| 401 | `AUTH_FAILED` + `MFA_VERIFICATION_FAILED` | 密码/TOTP/挑战失败，保持现有前端再验证语义 |
| 403 | `AUTH_ACCESS_DENIED` | 非超级管理员或禁止的自操作 |
| 404 | `PLATFORM_ADMIN_NOT_FOUND` | 受保护管理接口目标不存在 |
| 404 | `PLATFORM_ADMIN_INVITATION_NOT_FOUND` | 管理端邀请不存在 |
| 409 | `PLATFORM_ADMIN_LAST_SUPER_ADMIN` | 会导致无启用超级管理员 |
| 409 | `PLATFORM_ADMIN_SUPER_ADMIN_LIMIT` | 超级管理员名额规则冲突 |
| 409 | `PLATFORM_ADMIN_STATUS_CONFLICT` | 目标状态在挑战后变化 |
| 409 | `PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN` | 超级管理员或不可管理角色集合 |
| 409 | `PLATFORM_ADMIN_DISABLED` | 操作要求启用目标但目标已停用 |
| 409 | `PLATFORM_ADMIN_CONCURRENT_MODIFICATION` | 目标版本变化或并发终态冲突 |
| 409 | `PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT` | 同键不同请求指纹 |
| 409 | `PLATFORM_ADMIN_EMAIL_EXISTS` | 已存在平台账号 |
| 409 | `PLATFORM_ADMIN_INVITATION_ACTIVE` | 同邮箱已有有效邀请 |
| 409 | `PLATFORM_ADMIN_INVITATION_TERMINAL` | 已接受邀请不能撤销等终态冲突 |

上述键实施时必须加入 `ErrorKeys` 和全局 HTTP 映射，禁止继续用 `IllegalArgumentException` 让预期业务冲突落为 500。

## 10. 二次验证、事务、并发与幂等

### 10.1 统一锁顺序

所有会影响超级管理员名额/最后管理员保护的事务使用 `platform_roles` 中 `PLATFORM_SUPER_ADMIN` 行作为数据库互斥点（`SELECT ... FOR UPDATE`），然后锁目标账号/邀请。所有相关服务必须遵守同一锁顺序，避免死锁。

角色普通变更、普通账号启停同时竞争时都锁定目标 `platform_users` 行：

- 停用先提交：后到的角色变更发现目标已停用，返回 409。
- 角色变更先提交：角色先更新并撤销旧会话，随后停用可继续，最终状态为“新角色 + 停用”；两条审计均保留。

### 10.2 最后一名超级管理员

不能依赖前端或普通 count-then-write。最终停用事务在持有超级管理员互斥行锁后，重新统计启用超级管理员；若目标是超级管理员且统计不大于 1，则拒绝。超级邀请创建/接受和超级管理员恢复启用也使用同一互斥点检查上限。

### 10.3 安全版本原子推进

采用带行锁的实体更新或单条 SQL：

```text
security_version = security_version + 1
```

不得先在无锁快照读取、再按旧值覆盖。提交后旧 JWT 在下一请求因启用状态、角色快照或 security_version 不匹配而被拒绝。

### 10.4 邀请接受与撤销

两者都用 `findById/TokenForUpdate` 锁定同一邀请行，并在锁内检查终态；只有一个事务能从 ACTIVE 进入 ACCEPTED 或 REVOKED。账号、角色、邀请终态、活动邮箱占位和 `ADMIN_INVITATION_ACCEPTED` 成功审计处于同一事务。

### 10.5 审计事务原则

- **成功审计与业务变更同一事务**，使用现有 `recordInCurrentTransaction` 或等价 MANDATORY 方法；审计写入失败则业务回滚。
- **失败审计**在业务回滚后由外层边界使用独立事务写入，记录失败类别而不记录秘密。
- 不在外层业务仍可能回滚时提前以 `REQUIRES_NEW` 写 `SUCCESS`。
- 公开邀请激活继续沿用已经修复的“新用户+角色+邀请+挑战+审计同事务”模式。

### 10.6 幂等

- 状态、角色和目标会话命令要求 `Idempotency-Key`。
- 同键同指纹且已成功：返回安全结果，不再次改状态/推进版本/写成功审计。
- 同键不同指纹：409。
- 同一挑战只能首次执行；幂等重放依靠命令记录，不再次校验或消费凭证。
- 邀请创建依靠活动邮箱唯一占位和幂等键避免重复；原始链接不缓存、不重放。若首次响应丢失，管理员只能查看已创建邀请并受控撤销后重建，不能从数据库恢复原始令牌。
- 前端提交后立即禁用按钮，但前端防双击不是最终安全边界。

## 11. 审计动作与字段排除

### 11.1 动作

- `ADMIN_DIRECTORY_READ`
- `ADMIN_INVITED`（已有，扩展普通角色数组）
- `ADMIN_INVITATION_REVOKED`（已有）
- `ADMIN_INVITATION_ACCEPTED`（已有）
- `ADMIN_DISABLED`
- `ADMIN_ENABLED`
- `ADMIN_ROLES_CHANGED`
- `ADMIN_SESSIONS_REVOKED`
- `MFA_RESET`（已有）
- 首版不增加 `MFA_UNLOCKED`

### 11.2 安全摘要

允许：操作者 ID、目标 ID、目标邮箱安全快照、前后 enabled/roles、受影响授权数量、原因、结果、请求 ID、受信任来源 IP、时间、幂等键的不可逆摘要。

禁止：密码/散列、JWT、Authorization 头、TOTP/共享秘密/密文、恢复码/散列、MFA 挑战原文/散列、邀请原始令牌/散列、完整请求头、浏览器存储、租户业务记录、完整导出内容。

## 12. 前端信息架构与交互

“管理员管理”页面分为三个区域或标签：

1. **管理员目录**：筛选、分页、刷新、状态与安全摘要。
2. **邀请与记录**：普通/超级邀请入口、角色选择、邀请历史和撤销。
3. **管理员详情/安全操作区**：启停、角色、目标会话撤销、MFA 重置。

### 12.1 列表字段

姓名、邮箱、全部角色、启用状态、MFA 状态/锁定到期、创建时间、有效授权数。当前账号显示“当前账号”标签；最后一名启用超级管理员显示保护提示。

### 12.2 按钮规则

- 当前账号：不显示停用、目标会话撤销、MFA 重置或角色编辑；本人会话管理留在现有首页。
- 另一超级管理员：不显示角色编辑；按最终并发结果决定是否可停用；可撤销会话和重置 MFA。
- 普通管理员：启用时可停用、改角色、撤销会话和在已绑定 MFA 时重置；停用时只显示恢复启用和查看摘要。
- 最后一名启用超级管理员：停用按钮隐藏/禁用并解释原因，但后端仍重查。

### 12.3 高风险弹窗

每个操作独立命名，不使用“重置账号”：

- 停用账号：说明立即退出全部设备、保留 MFA/角色/授权、恢复后旧授权可能重生效。
- 恢复启用：显示 READ/EXPORT 有效授权计数并要求复查。
- 修改角色：显示前后角色；若移除角色，显示将批量撤销对应授权的数量。
- 撤销该账号全部会话：明确“不停用账号、不改角色、不重置 MFA”。
- 重置 MFA：沿用现有影响说明。

最终弹窗字段：影响说明、必填原因、当前密码、认证器当前 6 位 TOTP、明确确认勾选。挑战在弹窗确认提交前创建，令牌只放组件内存。

### 12.4 状态与文案

- 成功后刷新目标详情和当前目录页。
- 二次验证失败不退出当前有效会话；只有平台 JWT 本身被拒绝才退出。
- 409 显示“目标状态已变化，请刷新后重新操作”，不自动重试高风险命令。
- 中英文使用稳定 i18n 键；角色代码可保留技术代码，同时提供中文含义。
- 提供加载、空结果、失败重试、无权限状态。
- 窄窗口下筛选和操作区换行，表格在自身容器横向滚动，不撑宽整页；危险操作在移动端使用纵向抽屉/弹窗。
- UI 不显示或复制密码、TOTP、MFA 秘密、恢复码、挑战令牌或历史邀请链接。

## 13. 验收测试计划

### 13.1 后端/迁移

- 迁移全链通过；历史超级邀请角色子表回填正确。
- 同邮箱并发邀请只有一个成功。
- 超级管理员名额并发创建/接受/启用受同一互斥保护。
- 目录分页、搜索、筛选和批量角色/授权统计无 N+1。
- 目录 DTO 的序列化和 `toString()` 不含敏感字段。
- 非超级管理员不能读取目录或执行任何管理命令。
- 不能停用自己，不能停用最后一名启用超级管理员。
- 停用立即使旧 JWT 失效并清理未完成挑战，但保留角色/MFA/恢复码/授权/任务/审计。
- 恢复启用后旧 JWT 仍失效；新登录成功。
- READ/EXPORT 角色替换、无变化幂等、非法角色、超级升降级均正确。
- 移除角色按确认规则撤销对应授权；重新加角色不自动恢复旧授权。
- 目标会话撤销只推进安全版本，不改变其他状态。
- 接受/撤销邀请并发只有一个终态。
- 成功审计失败会使业务回滚；业务失败不会留下虚假的 SUCCESS。
- 相同幂等键同请求重放；不同请求返回冲突。
- 旧 JWT 下一请求因 enabled/roles/security_version 变化被拒绝。

### 13.2 前端

- 管理员页改用 `/api/platform/admins`，授权页继续使用候选接口或后续安全的共享 DTO，不再反向复用为目录。
- 筛选只在点击查询时请求；分页保持筛选。
- 当前账号、最后超级管理员、普通管理员和停用管理员按钮规则正确。
- READ/EXPORT 三种普通邀请组合正确，写删/超级混合组合不可提交。
- 各危险操作弹窗影响说明、密码、TOTP、原因和确认项齐全。
- 401 再验证失败不误清登录；主 JWT 失效才退出。
- 409 并发提示、空状态、重试、窄窗口、中英文均通过。
- 前端定向测试、全量测试、TypeScript 和生产构建通过。

### 13.3 人工验收

先使用测试事务/隔离数据完成自动验收。任何对当前两名真实本地超级管理员的停用、启用、MFA 重置或会话撤销，必须在执行前再次取得用户明确批准。

推荐人工路径：

1. 两名超级管理员分别登录并查看相同目录。
2. 验证不能自停用、自重置 MFA 或通过目标接口撤销自己的会话。
3. 邀请普通 READ 管理员，确认激活后零租户授权。
4. 增加 EXPORT 角色，确认旧 JWT 失效且仍无具体授权时不能访问租户数据。
5. 创建精确授权后验证 READ/EXPORT 分离。
6. 移除角色并确认授权按冻结规则失效/撤销。
7. 停用普通管理员，确认旧会话失效、数据未删除；恢复时检查授权提示。
8. 独立撤销目标会话，确认账号仍启用、MFA 和角色不变。

## 14. 与第一阶段的依赖

第一阶段正在或可能修改：

- `frontend/src/platform/PlatformApp.tsx`
- `frontend/src/platform/api.ts`
- 平台公共样式
- 租户目录查询接口、DTO 和测试

第二阶段实施前必须：

1. 等第一阶段形成明确合并基线。
2. 在独立 worktree/`codex/` 分支同步最新基线。
3. 重新检查迁移目录，替换 `NEXT_AVAILABLE`。
4. 以第一阶段最新 `PlatformApp.tsx`、`api.ts` 和样式为准做最小合并，不覆盖租户管理入口和权限逻辑。
5. 管理员目录后端独立于租户目录和授权候选接口。

## 15. 实施拆分、合并顺序与回滚

### 15.1 推荐实施批次

1. 数据库 expand 迁移、迁移契约测试、稳定错误键。
2. 独立管理员目录/详情与批量查询测试。
3. 通用管理员挑战、幂等命令和事务审计基础。
4. 账号启停。
5. READ/EXPORT 角色变更和目标会话撤销。
6. 普通管理员邀请及激活兼容。
7. 管理员页面重构、i18n、响应式布局。
8. 并发/集成/前端全量测试和人工验收清单。

每一批独立提交；公共前端文件最后基于第一阶段基线合并。

### 15.2 回滚原则

- 数据库首个迁移采用 additive expand，保留旧邀请 `role_code`，使回滚应用版本有兼容空间。
- 不通过 down migration 删除新角色快照、审计或管理员历史；数据库问题优先 forward-fix。
- 若普通邀请出现问题，关闭/隐藏普通邀请入口并保留已创建记录；不得删除已激活账号。
- 启停/角色/会话操作属于已审计真实安全变更，不能靠部署回滚自动逆转；需要按审计记录执行新的受控补偿操作。
- 回滚前仍需保证至少一名启用超级管理员，且不得恢复任何旧 JWT。

## 16. 待用户确认（设计冻结门槛）

以下均未擅自视为最终决定。括号内为本草案推荐：

1. **已由 D-01 取代**：不把 READ/EXPORT 组合作为管理员类型；普通邀请等待岗位权限矩阵。
2. **已由 D-01 取代**：本阶段不提供账号 READ/EXPORT 角色修改功能。
3. 是否禁止普通管理员升为超级管理员、禁止超级管理员降级？（推荐：均禁止）
4. 停用时未过期授权如何处理？（推荐：保留记录，因账号停用失效；恢复时强提醒复查）
5. 是否增加独立“撤销目标全部会话”？（推荐：增加）
6. 是否增加 MFA 临时锁定手工解锁？（推荐：不增加，15 分钟自动到期）
7. 管理员目录可见范围？（推荐：仅超级管理员）
8. 超级管理员最多两名规则是否保持？（推荐：保持）
9. 是否允许删除平台管理员？（推荐：不允许，只停用）
10. 是否允许编辑管理员邮箱？（推荐：首版不允许）
11. **已由 D-01 取代**：本阶段不实施 READ/EXPORT 角色修改，因此不触发角色移除后的批量授权处理。
12. 管理员目录首版是否直接上分页、搜索和筛选？（推荐：是，默认 20、最大 100）
13. “最多两名超级管理员”的精确定义：只计算启用账号+有效邀请，还是连已停用但仍持有超级角色的账号也计入？（推荐：按当前既有规则计算“启用账号+有效邀请”，恢复启用时重新校验上限；需明确接受可能存在停用超级账号的结果）
14. **已由 D-01 取代**：普通邀请暂缓；现有超级管理员邀请路径和语义保持不变。

完成以上确认后，本文状态才能改为“设计冻结”。在此之前不得开始代码、迁移或数据库实施。

## 17. 2026-08-22 实施任务拆分与第一步准备

> 本节只做实施准备；尚未获得设计冻结和开工确认，不代表已经进入代码实施。

### 17.1 当前执行基线

- 当前分支：`2-1G`，HEAD `a5f695e`。
- 当前平台、多租户和 `V4_50`–`V4_67` 文件仍属于未提交工作区内容。
- 第一阶段已实际修改 `PlatformApp.tsx`、`api.ts`、租户页面和租户目录后端；当前租户页面行为已经改为“进入页面加载目录”，第二阶段不得按较早交接稿覆盖这一新基线。
- 当前最新迁移仍为 `V4_67`；第一步候选编号为 `V4_68`，但创建文件前必须再次检查占号。
- 由于关键平台代码尚未进入当前 HEAD，从 HEAD 新建独立 worktree 会缺少这些未提交文件。安全开工仍需先明确第一阶段基线如何冻结；不得擅自提交、搬运或覆盖现有大量用户改动。

### 17.2 总体拆分

| 任务 | 目标 | 主要依赖 | 独立验收结果 |
|---|---|---|---|
| T0 设计与基线冻结 | 确认第 16 节决定、冻结第一阶段基线、确定分支/worktree | 用户确认、第一阶段状态 | 无业务代码变化；形成可实施基线 |
| T1 管理员目录后端 | 正式分页目录/详情、筛选、批量角色和授权统计、目录审计 | T0 | 只读 API 可独立上线，不改变账号 |
| T2 管理员目录前端 | 管理员页切换到正式目录，完成筛选/分页/详情骨架 | T1、第一阶段公共前端基线 | 可视化目录闭环，无危险操作 |
| T3 高风险命令底座与启停 | 扩展挑战绑定、幂等命令、启停、最后超级管理员保护 | T1 | 单账号启停可独立验收 |
| T4 目标会话与 MFA 整合 | 目标会话撤销、现有受控 MFA 重置整合 | T3 | 两类安全操作含义独立、可审计 |
| T5 平台岗位权限设计 | 冻结运营管理员、安全审计员等岗位的模块权限矩阵 | T3、后续平台模块范围 | 只形成岗位模型；未确认前不开放普通邀请 |
| T6 管理员安全操作前端 | 启停、会话、MFA 和现有超级管理员邀请交互 | T3–T5 | 不出现 READ/EXPORT 管理员类型选择 |
| T7 全量验证与交接 | 迁移链、并发、安全、前端全量、人工验收和回滚清单 | T1–T6 | 发布门禁证据齐全 |

任务顺序不把 T3–T6 合并成一个大改动。T1/T2 先交付纯只读目录；真实账号状态变化必须到 T3 后再单独确认人工验收。

### 17.3 每个任务的精确边界

#### T0：设计与基线冻结

- 确认第 16 节全部产品决定，重点确认角色移除时授权处理、超级管理员人数口径和邀请路径兼容。
- 确认第一阶段是否已经完成，公共文件是否仍会继续修改。
- 不替用户提交当前脏工作树；由用户明确基线后再建立 `codex/platform-admin-phase2` 分支/worktree。
- 再次扫描迁移编号和同名设计/实现文件。

#### T1：管理员目录后端（建议作为第一个编码步骤）

范围：

- `GET /api/platform/admins`
- `GET /api/platform/admins/{targetUserId}`
- 默认 20、最大 100；关键词、enabled、role、mfaStatus 筛选。
- 当前页批量角色聚合和有效授权数量统计，禁止 N+1。
- `ADMIN_DIRECTORY_READ` 审计和稳定 `PLATFORM_ADMIN_NOT_FOUND` 错误。

不包含：前端公共文件、启停、角色写入、邀请修改、MFA/会话操作、真实账号或数据库数据变更。

候选文件（最终以基线为准）：

```text
src/main/resources/db/migration/V4_68__Platform_Admin_Directory.sql
src/main/java/com/wms/system/platform/controller/PlatformAdminController.java
src/main/java/com/wms/system/platform/dto/PlatformAdminSummaryResponse.java
src/main/java/com/wms/system/platform/dto/PlatformAdminDetailResponse.java
src/main/java/com/wms/system/platform/repository/PlatformAdminDirectoryRepository.java
src/main/java/com/wms/system/platform/service/PlatformAdminDirectoryService.java
src/main/java/com/wms/system/platform/repository/PlatformUserRoleRepository.java
src/main/java/com/wms/system/platform/repository/PlatformAccessGrantRepository.java
src/main/java/com/wms/system/exception/ErrorKeys.java
src/main/java/com/wms/system/controller/GlobalExceptionHandler.java
src/test/java/com/wms/system/platform/**/PlatformAdminDirectory*Test.java
src/test/java/com/wms/system/migration/PlatformAdminDirectoryMigrationContractTest.java
```

`V4_68` 只允许：扩展审计动作 CHECK 和增加经过查询计划验证的目录索引；不增加普通邀请、命令表或账号状态迁移，不执行账号 UPDATE。

T1 验收门槛：

1. 非超级管理员不能读取目录或详情。
2. 返回字段不包含密码、MFA 密钥/密文、恢复码、JWT、挑战或邀请令牌。
3. 分页、关键词、enabled、role、mfaStatus 组合查询正确。
4. 角色和授权统计采用批量查询，无逐账号 Repository 调用。
5. 每次目录分页请求只写一条 `ADMIN_DIRECTORY_READ` 汇总审计。
6. 目标不存在返回稳定 404，不落为通用 500。
7. 定向测试、迁移契约测试通过；不启动会占用共享端口的服务。

#### T2：管理员目录前端

- 修改 `PlatformAdminManagementPage.tsx`，只先交付目录和详情只读骨架。
- 修改当前最新的 `api.ts`，不得回退第一阶段租户筛选、路由或错误处理。
- 现有超级管理员邀请区暂时保持功能，待 T6 再重构。
- 前端定向、全量测试、TypeScript 和生产构建通过。

#### T3：高风险命令底座与启停

- 候选 `V4_69`：挑战上下文/目标版本、幂等命令、目标审计字段和启停审计动作。
- 实现 status-change challenge、disable、enable。
- 统一锁顺序，验证最后超级管理员并发保护和 `security_version` 原子推进。
- 只做自动化测试；真实账号启停仍需新的明确批准。

#### T4：目标会话与 MFA 整合

- 实现 sessions revoke challenge/final。
- 把现有受控 MFA 重置放入管理员详情安全操作区，但不改变其安全语义。
- 会话撤销不改变账号、岗位、MFA、授权或任务。
- 本任务不实现 READ/EXPORT 角色修改。

#### T5：平台岗位权限设计

- 明确平台超级管理员、平台运营管理员和安全审计员的模块权限。
- 平台岗位控制平台模块访问；具体租户数据访问继续走委派授权。
- 形成岗位新增、变更、邀请和审计的后续详细方案。
- 岗位矩阵未确认前，不实现普通平台管理员邀请。

#### T6：管理员安全操作前端

- 整合目录、现有超级邀请和管理员详情安全操作区。
- 每个危险操作使用独立名称、影响说明和弹窗。
- 当前账号、最后超级管理员、停用账号和普通管理员按钮规则完整。
- 不显示 READ/EXPORT 管理员类型选择或修改入口。
- 响应式、中英文、错误和并发冲突提示完整。

#### T7：全量验证与交接

- Flyway 全链和迁移契约。
- 后端单元、Repository、Controller、并发集成测试。
- 前端定向/全量、TypeScript、生产构建。
- 敏感字段日志/审计排除检查。
- 更新本设计状态、实施交接和人工验收步骤；不自动执行真实账号高风险操作。

### 17.4 第一步开工门槛

T1 开始前必须同时满足：

1. 用户确认第 16 节按推荐方案冻结，或逐项给出修改。
2. 用户确认第一阶段已形成可用基线，并明确允许在当前工作树实施，或先提供可用于新 worktree 的已提交基线。
3. 再次确认迁移目录没有新于 `V4_67` 的文件。
4. 第一阶段不再同时修改 T1 候选后端文件；T1 不触碰 `PlatformApp.tsx`、`api.ts` 或租户页面。

满足后，第一步只实施 T1；完成测试和差异审查后停止，与用户确认是否进入 T2/T3。
