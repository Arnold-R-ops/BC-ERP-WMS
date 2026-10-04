# 平台清单工具修复及 r9 修改的无数据库验证

日期：2026-09-18。结论：**本批无数据库验证通过；r9 原 FAILED 保留，未完成实库验收，不是发布候选版本，不可上线。**

最终核验时间：UTC 2026-09-18 14:23:19.967（北京时间 22:23:19.967）。本轮没有失败后自行修补重跑，没有启动或连接 PostgreSQL，没有创建下一轮数据库环境。

## 1. 范围、实际修复和历史保留

本批修改 6 个测试／工具文件，新增 3 个专用脚本。产品源码、公共 POM、数据库约束及 94 个迁移文件未改；未修改 ERP、前端或锁文件，未提交、合并或推送。分支 `2-1G`、HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。

清单修复使用已批准 a3 的输入指纹及完整展开名称。仅在测试源文件和相应参数来源指纹一致时复用名称，不使用历史成绩标志。现有 MFA `@MethodSource("invalid")` 同时绑定测试文件、本地 provider 和 `PlatformMfaFailureFixture.java` 指纹，保留 10 个普通方法与 12 次参数化调用的全部 22 个名称。

变化或新增的普通 `@Test`、现有 `@ValueSource` 测试独立从源码声明生成预期名称，先冻结再执行。变化或缺少已批准清单的 MethodSource 拒绝继续，不能猜测展开结果；本批没有新增或变化的 MethodSource provider。未经明确支持的声明也拒绝，不静默漏项，不删除参数化测试。

`platform-r9-backend-snapshot.mjs` 的真实封存入口现在调用共享 `javaCatalogPreflight()`，位置在创建验收目录及复制源码／依赖之前；本批新目录封存入口调用同一个函数。`platform-r9-ledger.mjs` 也使用同一清单实现，不是旁路验证工具。新增 28 项清单检查覆盖普通／参数化声明、指纹变化、参数辅助来源变化、缺失／重复／漏项／额外／不连续名称及实际入口接入。

r9 当时没有生成完整 backend manifest，因此绑定契约改为读取其原样保留的 `method-ledger.json`，不伪造 r9 成功清单。四项 r9 夹具修正保留；本轮未再修改 63 个业务方法正文。累计账本仍为 **8 个不同业务方法、11 条修订记录**，不能改称原始 63 个正文从未修改。

r9 部分副本、原 FAILED、旧快照及其他已封存历史证据均通过逐文件指纹核验，未覆盖或改写。r9 原失败文件 SHA-256 仍为：

`4975ac07b1d004b1e9c96f5f63580d3269d494a269b8586cd561229257b3fe0e`

## 2. 四项已写入修改的验证范围

| 项目 | 本轮实际无数据库验证 | 尚不能证明的实库行为 |
| --- | --- | --- |
| 导出目录、任务及文件归属 | 17 项 Java 检查：实际 Spring 命名执行器包装、确切文件登记、真实异步 worker 等待、提前删除拒绝、未知内容保留、原配置恢复；另有 r9 接入契约 | 真实导出授权、后台权限复核、ZIP 完整性及退出／停用后的业务拒绝仍须实库执行 |
| 告警邮件隔离 | Spring 夹具共 18 项：精确告警／租户／收件人、外部行锁事务回滚、未知变化拒绝、首因保留；新增 2 项直接调用实际邮件业务测试方法，在 record 与取锁失败路径确认 mock 和配置恢复 | PostgreSQL 行锁及产品 SKIP LOCKED 在真实事务中的配合，不能由 mock 证明 |
| TRIAL 基线 | 上述 18 项中的基线测试覆盖读取已有套餐，缺失、错误状态／编码／期限／fallback／身份均拒绝，未调用补建 | 新空库的实际参考数据与开户、交接兼容仍待实库验证 |
| 并发线程收尾 | 8 项 Java 检查，包括真实双线程结束、真实卡住线程限时拒绝并显式释放；契约确认结束屏障在删除夹具前且原竞态测试仍为双线程 | 真实数据库权限、唯一约束及锁竞态仍待实库验证 |

