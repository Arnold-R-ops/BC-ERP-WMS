# r8 平台后端隔离验收交付

执行日期：2026-09-17。**本批最终结果 FAILED；迁移通过，独立及顺序阶段通过，完整回归中止；专用集群正常关闭已证实。**

不是发布候选版本，不可据此上线。未自动修复、重跑或创建下一轮；本文及补充只读复核不改写原始结果。

## 1. 固定范围与输入

- 根目录：`D:\ERP_WMS\BC warehouse\2G\target\platform-db-acceptance-20260916\r8`。沿用批准的目录日期，实际执行日为 09-17。
- 数据目录：根目录下 `pgdata`；源码 `snapshot/source`；依赖 `snapshot/maven-repository`；新产物 `build/a1`。
- 仅 `127.0.0.1:55449`；主库 `p3_m1_platform_release_r8`、增量库 `p3_m1_platform_upgrade_r8`。
- 专用角色 `p3_r8_initializer`、`p3_r8_migrator`、`p3_r8_app`；维护库 postgres 仅用于初始化/身份/双库准备。
- 新空集群、公共参考数据和登记的合成夹具，随机凭据、SCRAM；没有复制真实数据、旧验收账号或使用 trust。
- Node 24.12.0、Java 17.0.17、Maven 3.9.9、Surefire 3.1.2、PostgreSQL 18.6。固定离线依赖；不修改全局 PATH、公共 POM、依赖锁文件或生产配置。
- 分支 `2-1G`；HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；没有提交、合并、推送。
- 初始化、启动、SQL、迁移、业务测试及关闭均通过获批沙箱外执行器执行，并在初始化之前及后续身份核验中实际确认：`ARNOLD\Arnold`，SID `S-1-5-21-3770167052-703495069-1631994977-1001`，restrictedToken=false、administratorEnabled=true、elevated=true、elevationType=1。无 RunAs、UAC/系统权限/安全策略修改。此前文件封存、编译和无数据库检查在专用准备执行进程完成。
- 没有启动前端或 HTTP 服务，不涉及 ERP 功能开发、真实账号、真实租户、真实数据库或现有服务变更。

本次只调整以下 4 个测试适配文件的 r8 目录、角色、数据库绑定，并补一项拒绝旧 r7 目标的参数用例：

- `src/test/java/com/wms/platformverification/PlatformR2Isolation.java`
- `src/test/java/com/wms/platformverification/PlatformR2IsolationTest.java`
- `src/test/java/com/wms/platformverification/PlatformR2SpringInitializer.java`
- `src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java`

新增 6 个专用工具（均在 `src/test/scripts/`）：`platform-r8-plan.mjs`、`platform-r8-backend-snapshot.mjs`、`platform-r8-database-batch.mjs`、`platform-r8-binding.test.mjs`、`platform-r8-environment.mjs`、`platform-r8-environment.test.mjs`。

新入口复用 s2 已验证的身份查询、父链、诊断、计时与收尾机制；没有仅把旧 r7 启动入口改名。公共工具及历史入口未改写。

965 个源码/资源/测试文件、3337 个依赖文件、90 个 Maven 分发文件均封存。8 个业务套件的当前 63 个方法正文指纹不变；**保留已披露的 7 处历史方法正文修订，不声称原始 63 个方法自始未改。** 产品逻辑、迁移及产品安全约束未修改。

## 2. 预先固定的预算

预算在源码封存、编译及数据库执行之前写入专用计划并纳入输入指纹；运行中没有加时。

