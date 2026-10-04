# P3-M1 实施与交付记录

## 授权及停止点

2026-09-09 用户确认 P3-M1 及 Webhook 超限处理原则，授权代码、增量迁移、专用隔离数据库验证和测试。

- A：基础模型、租户会话失效、统一状态检查、隔离测试。
- B：紧急安全暂停、双人恢复、必要集成联动、页面闭环。
- 本批完成点：隔离验收通过、代码可交付。真实数据库迁移、真实租户操作、现有服务重启和生产开放均未授权。
- 计划暂停、关闭/关闭恢复、租户清理、备份退出、网址回收、计费、通用任务中心、完整集成中心及 Phase 5 延期；历史已确认规则不取消。

## 实施前兼容核对

- 当前工作区：`2-1G@9c12f6f`。已有 `docs/CODEX_HANDOFF.md` 修改及多个未跟踪设计/验收文档、outputs，均保留。
- 最新可见 ERP 分支：`codex/tenant-erp-a1@ea4697e`；相关工作区存在 A2 采购、盘点、异常处理等未提交修复，不修改该工作区。
- JWT、刷新、角色切换后端和交接消费服务在两个提交基线间无差异。
- ERP 已增加 AuthProvider 会话范围切换回调、多路径权限匹配、开户库存权限初始化、InventoryDispositionExpiryScheduler。共享防护须兼容这些能力。
- ERP 未提交开户修复：创建 provisioning job 前 `signupRepository.saveAndFlush(locked)`，不能回退为仅 save。
- ERP 已占用 V4_73 至 V4_84。本批增量迁移使用 V4_85–V4_88，交付前已再次核对；不重编号已有迁移。
- 不整体合并 ERP 业务分支，不覆盖其他工作区；必要公共修复采用可追溯的最小兼容补丁。后续记录实际兼容检查结果。

## 状态

- A：隔离验收通过。覆盖规则、会话、RLS/事务、开户登录交接和旧工作版本拒绝。
- B：隔离验收通过。紧急安全暂停、双人安全恢复、连接证据/凭据处理、可信暂存、告警和页面闭环已实现。
- **本批达到“隔离验收通过、代码可交付”的停止点；不是部署或生产开放。** 所有安全操作入口在非专用验证配置下保持关闭。
- 验收证据、已知边界及复验方法见下文，不把模拟第三方结果当作真实店铺验证。

## 本批实现契约

### A：基础模型与执行边界

1. 保留已有六种租户状态；本批业务执行只接受 ACTIVE。受限登录/公司访问状态、平台受控读取、可信 Webhook 接收分别使用独立用途，不把“暂停”解释为平台人员完全不能读取。
2. `tenants.session_version` 与原乐观锁 `version` 分离。安全暂停、批准安全恢复各递增一次租户会话版本；JWT 与开户交接码绑定此版本。旧 JWT 无版本字段仅按初始版本 1 兼容，第一次撤销后即永久失效。
3. 一次请求/后台工作固定一个租户及会话版本；嵌套调用不能采用新版本，使恢复后的旧在途工作重新获得执行资格。租户 ERP 原有用户级撤销、24 小时令牌、7 天绝对登录窗口、角色切换和刷新规则继续沿用。
4. 请求入口、JPA/RLS 事务入口、业务响应输出前、第三方调用前后共用权威状态。业务写事务持有租户共享行锁；暂停/恢复使用排他行锁，因此已准入的原子业务单元与暂停提交有确定先后，不会在成功暂停后提交未经准入的新业务单元。
5. “立即暂停”以暂停事务成功提交为生效边界，并非强行杀死 SQL 或撤回已经发到第三方的网络请求。已发出的远端请求无法从本地收回；晚到结果必须通过状态/版本复查才能进入后续业务。数据库争用失败只返回未完成，不能假报暂停成功。
6. 前端保留 ERP 的会话切换缓存清理回调，并补入租户 ID、租户会话版本及用户安全版本作为缓存分区；同名所有者切换不同租户也会清缓存，正常同版本刷新不清缓存。JWT 在此只作为缓存分区提示，绝不充当前端授权依据。

### B：安全操作、恢复和证据