新增两个 Java 方法：`actualMailCaseRestoresConfigurationAndMockOnRecordFailure`、`actualMailCaseRestoresConfigurationAndMockOnLockFailure`；新增 `mailFixture()` 仅组合 mock 和内存配置，无真实 DataSource。其余原 16 个 Spring 夹具测试保留。方法及文件指纹见本批 `METHOD_INDEX.md`。

本批专用 `export-fixture-temp` 最终为空；只处理本次登记的合成文件。没有清理历史归属不明目录，没有真实邮件或认证材料归档。

## 3. 实际执行结果

| 验证 | 本批实际结果 |
| --- | --- |
| 清单预检及新副本封存 | 通过；987 个源码／测试／资源文件，另生成专用 POM |
| 离线全新编译／打包 | 通过，32.958 秒，未复用旧 classes |
| Java 无数据库回归 | **162 执行、162 通过，0 失败／错误／跳过**，83.954 秒 |
| Node 无数据库回归 | **20 个套件、583 执行、583 通过，0 失败／跳过／取消** |
| 真实 Maven 三阶段落盘探针 | **3 次执行均通过**，是同一个无数据库方法执行三次，不是三项数据库业务方法 |
| 本轮实库业务方法／迁移 | **0 执行**；1／42／63 全部未执行 |
| 最终源码、产物及历史指纹核验 | 通过；1204 项 class／SQL，产品目录 951 项与 a3 双向对比无新增、缺失或变化 |

Java 明细：隔离 43、基线角色 7、归属 29、MFA 失败分支 22、时间／角色／导出诊断 18、导出目录及执行器 17、线程收尾 8、Spring 夹具 18。93 个名称来自经指纹验证的不变源文件清单，69 个名称由变化或新增源码独立生成；这些仅为预期名称，全部成绩来自本次实际执行。

Node 明细以 `input-manifest.json.nodeGates` 和 `verification-results.json.nodeResults` 为准；包含生命周期、身份查询、真实无数据库合成子进程计时／超时与独立收尾、哨兵契约、结构化 XML 解析、1／42／63 编排、报告绑定、r9 接入及清单修复。它们不是 PostgreSQL 启停或业务事务测试。

三次探针通过实际数据库批次导出的 `executePlatformSuite === executePlatformMavenSuite` 同一参数构造和 Maven 调用链执行，实际 effective POM、XML、stdout、进程结果和夹具日志分别核验。目录为本批 `backend/landing-reports/{independent,sequential,full}`；Java 门禁使用 `backend/no-database-reports`。后续阶段未覆盖先前文件，文件哈希保持不变；默认 `backend/surefire-reports` 未产生，数据库业务 `backend/reports` 未产生。

探针 XML SHA-256：

| 探针阶段 | XML SHA-256 |
| --- | --- |
| independent | `bd8d2dc3f796cc06563dde07da230285990bbb0b4de9066fe330f843e17a0169` |
| sequential | `de8b3619624f6297b105905d25ae2943b08ed06d08178ffcd28a6be79f2466aa` |
| full | `228797e6e47d01aaed0928e3226a7580ad68167a3b64d8acbd51323a694d8e37` |

保留但未擅自修正的非失败告警：编译记录有 5 条 `javax.annotation.meta.When.MAYBE` 未知枚举警告；Surefire dumpstream 记录 Windows Manifest-JAR 绝对 classpath／不同根提示。没有按提示关闭 classpath 安全检查，没有改依赖。实际进程退出 0、完整 XML／stdout 和所有预期方法均已核验，不能把这些提示省略为“无任何警告”。

## 4. 输入、产物和命令对应关系

独立输出根：`D:\ERP_WMS\BC warehouse\2G\target\platform-case-catalog-repair-20260918\a1`。

脱敏证据：`docs/releases/platform-case-catalog-repair-20260918/a1`。原 r9 目录未复用。

实际入口（工作目录为项目根）：

```text
D:/JAVA/NODE/node.exe src/test/scripts/platform-case-catalog-verification.mjs seal
D:/JAVA/NODE/node.exe target/platform-case-catalog-repair-20260918/a1/source/src/test/scripts/platform-case-catalog-verification.mjs build
```