| 命令/阶段 | 预算 |
| --- | ---: |
| 源码编译 / Java 无数据库门禁（分别） | 300 秒 |
| 每个 Node 无数据库套件 | 120 秒 |
| 语法 / 版本查询（分别） | 15 秒 |
| 每条迁移准备器 Java 命令 | 180 秒 |
| 常规受控 SQL 命令 / statement_timeout | 30 秒 / 15 秒 |
| Maven effective-pom / 每个探针或业务套件调用（分别） | 240 秒 |
| Surefire fork / exit | 120 秒 / 20 秒 |
| initdb | 60 秒 |
| 启动阶段 / pg_ctl 命令 | 37 秒 / 35 秒（内部等待 30 秒） |
| 就绪阶段 | 40 秒 |
| 独立收尾 / 正常停止命令 | 60 秒 / 32 秒（内部等待 30 秒） |
| 全环境查询 / 单 PID 查询 / 就绪 SQL | 15 秒 / 5 秒 / 4 秒 |

实际：编译 30.209 秒；启动阶段 0.488 秒；就绪 26.524 秒；独立收尾 35.957 秒；正常停止命令 0.152 秒。4 条迁移准备器命令分别 2.494、2.301、1.313、1.358 秒。数据库控制与查询共 388 条记录，无超时或未确认终止。业务 Maven 调用的非零退出随失败用例如实记录，未当作通过。

这些预算不是所有异常主机状态下硬性墙钟保证；不对 s1 曾出现的一小时延迟作新的归因。

## 3. 新编译及无数据库门禁

全部使用本批冻结源码和新的 `build/a1`，未复用旧 classes：

| 项目 | 实际结果 |
| --- | --- |
| 固定工具版本检查、源码编译 | 通过 |
| Java 隔离/角色/归属/MFA 夹具门禁 | 100 通过，0 失败/错误/跳过 |
| Node 隔离/生命周期/父链/查询/真实合成计时/哨兵/XML/编排/报告/入口门禁 | 505 通过，0 失败/跳过/取消 |
| 真实 Maven 三阶段落盘探针 | 3 次执行，各 1 项通过 |
| 新产物与输入、专用 POM、报告路径绑定 | 通过 |

Java 四组数量为 42、7、29、22。Node 全部 15 个套件及精确计数见输入清单的 `nodeGates` 和构建记录的 `nodeResults`；旧脚本在新快照中重新运行，未累加历史成绩。真实合成进程计时证据位于 `build/a1/tool-evidence`。

探针路径 `build/a1/landing-reports/{independent,sequential,full}`，业务路径 `build/a1/reports/{independent,sequential,full}`。使用同一个实际 Maven 参数构造/执行链。三次都验证了 XML、stdout、有效 POM、方法清单、前阶段哈希不变和默认报告目录不被写入；业务执行期间探针证据也保持不变。

共 1190 个编译 class/SQL 文件，其中产品 class 855 个、测试 class 239 个。产品 class/SQL 与旧参考产物比较无变化；11 个测试 class 不同，名单记录于构建与补充复核，不能把“方法源码正文未改”写成“所有测试字节码相同”。

## 4. 实际迁移与权限

迁移文件共 94 个。空库执行链：

`B4_71 → V4_72 → V4_85 → V4_86 → V4_87 → V4_88 → V4_89 → V4_90`。

| 数据库/步骤 | 新增迁移数 | 最终历史条数 | 结果 |
| --- | ---: | ---: | --- |
| 主库空库至 V4_90 | 8（含 baseline migration） | 8 | 通过 |
| 增量库空库至 V4_88 | 6 | 6 | 通过 |
| 增量库 V4_89 | 1 | 7 | 通过 |
| 增量库 V4_90 | 1 | 8 | 通过 |

每步都实际执行了身份、精确迁移顺序/数量、校验和验证及重复迁移零变更检查。未 repair、clean、强制 baseline 或放宽约束。完整历史版本/脚本/校验和在 `database-result.json.steps` 及对应日志。

升级哨兵按确切标识核对：用户 1、审计 1、旧会话 1；用户/审计/旧会话字段保持，V4_90 新增的实际字段 `browser_binding_hash` 为 null。哨兵准备与迁移阶段独立记录。

运行角色实测：

- `p3_r8_app`：rolsuper=false、rolbypassrls=false。
- 不拥有 public/专用控制 schema 中的表及关系（计数 0）。
- 对 Flyway 历史 INSERT/UPDATE/DELETE 和专用 ready 标记 UPDATE 均无权限。
- 迁移只由专用准备器和 migrator 执行；运行角色不负责迁移。