- 紧急安全暂停：一名已启用、已开启 MFA 的平台超级管理员，提供原因、当前密码和 TOTP，立即执行 ACTIVE → SUSPENDED；租户端 TENANT_ADMIN 无此权限。
- 安全恢复：同类管理员提交申请，由另一名平台超级管理员批准或驳回。PENDING 申请长期有效；发起人仅在审批决定前可撤回，审批后任何人都不能撤销原申请。
- 审批 APPLIED、SUSPENDED → ACTIVE、会话版本递增、可恢复连接状态、成功审计、幂等结果在一个数据库事务提交。审计失败会整体回滚。
- 五分钟操作挑战绑定操作载荷、操作者/租户及各自版本；错误密码/TOTP 最多五次，失败计数持久化并锁定 15 分钟。相同命令编号可重试/查询；相同编号不能替换操作内容。
- 原因、当前状态、连接结果、数据补齐结果、告警问题状态与邮件投递状态分别记录。没有计划暂停、关闭/关闭恢复或最终清理入口。

### 现有集成的最小联动

1. 暂停事务使该租户所有业务连接本地停用、清除当前进程调用令牌缓存，保留原启用状态。范围未知按全部连接存在风险；明确与连接无关可按未受影响处理，但仍不能在暂停期间同步业务。
2. 疑似泄露的旧签名不视为可信 Webhook。凭据更换保存、远端撤权/更换核验、连接检查、数据补齐是分开的结果。原危险密钥指纹未变化，不能标记连接检查通过。
3. 本批提供**人工证据登记**和本地加密凭据更新，不声称自动调用第三方撤权 API 或自动完成所有远端检查。操作人员须实际核验第三方后台/连接情况，登记证据；数据库中以 `MANUAL_EVIDENCE` 标明来源。没有对真实店铺发起验证请求。
4. 仅恢复原本启用且通过检查的连接；失败连接保持 REAUTH_REQUIRED/停用，其他获准的 ERP 功能可恢复。CHECK_PASSED 仅表示检查通过，租户仍暂停时不运行。
5. 暂停前及暂停期间的待处理推送在恢复后进入核对，不盲目重放。使用既有 Shopify 对账/补单能力，记录覆盖暂停期间、恢复后生成且无缺失订单的报告；还必须登记订单修改、取消、退款等人工核验结果，才标记数据核对完成。报告数量一致本身不等于全面补齐。
6. 渠道原始报文接收独立留底，但成功/跳过等处理标记加入业务事务；业务回滚不能留下“已成功处理”的去重标记。重试复用原接收记录，避免唯一键冲突导致永久无法重试。
7. 没有通用任务中心或完整集成中心。仅有本功能的持久化 Webhook 接收/消费、连接证据和异常邮件处理。

### Webhook 可信暂存与超限

- 验签、路由与风险检查通过后，先可靠提交接收记录才返回 200；重复同一事件且内容相同返回 200，但不延长七天期限。相同事件编号内容冲突返回 409。
- 配置容量/速率超限返回 429；无法可靠保存返回 503；超大请求返回 413。超大请求未完成验签时的告警明确标为“未核验入口拒绝”，不伪装为可信事件。
- 默认技术参数：单请求 1 MiB；每租户暂存 10,000 条/100 MiB；全局正文 1 GiB；每租户每分钟 300 次、全局每分钟 3,000 次。均可配置并有参数合法性验证；这是技术限额，不是套餐额度，不会自动收费。
- 保留期从首次成功接收计七天。专用消费者只处理 ACTIVE 租户；失败有限重试后进入人工核对。正常业务原子单元、在途任务均使用同一租户版本边界。
- 七天到期清除正文及与此接收记录关联的渠道诊断正文副本；业务单据不属于暂存正文。最小接收哈希/结果元数据保留去重和审计用途。法律保留标记在领取及删除时复查；本批没有开放完整法律保留管理流程。
- 七天是处理器执行的到期边界，不承诺机器停机期间仍能执行删除；计划任务故障须恢复后补做清理，并检查剩余正文。有效法律保留是已确认的删除阻断例外。
- 对已核验补齐的历史接收记录，仅登记“由核对覆盖”，不假称逐条处理；后续正常正文到期也不重新制造未补齐告警。
- 不承诺绝对零丢失。第三方重试耗尽、容量不足、数据库不可用等均需按证据核对缺口。

### 平台受控读取、导出与告警