第二条强制从冻结源码执行。源文件 987、冻结依赖 3337、Maven 发行文件 90；专用 POM 仅在新副本生成，公共 POM 不变。Node 24.12.0、Java 17.0.17、Maven 3.9.9 均实际查询核验，effective POM 确认为 Surefire 3.1.2；离线解析依赖，没有下载／升级、修改全局 PATH 或锁文件。pnpm／前端没有执行。

预算执行前固定：版本查询 15 秒、编译及 Java 门禁各 300 秒、每个 Node 套件 120 秒、每次 Maven effective／test 各 240 秒，fork 120 秒、退出 20 秒；未运行中加时。无数据库合成子进程的预期超时由各测试断言核验，不是批次调用超时或业务失败。

| 指纹 | SHA-256 |
| --- | --- |
| 输入 ID | `28b65f41ab621332455c3bdb5e1c9de77773e80aab1a1baa21102e21bb0a9691` |
| 输入清单文件 | `ed64025768eb73d402158420a1a8508eaba4695bcb0cf3c2eadd7b90a605cc2f` |
| 源码组 | `b32da9cb0a7ebd5d57aeb78fd5632a27b76791f855ddacec67457b4f725a7768` |
| 专用 POM | `7bee6ca8caa125c02307327b1bbe8f9a522b0c3dbaa5563ce4e2ad96a5d378d8` |
| 新 JAR | `e4a2df049d3f016698c657c9aa7b741d3b4ae147e6e2e490c8d9998f6d2f706b` |
| class／SQL 聚合 | `a033a5ed9c508ebbadeab04c3592ab1609104eb92f563576ea13920968ee2f60` |
| 实际结果文件 | `f0e975f1a71f88ebfb6b6195802f9f83d4cd61d2220891762d79b350d0b85e7e` |

逐文件、方法名、修改链、调用参数、预算、退出状态、日志与报告哈希保存在：

- [冻结输入及完整预期清单](releases/platform-case-catalog-repair-20260918/a1/input-manifest.json)
- [实际验证结果](releases/platform-case-catalog-repair-20260918/a1/verification-results.json)
- [本批修改与方法索引](releases/platform-case-catalog-repair-20260918/a1/METHOD_INDEX.md)
- [三阶段探针记录](releases/platform-case-catalog-repair-20260918/a1/landing/full.json)

## 5. 仍未覆盖和禁止推导的结论

本轮没有初始化目录、创建数据库／角色、连接数据库、执行迁移或启动前端／HTTP 服务，也没有操作既有服务。数据库收尾应记为 **NOT_STARTED_NO_STOP_PERFORMED**，不是“本轮正常关闭验证成功”。

r9 仍然是封存前置失败，不能以本轮无数据库通过改写其结果。r5 未留存的具体异常码、r8 未留存的导出底层异常仍未知。缺单报告＋人工核验修改、取消和退款的最终业务边界仍待确认。

前端地址测试 TS2769、正式类型检查／整包构建保持范围外交接及未通过状态，本轮未复核解决情况。邀请激活完整人工流程继续待验；已通过的 MFA／恢复码等不无故重复。生产数据库／Flyway 兼容、正式安全配置、备份恢复和迁移演练仍是独立后续事项。

## 6. 下一次完整实库方案（新 r10，尚未创建，待单独批准）

建议将目标绑定、重新封存编译、无数据库门禁、探针及完整实库验收合为一次授权。当前已只读确认以下新根不存在；执行前仍须重新检查，不能以本次检查代替启动前检查。

| 目标 | 固定建议 |
| --- | --- |
| 新根 | `D:\ERP_WMS\BC warehouse\2G\target\platform-db-acceptance-20260918\r10` |
| PGDATA | 上述根下 `pgdata` |
| 证据 | `docs/releases/platform-db-acceptance-20260918/r10` |
| 监听 | 仅 `127.0.0.1:55449` |
| 主库／增量库 | `p3_m1_platform_release_r10`／`p3_m1_platform_upgrade_r10` |
| 角色 | `p3_r10_initializer`、`p3_r10_migrator`、`p3_r10_app` |
| 测试控制 schema | `p3_r10_control`，仅专用准备器管理 |
| 导出临时根 | 新根下 `export-fixture-temp`，禁止旧根或全局 Temp 回退 |
| 编译产物 | 新根下 `build/a1`，源码副本为新根下 `snapshot/source` |
| 报告目录 | `build/a1/landing-reports/{independent,sequential,full}` 与 `build/a1/reports/{independent,sequential,full}` 分离 |
| 工具 | Node 24.12.0、Java 17.0.17、Maven 3.9.9、Surefire 3.1.2、PostgreSQL 18.6；不升级 Flyway |
| 执行身份 | 拟使用已说明的沙箱外 `ARNOLD\Arnold` 提升令牌上下文，执行前重新核验并按工具流程申请；不 RunAs、不修改 UAC 或系统权限 |