保留两项说明：

1. Flyway 9.22.3 实际日志警告 PostgreSQL 18.6 高于该版本已测试支持范围（日志称最新支持到 15）。本次隔离迁移通过不等于生产兼容性通过；没有自行升级依赖。
2. 准备器四条迁移日志的末行仍含旧的 **“R7 dedicated migration step verified”显示标签**，plan-only 说明也有旧标签。这是测试工具显示字符串遗漏；实际固定 URL、角色、数据目录、SQL/物理集群身份、冻结输入及新产物均为 r8。原日志保留不改写，列为后续测试工具文字收尾项，不将它作为 r7 结果复用。

## 5. 三阶段实际业务结果

同一新产物按授权顺序执行。以进程结果、实际 XML testcase 和预期方法清单共同判定；后续只读复核再次读取全部 12 份已执行业务 XML，数量和哈希与原结果相符。

| 阶段 | 计划 | 通过 | 失败 | 错误 | 跳过 | 未执行 | 无法核实 | 阶段结果 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 独立目标 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | PASSED |
| 前序 41＋目标 | 42 | 42 | 0 | 0 | 0 | 0 | 0 | PASSED |
| 完整回归 | 63 | 45 | 1 | 2 | 0 | 15 | 0 | FAILED |

106 是原计划累计执行次数，不是 106 个不同方法。实际累计执行 **91 次**：88 通过、1 失败、2 错误；其余 15 次未执行。不能将前两阶段或历史成绩拼入完整 63 项结果。

完整阶段逐套件：

| 套件 | 计划 | 实际 |
| --- | ---: | --- |
| TenantSecurityPostgresTest | 4 | 4 通过 |
| TenantSecurityCommandsPostgresTest | 6 | 6 通过 |
| PlatformSessionPostgresTest | 27 | 27 通过 |
| PlatformInvitationSessionPostgresTest（invitation*） | 4 | 4 通过 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 | 1 通过 |
| PlatformCompanyDataServiceIntegrationTest | 6 | 3 通过、1 失败、2 错误 |
| TenantSecuritySpringPostgresTest | 14 | 未执行 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 | 未执行 |

目标 MFA 用例三次结果一致：调用前有效超级管理员 1、待接受邀请 0、目标挑战 1；准确异常为 `PlatformAdminInvitationMfaCredentialException / AUTH_FAILED`。MFA 用户失败次数 5、锁定有效、挑战失败次数 5、挑战已消费、未创建邀请等原断言通过。比较的是目标状态，不是全库历史记录总量。**r5 当时实际异常码未留存仍是未知。**

12 次已执行套件均完成数据库夹具归属/收尾核验；未知残留未被认领或删除，公共角色和既有不可变审计未改写。此项只证明数据库夹具收尾，不代表失败测试的所有文件系统临时产物均已清除。

### 5.1 两个已确认的 PURGED 夹具错误

错误方法：

- `tenantOverviewIsExplicitlyAuditedAndPurgedTenantFailureIsRecorded`
- `tenantDirectoryFiltersInDatabaseAndExcludesPurgedTenants`

实际异常 `DataIntegrityViolationException`，SQLSTATE `23514`，约束 `ck_tenants_retention`。

冻结测试的 `tenant(slug,status)` 直接插入 PURGED，但未设置 `closed_at` 和 `purge_due_at`。现有 baseline/历史 V4_50 约束要求这类状态两字段非空，且 `purge_due_at = closed_at + interval '30 days'`。错误发生在目标业务断言之前，属于明确的测试数据准备不合法，不能放宽产品约束来通过。原 XML/日志保留。

### 5.2 导出用例失败：事实与推断分开

`singleDatasetExportContainsNoSecretDataset` 实际断言失败：预期 COMPLETED，实际 FAILED；文件内容与敏感数据排除的后续断言没有执行，不能记为导出功能通过。