- 暂停租户业务导出被统一状态检查阻止。平台端沿用 Phase 2 当前角色及有效授权，新增暂停期间必填访问原因（`X-Platform-Access-Reason`，前端 URI 编码传输），审计包含租户状态、暂停类别及原因。
- 平台导出在创建、实际执行/分页、完成前及下载时复查相应状态/权限；一次只允许已有首版数据集。重复消费者不能重复领取同一任务；失败后清理本次产生的不完整文件，不能删除已有他人文件。
- 告警接收者为指定且仍有效的平台超级管理员，至少一人；控制开关开启前须具备有效接收者及独立凭据加密配置。必要审计失败与邮件失败分开，邮件失败不撤销已合法执行的暂停/恢复。
- 首次异常登记并安排邮件；同问题合并，未解决每六小时提醒，风险加重立即再次安排。失败以首次失败时间为基准在第 5、15、60 分钟重试；再失败显示 MANUAL_REQUIRED。
- SUBMITTED 只表示邮件发送器受理，不表示送达或已读；真实问题核验解决后停止提醒。不会向租户发送生命周期主动通知。

## 文件与迁移

| 交付点 | 主要文件/结构 |
|---|---|
| A | `tenant/security/TenantExecutionScope`、`TenantSecurityStateService`、`TenantAccessPolicy`；`CompanyRlsJpaTransactionManager`；`TenantSecurityAdmissionFilter`、`TenantBusinessResponseGuard`、`CompanyAccessController` |
| A 会话接入 | `JwtUtil`/`TenantJwtClaims`/`JwtAuthenticationFilter`、`AuthController`、`SessionHandoffService`/`TenantProvisioningService`、`ActiveCompanyTaskRunner`；前端 AuthProvider、TenantAccessBoundary、sessionCacheScope |
| B | `PlatformTenantSecurityService`/`Controller`、`TenantSecurityCommand`、`TenantConnectionSafetyService`、凭据加密与转换器、`TenantSecurityAlertService`、`TenantWebhookInboxService`/`Worker`/`ReceiptScope` |
| B 既有调用接入 | Shopify webhook/controller、TokenProvider、ApiClient、ChannelRawEventService/Repository；平台数据服务/导出服务/消费者；前端 TenantSecurityPanel、TenantDetail、TenantDatasetBrowser、platform/api |
| V4_85 | 租户会话版本/安全暂停字段、交接码版本、统一 PostgreSQL 状态/版本检查函数 |
| V4_86 | 七张专用表：安全申请、挑战、命令结果、连接状态、Webhook 接收、接收限额计数、异常告警；凭据字段改 TEXT、平台导出原因 |
| V4_87 | 危险凭据原指纹、最近安全恢复时间 |
| V4_88 | 暂存副本来源、过期正文防复活触发器、补齐报告/证据、告警等级与解决证据 |

Java 文件路径前缀：`src/main/java/com/wms/system/`。迁移位于 `src/main/resources/db/migration/`。

接口均为本功能专用，不开放延期操作：

- GET `/api/company-access/status`
- GET `/api/platform/tenants/{tenantId}/security`
- POST `/api/platform/tenants/{tenantId}/security/challenges`
- POST `/api/platform/tenants/{tenantId}/security/commands`
- GET `/api/platform/tenants/{tenantId}/security/commands/{key}`（仅命令本人查询自己的已提交结果）
- 沿用 POST `/api/webhooks/shopify` 与原平台读取/导出接口。

## 专用隔离环境与复验约束

- 新建本批 PostgreSQL 数据目录：`target/p3-m1-pg-20260909`；只监听 `127.0.0.1:55439`。
- `p3_m1_foundation` 验证当前工作区；`p3_m1_erp` 验证组合代码与 ERP V4_73–V4_84 + P3 V4_85–V4_88 的完整顺序。
- 运行角色 `p3_m1_app` 为 NOSUPERUSER/NOBYPASSRLS；测试不加载普通 application-test.yml，不连接 localhost:5432 或任何真实租户库。
- `p3-m1-smoke.properties` 独立加载，全部 JWT/MFA/凭据密钥为专用虚拟测试值；删除测试上下文的全部计划任务处理器，外部邮件使用模拟发送器，远端 API 单元测试使用模拟客户端。没有启动真实业务 HTTP 服务。
- P3 数据库测试带固定地址/库名前缀白名单，无 `p3.test.jdbcUrl` 参数时跳过；后续复验必须先明确启动同一专用测试实例，不能复制普通 test profile 的库地址。
- Maven 使用 `-Dwms.build.directory=.../target/p3-m1-build`，不触碰现有 `target/classes`，避免 DevTools 监听输出。兼容构建输出 `target/p3-m1-erp-build`。
- 前端构建输出 `target/p3-m1-frontend-dist`，没有覆盖常用 dist。
- 本机验证 PostgreSQL 为 **18.6**。当前 Flyway 9.22.3 提示高于其已测试版本；实际迁移与 ORM validate 已通过，但不是生产目标版本/云环境验证，部署前仍需针对真实目标版本复验。
- 验证结束后已用确切数据目录和 55439 端口核对身份，再停止本次专用 PostgreSQL 实例。测试数据、构建产物及兼容副本保留在 target 内供追溯；未停止其他数据库或现有业务服务。