仅空库、源码公共参考数据和明确登记的合成夹具；随机凭据、SCRAM，使用结束删除本批临时明文凭据。运行角色必须核验无 SUPERUSER、无 BYPASSRLS，不拥有业务／控制表；不得修改 Flyway 历史和测试 ready 标记。公共参考角色／套餐只核验，不补建掩盖缺失。

连续执行顺序：

1. **测试层 r10 接入**：新目录／双库／角色／control schema／参数与产物绑定；沿用本批清单预检、实际 Maven 调用链和 8 方法／11 修订账本。保留 63 个业务目的与断言，不改产品逻辑。旧 r9 绑定契约核对其本轮冻结版本，新目标使用独立契约，不能把旧目录当成新目标。
2. **重新封存及前置检查**：创建任何验收副本之前冻结完整预期清单；重新编译新输出，不直接复用本批 classes。运行现有无数据库覆盖及新目标绑定检查、三次真实 Maven 探针。当前覆盖基准为 Java 162、Node 583；目标适配后以独立冻结清单确定实际数量，不能减少原覆盖或从 XML 倒推。全部通过才能初始化数据库。
3. **新集群及迁移**：实际查询能力前检、端口／路径／进程／所有者／创建时间／父链核验；一次初始化及启动；核对集群号和 SQL 身份。主库空库迁移至 V4_90；增量库 V4_88→V4_89→V4_90；逐阶段核对版本、数量、校验和、约束、确切哨兵和重复迁移零变更。不得 repair／clean。
4. **同一新产物 1／42／63**：独立目标 MFA 事务 1 项；前序 41 项后目标复测，共 42 项；完整 8 套件 63 项。106 是累计执行次数，不是不同方法数。各阶段独立目录，逐项核对进程、XML 实际元素、预期清单、准确异常、夹具归属及收尾；不能拼接历史成绩。完整套件为会话 27、邀请会话 4、受控读取／导出 6、MFA 事务 1、角色竞态 1、安全状态 4、安全命令 6、Spring 联动 14。
5. **身份绑定关闭及交付**：成功或失败都执行独立限时收尾，只停止确认属于本批的集群；关闭后确认目标进程与监听消失。保留首因、准确成绩、未执行／无法核实项、所有产物指纹及脱敏证据。不得将查询失败记为无残留。

拟固定预算与当前计划相同：编译／Java 门禁各 300 秒；Node 套件各 120 秒；迁移每条 180 秒；Maven effective／业务／探针每次 240 秒（fork 120、退出 20 秒）；初始化 60 秒；启动命令 35 秒／包装 37 秒；就绪 40 秒；独立收尾 60 秒；停止命令 32 秒；SQL 30 秒／statement 15 秒；完整身份查询 15 秒、单 PID 5 秒、就绪 SQL 4 秒。执行前记录，运行中不自动加时。

**停止条件**：目录已存在、端口冲突、身份／指纹／迁移状态不符、任何阶段失败／非预期跳过／证据不完整、未知残留或夹具／环境收尾未完成，立即停止后续阶段，仅做已核实目标的必要收尾。不换目标、不覆盖、不放宽断言、不删除未知数据、不自行修补后重跑或创建下一轮。发现产品缺陷先报告修复范围。

本节仅提交方案，不是启动授权。本轮未创建 r10、不申请数据库启动权限；前端构建、人工验收和正式上线准备不纳入该实库批次。即使未来 1／42／63 全部通过，也只代表对应冻结版本的平台后端隔离验收，不代表发布候选版本完成或可以上线。