只读源码发现：

- 夹具仅保存 PlatformUser；通过模拟 SecurityContext 赋予 ROLE_PLATFORM_TENANT_EXPORT，并建立数据授权。
- 后台 `PlatformTenantDataSafety.requireWorkerExport` 会重新查询数据库中实际的用户角色关联。
- 该夹具没有创建对应的持久化角色关联，存在明确的请求身份与后台授权状态不一致。

这是需修复和实测确认的夹具方向，**不是本次已捕获的底层导出异常码**。现有 XML 仅留存 FAILED 断言；输出没有后台原始异常详情，准备器的通用错误不能替代运行事实。未在关闭后重连数据库取数，未擅自修改安全检查或产品逻辑。下一批应以专用测试诊断按确切 job/actor 标识留存允许公开的任务错误码和审计异常类型，不记录认证材料。

导出测试仅在全部断言之后执行临时目录删除，因此本次提前失败时该语句未执行。只读发现一个与前缀及执行时间匹配的空目录：

`C:\Users\Arnold\AppData\Local\Temp\bcwms-platform-export-test-16037803463767874847`

创建 UTC 15:50:51.286，观察时无内容。原测试未输出确切目录归属，保留此目录，没有按前缀批量删除或把它宣称为已清理。下批应补充归属登记及 finally 收尾。

## 6. 停止、关闭及历史保护

业务批次记录区间：UTC **15:29:03.134～15:51:54.395**（北京时间 23:29:03.134～23:51:54.395）；最终文件指纹复核及只读报告整理在此之后完成。

- 物理/SQL 集群号：`7686526692627067356`。
- 核实父链：pg_ctl 68760 → cmd 69652 → postgres 17360；进程路径、所有者、创建时间/ticks、数据目录、集群号和监听均验证。
- 完整阶段第 6 个套件失败后停止后续阶段；第 7/8 套件未启动。
- 实际一次启动、一次正常停止，无失败后重试。
- 正常停止命令 UTC **15:51:36.574**，退出 0；数据库日志北京时间 **23:51:36.722** 记录关闭完成。
- `stop.status=STOPPED`、attempted=true、completed=true、`normalShutdownProven=true`。
- 目标 postgres、已识别 cmd 及后代、55449 监听、postmaster.pid 均确认消失；查询失败未当作不存在。closeoutErrors 为空。
- 核查范围内其他监听与之前一致：5432 / PID 10568；没有操作其他服务。这不是对所有服务健康状态的扩展保证。
- 临时明文初始化凭据文件已删除，内存凭据引用清除；报告不保存原始密码、JWT、MFA 密钥或恢复码。新 pgdata、日志及脱敏证据保留。
- 本轮初末完整指纹核验通过。r2～r7 的 47,808 个历史文件、s1 的 1,945 个受保护文件及既有工具修复证据保持；另外封存核验 s2 证据/数据/准备目录及报告共 1,990 个文件。原始 r2～r7、s1 失败记录未改写；s2 原通过结果未改写。
- 本次 `database-result.json` 原样保留 **FAILED**，外层执行器退出码 1。补充 `post-run-review.json` 仅只读复核，不改变原结果。
- 没有修改产品逻辑或 63 个方法来修补后重跑，没有创建 r9。

## 7. 指纹和证据入口

证据目录：`D:\ERP_WMS\BC warehouse\2G\docs\releases\platform-db-acceptance-20260916\r8`。