## 隔离验收证据（2026-09-09）

| 验证 | 实际结果 |
|---|---|
| 当前工作区后端最终回归 | **236 项通过，0 失败/错误/跳过**；涵盖 Phase 1/2 平台、权限、MFA、租户基础纯单元测试及 P3 专用数据库测试 |
| P3 四组专用测试 | 规则/基础 11 项、真实 PostgreSQL/RLS/锁 4 项、安全命令 6 项、完整 Spring + MockMvc + JPA 14 项，已包含在 236 项内，不能重复加总 |
| ERP 组合回归 | **111 项通过，0 失败/错误/跳过**；874 个主源码、250 个测试源码编译通过；A1 + 已有 Java 修复 + P3，不是 ERP 全量业务 UAT |
| 数据库迁移 | 当前库 B4_71、V4_72、V4_85–88 全成功；ERP 组合库 B4_71、V4_72–88 全成功；两库均 ORM validate 通过 |
| 前端最终全量回归 | **60 个文件、215 项通过，0 失败**；包含新增页面完整认证/同编号重试测试，机器可读报告为 `target/p3-m1-frontend-tests.json` |
| 前端静态与构建 | `pnpm exec tsc --noEmit` 返回 0；`vite build --outDir ../target/p3-m1-frontend-dist` 成功 |
| 补丁检查 | `git diff --check` 无空白错误；现有 CRLF 提示不涉及业务差异 |

后端可读及 XML 报告位于 `target/p3-m1-build/surefire-reports`、`target/p3-m1-erp-build/surefire-reports`。历史较早测试计数（例如 108/110）已被最终含导出用例的回归替代，不累加为新测试。

关键验收已覆盖：

- A：六状态用途矩阵、暂停/恢复均撤销租户会话、旧 JWT/刷新/角色切换/交接码不得复活、嵌套工作不能借新版本继续执行、跨租户 RLS、写事务与暂停行锁并发顺序。
- B：真实 HTTP 挑战/密码/TOTP/执行/查询结果、禁止自己审批、请求长期有效、待审批撤回及终态不可更改、并发只生效一次、幂等载荷不能变更、必要审计失败整体回滚。
- 集成：旧风险凭据阻断、本地加密读写、失败连接停用、凭据/远端人工核验/补齐结果分离、必须有当前报告证据才可确认补齐；第三方晚到响应须再次通过状态检查。
- Webhook：暂停可信持久化后才 ACK、限额/速率拒绝不增加正文记录、重复不续期、冲突拒绝、恢复不盲目重放、七天正文与诊断副本清除、法律保留阻断、旧副本不能回写复活；业务回滚不能留下成功去重标记。
- 平台读取与导出：暂停业务用途被拒绝，受控平台读取须原因；真实 ZIP 生成、重复消费者不重跑、下载再次验证原因、管理员被禁用后新导出任务失败；审计中的百分号等原因文本不二次解码。
- 告警：虚拟邮件失败按 5/15/60 分钟安排，之后转人工；风险加重立即重新安排，成功受理只标 SUBMITTED，未解决六小时提醒。
- 页面：默认关闭按钮、申请人撤回/另一人审批、失败连接真实显示、MFA 提交及未知结果同编号重试、暂停访问边界、跨租户及会话版本缓存清除。

测试边界：未执行真实浏览器联调到生产服务，未验证真实邮件送达、真实 Shopify 撤权/重授权或生产负载。容量是可配置保护参数，测试证明越界拒绝与数据一致性，不构成吞吐或绝对零丢失保证。全量前端采用两个并发 worker；早期高并发运行的既有页面超时已在限制并发后复验通过，没有放宽业务断言。

### 安全复验示例