| 对象 | SHA-256 / 标识 |
| --- | --- |
| 后端输入 ID | 8d118d0a4de14d8c676d1475f1f573677e4d3e4d44e56bf705ad94be106f29fa |
| 源码组指纹 | 8c0c8a0679cd01068a65199278f579ab52d80f829a376fa4d9d0df7aab7cfd9f |
| 新 JAR | 92563c237ab26cf01427d05c7b4d2f870f3b28f6f1109b72d2caa874bdd7ed4f |
| 编译 class/SQL 汇总 | 116a12d8e921bafe6746a3f228959a217b9b068ae4a52e080e9b7ea908755bbf |
| 专用测试 POM | 7bee6ca8caa125c02307327b1bbe8f9a522b0c3dbaa5563ce4e2ad96a5d378d8 |
| backend-input-manifest.json | a406b4e48f49bb890f2943936d962387e2e011269aa06e259ebeb6ea3568c16b |
| backend-build.json | c300dc7beaf780a8ad66a4e68e16a4d5841c9126cd62486cdb6e23e8334e8843 |
| database-result.json（原 FAILED） | a52faeb361409dd0531dfe311d02b1dd334cc3123450a2bdc6f7b25cc2fd6c79 |
| post-run-review.json | 073a5283606ef85a043664e59cb40d2db2909f4f138fb7127962df57bcc48e11 |
| postgres-private.log | 845cb62056eab451a364c725929fd9bff5dfc15dc499e8b7f5941edd9f996ad1 |

三阶段 `phases/*.json`、探针 `landing/*.json` 和 12 份实执行业务 XML 的哈希在补充复核中逐项列出。编译/探针/业务实际参数、有效 POM、文件位置和结果在构建/原始结果中；认证凭据仅通过专用进程环境或 stdin，不写入 Maven 系统属性或命令记录。

已执行的关键命令仅用于追溯，**不得对现有目录重跑**：

```powershell
& 'D:/JAVA/NODE/node.exe' 'src/test/scripts/platform-r8-backend-snapshot.mjs' seal
& 'D:/JAVA/NODE/node.exe' 'D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260916/r8/snapshot/source/src/test/scripts/platform-r8-database-batch.mjs' build
# 下条通过工具流程获批沙箱外执行
& 'D:/JAVA/NODE/node.exe' 'D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260916/r8/snapshot/source/src/test/scripts/platform-r8-database-batch.mjs' database '--authorized-20260917-r8-arnold-elevated-platform-backend-only'
```

## 8. 下一批建议与保留事项（未执行，须另行批准）

建议先限定为**测试夹具修正＋脱敏失败取证＋无数据库契约验证**：

1. 专用租户状态夹具生成合法的关闭/保留时间，按实际约束检查；不修改约束或产品状态规则。
2. 导出夹具从基线读取并核验已有的最小合法导出角色，将确切合成 actor 的持久化角色与模拟请求身份保持一致；缺失应失败，不自动新增角色、不提升权限上限、不绕过 worker 核验。
3. 导出失败时按确切 job/actor 归属取任务错误码和允许公开的审计异常类型；补充临时目录归属和 finally 收尾。若涉及业务方法诊断/清理正文变化，明确说明并更新方法指纹，不再宣称那些正文未改。
4. 修正专用迁移准备器的旧 R7 显示文字；回归实际入口。原封存日志不更改。
5. 完成无数据库验证后另行提出新环境实库计划；不能复用 r8、拼接旧成绩或自动启动下一轮。若定位到产品缺陷，再报告产品修复范围，不能顺手修改。

继续独立保留：

- 完整阶段未执行的 14 项 Spring 暂停/恢复、导出/连接联动等测试及 1 项权限竞态测试；本批不能宣布这些覆盖通过。
- 前端 addressLength.audit.test.tsx 的 TS2769 交接、正式类型检查与整包构建阻塞。本批未执行前端检查，未修复/删除/排除该测试。
- 邀请完整人工激活流程，以及其他必要发布候选人工验收；既有 MFA/恢复码人工结果不无故重做。
- 恢复核对仍为“缺单报告＋人工核验修改、取消和退款”；最终业务边界待确认，未扩展完整自动核对。
- 正式配置、生产数据库兼容性、备份恢复/迁移演练及上线审批，不属于本次隔离批次。

**当前停止点：原 FAILED 与实际成绩完整保留、正常关闭已证实、失败定位和建议修复范围已交付；等待下一批确认。**