先明确启动本次保留的专用测试实例、核验端口 55439 和 `p3_m1_app` 角色；以下命令本身**不会**启动数据库，也不能改成真实库地址。禁止用普通 `application-test.yml` 或不筛选的 `mvn test` 代替。

在当前工作区运行核心安全包（109 项；236 项还包括另外筛选的 Phase 1/2 纯单元回归）：

```powershell
& 'target/codex-tools/apache-maven-3.9.9/bin/mvn.cmd' -B -ntp `
  '-Dwms.build.directory=D:/ERP_WMS/BC warehouse/2G/target/p3-m1-build' `
  '-Dtest=TenantSecurity*Test,JwtUtilTest,JwtAuthenticationFilterTest,TenantSessionSecurityControllerTest,ShopifyTokenProviderTest,ShopifyApiClientTest,ShopifyWebhookServiceTest,ActiveCompanyTaskRunnerTest,CompanyScopeTest,TenantLifecycleServiceTest,PlatformCompanyDataServiceDirectoryTest,DynamicAuthorizationManagerTest,IntegrationSchedulerTest' `
  '-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55439/p3_m1_foundation' test
```

ERP 组合副本须用绝对 Maven 路径、`target/p3-m1-erp-build` 和数据库 `p3_m1_erp`，不要把组合编译输出写回 ERP 原工作区的 target/classes。

前端在 `frontend` 目录执行：

```powershell
pnpm exec tsc --noEmit
pnpm exec vitest run --maxWorkers=2 --reporter=default --reporter=json --outputFile=../target/p3-m1-frontend-tests.json
pnpm exec vite build --outDir ../target/p3-m1-frontend-dist
```

## 合并说明与停止边界

### 后续范围补充：恢复核对尚未形成最终业务确认

当前恢复核对交付为“系统缺单报告＋人工核验订单修改、取消和退款”，**不是完整自动核对**。本文中的实现与隔离验收记录，不代表用户接受人工核验为最终方案。最终边界仍待另行确认。后续平台会话后端收尾不包含完整自动核对，也不以确认完整第三阶段为开工前提。会话收尾的独立记录见 `PLATFORM_SESSION_BACKEND_DELIVERY_2026-09-09.md`；隔离通过不等于生产验收或上线授权。

- 当前交付代码留在本工作区 `2-1G`；未提交、未推送、未整体切换/合并 ERP 分支，保护已有脏工作区。
- 独立兼容副本：`target/p3-m1-erp-compat`，基于 `codex/tenant-erp-a1@ea4697e`。应用路由保留 ERP 新页面；权限匹配保留分号分隔多路径；开户同时保留库存权限初始化与 saveAndFlush 修复。
- 实质重叠：ERP 的 Shopify 取消逻辑已使用商业/仓库状态，退款已进入 RMA 渠道案例。本批兼容副本保留 ERP 这些业务处理，仅叠加 P3 可信接收、状态约束消费者与事务完成标记；**不得整文件以当前旧 ERP 业务版本覆盖最新 ERP**。
- 隔离兼容副本不是对 ERP 原工作区的修改。下一次正式合并仍需保留同期 A2 未提交修改，不得使用 reset/checkout 覆盖。
- 组合副本额外包含 ERP 原工作区 15 个有内容变化的既有 Java 源码/测试文件（控制器异常映射、出库状态、销售提交、盘点、采购收货/库存处置等）；开户修复另已与 P3 最小合并。`CustomerRepository` 的工作区提示仅换行层面无内容 diff。没有覆盖或声称验收 ERP 未提交的前端扫描/摄像头变更及其新 UAT 文件。
- 独立环境中的验证完成，不等于生产按钮已开放。默认 `wms.tenant-security.controls-enabled=false`；现有 SaaS 域名启用安全锁未解除。
- 真实迁移、真实租户操作、现有服务重启、生产配置和开放均须另行确认。尤其需确认生产加密密钥/保管、有效告警接收者、邮件受理/失败证据、目标数据库版本和外部连接人工处置流程。
- 原始凭据旧格式在未开启控制时仍兼容；本批不自动全库重写历史密钥，在该租户暂停事务及后续保存时加密。
- 全部关闭/关闭恢复、自动租户清理、备份退出、网址回收、计费与 Phase 5 设计继续保留，不被本批实施替代。旧清理执行器另有硬性未开放门闸，不能仅改配置误启动。
