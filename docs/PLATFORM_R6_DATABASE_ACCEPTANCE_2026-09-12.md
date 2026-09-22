# R6 平台后端隔离数据库验收：FAILED，已正常关闭

本报告是本次 r6 实际执行及关闭后的只读复核记录，不是发布候选版本或上线批准。原始 `database-result.json` 保持 FAILED，未覆盖、移动或改写原始 XML、日志、快照及 r2～r5 历史证据。

## 1. 结论与三阶段实际结果

本次新编排工具的报告目录绑定未生效，是本次停止的已确认原因。独立阶段的业务测试实际上执行通过，但批次入口没有从指定阶段目录取得报告和夹具输出，因此停止顺序、完整阶段，并正常关闭专用集群。不能把只读复核结果倒填成阶段验收通过。

| 阶段 | 计划执行次数 | 批次入口原始记录 | 关闭后只读复核 | 阶段验收结论 |
| --- | ---: | --- | --- | --- |
| 独立 | 1 | 0 通过；1 无法核实；报告读取失败；夹具核对失败 | 同次执行实际 XML：1 通过，0 失败／错误／跳过 | FAILED；额外收尾 SQL 未采集 |
| 顺序：前序 41＋目标 1 | 42 | 42 未执行 | 未执行，不补测 | NOT_EXECUTED |
| 完整：8 套件 | 63 | 63 未执行 | 未执行，不拼接其他成绩 | NOT_EXECUTED |

106 是计划累计执行次数，不是 106 个不同方法。本次实际只执行了独立阶段的目标方法一次；其余 105 次未执行。**最终完整回归单独结果为：0 通过、0 失败、0 错误、0 跳过、0 无法核实、63 未执行。** 不把未执行称为通过，也不拼接 r4、r5 或无数据库测试。

已完成：新源码编译、343 项无数据库门禁、两库迁移、权限及哨兵检查。未完成：独立阶段编排闭环、顺序一致性比较、完整业务回归。

## 2. 授权环境、输入及产物

| 项目 | 实际值 |
| --- | --- |
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r6/pgdata` |
| 监听 | 仅 `127.0.0.1:55449` |
| 两库 | `p3_m1_platform_release_r6`、`p3_m1_platform_upgrade_r6` |
| 专用角色 | `p3_r6_initializer`、`p3_r6_migrator`、`p3_r6_app` |
| PostgreSQL | 18.6；新集群；随机凭据；SCRAM |
| 集群号／主进程 | `7684559831702267716`／PID `45912` |
| 分支／HEAD | `2-1G`／`9c12f6f893105632c4de24ad8bb4f6f43092f84e` |
| 后端输入指纹 | `a23a0d15b9d2bc73c584345630ba51be080b7b6e0f8d0b8625ae58ae38742047` |
| 输入清单 SHA-256 | `25905507a5b4c3716ed98b8066f0663907622293513312a8ded19c964cf454de` |
| 构建清单 SHA-256 | `e86077fcc6cbfe4240923608a4fd8c2cc0ca985127326414910c4393a5294026` |
| 新 JAR SHA-256 | `2a48f855224c60a9b7ba3a47481742356aa1723f130bc2dfbdb0cb8f0862d8bd` |
| 1,188 项 class／SQL 产物集合指纹 | `59a97f192b6ec45e7aa2ce75f89a259768c240185438d4687be35fa9bce157c8` |

源码：`target/platform-db-acceptance-20260911/r6/snapshot/source`。新构建目录：`target/platform-db-acceptance-20260911/r6/build/a1`。封存 934 个源码、测试、资源及工具文件，3,337 个 Maven 缓存文件、90 个 Maven 发行文件；运行类路径按既有 136 个依赖 JAR 的指纹核对。94 份迁移资源均未修改。

实际使用 Node 24.12.0、Java 17.0.17、Maven 3.9.9，离线使用封存依赖。本批未调用 pnpm 或进行前端安装／构建。未修改全局 PATH、公共工具版本声明或锁文件。

从新目录编译 580 个主源码、197 个测试源码文件，不复用旧 classes。对比已确认的夹具修复 a2 产物：11 个测试 class 变化，产品 class／SQL 变化为 0。关闭后再次核对：封存输入、工作区后端对应文件、JAR 和全部已登记编译产物均无漂移。

这只是 r6 后端隔离输入，不是正式 R2 全源码快照。工作区原有未提交改动保留，未进行提交、合并或推送。

## 3. 本批测试层修改范围

原有文件仅修改以下四个测试辅助文件的 r6 目标绑定；隔离检查另外增加对旧 r5 正确 URL 的拒绝用例：

- `src/test/java/com/wms/platformverification/PlatformR2Isolation.java`
- `src/test/java/com/wms/platformverification/PlatformR2IsolationTest.java`
- `src/test/java/com/wms/platformverification/PlatformR2SpringInitializer.java`
- `src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java`

新增四个测试工具：

- `src/test/scripts/platform-r6-backend-snapshot.mjs`：封存、依赖及历史证据核验。
- `src/test/scripts/platform-r6-database-batch.mjs`：编译、门禁、生命周期、迁移及三阶段实际入口。本次报告目录绑定缺陷在此文件，尚未修复。
- `src/test/scripts/platform-r6-stages.mjs`：1／42／63 计划、失败即停、分阶段统计、夹具归属和 MFA 比较。
- `src/test/scripts/platform-r6-stages.test.mjs`：57 项无数据库编排／归属契约检查。

没有修改产品逻辑、迁移、公共默认数据源或 8 套件当前方法正文。旧 `PlatformR2*` 类名与 `p3.r2.*` 测试授权参数名继续保留，实际目标严格固定为 r6，不代表允许访问 r2。

相对最初 r5，已批准的 7 处正文变化仍在：目标 `credentialFailureSurvivesRollbackOfInvitationCreationTransaction` 的前置条件与准确异常断言，以及 `TenantSecuritySpringPostgresTest` 中以下六项的夹具登记：

1. `realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor`
2. `alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate`
3. `provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode`
4. `platformHttpTwoPersonRecoveryCommitsWithRealAuditRepository`
5. `failedConnectionStaysDisabledAndGapNeedsCurrentVerifiedEvidence`
6. `fullApplicationWiresAndRealJpaAdmissionRejectsOldWork`

7 处变化的修改前／后指纹保存在输入清单。**本批以修订后 63 个方法为准，均未再修改；不能声称相对最初 r5 的 63 个正文全部没变。**

## 4. 无数据库门禁：343 项通过

| 门禁 | 实际通过 | 失败／错误／跳过 |
| --- | ---: | --- |
| 隔离目标及连接保护 | 40 | 0 |
| 基线角色夹具 | 7 | 0 |
| 夹具归属与收尾 | 29 | 0 |
| MFA 前置条件及准确异常 | 22 | 0 |
| 源码夹具隔离契约 | 24 | 0 |
| 启动器生命周期 | 32 | 0 |
| 升级哨兵契约 | 65 | 0 |
| 结构化 XML 解析及原有入口回归 | 67 | 0 |
| r6 实际导出入口／三阶段编排契约 | 57 | 0 |
| 合计 | 343 | 0 |

Java 为 98 项，Node 为 245 项；11 个脚本语法检查通过。正式结果来自本次封存源码构建，不叠加封存前的 57 项开发预检查。

57 项编排检查验证了入口调用、模拟 XML／进程结果、回调、归属拒绝及失败中止；**没有验证真实 Maven 对报告目录参数的解析和文件落盘**。这项覆盖缺口导致报告目录问题未在数据库启动前发现，不能将这些契约测试描述为实际三阶段数据库验收。

## 5. 两库真实迁移、权限及哨兵

数据库批次开始于 2026-09-12 16:16:51（北京时间），业务执行失败后于 16:17:56 完成正常收尾记录。启动前确认新目录不存在、55449 无监听；启动后核验 PID、可执行文件、目录、集群号、监听、SQL 数据库及角色身份。

| 目标／步骤 | 新应用迁移数 | 完成历史条数 | 重复 migrate | 结果 |
| --- | ---: | ---: | ---: | --- |
| 主库空库至 V4_90 | 8 | 8 | 0 变更 | 通过 |
| 增量库至 V4_88 | 6 | 6 | 0 变更 | 通过 |
| 增量库 V4_88 → V4_89 | 1 | 7 | 0 变更 | 通过 |
| 增量库 V4_89 → V4_90 | 1 | 8 | 0 变更 | 通过 |

94 份资源校验通过，实际历史为 `B4_71、V4_72、V4_85、V4_86、V4_87、V4_88、V4_89、V4_90`。对应 Flyway 校验和分别为 `-1902150811、-1014316113、1952011876、1901493228、1941766358、-1639265665、178422383、618333881`。

精确哨兵 ID 下的用户、合法审计、旧会话各 1 条，事务准备和升级前后保留核验通过。V4_90 的实际字段为 `browser_binding_hash`，既有旧会话此字段仍为 NULL，其余字段保持。未以全表一条记录作为哨兵断言。

应用角色不是超级用户、无 BYPASSRLS；不能写 Flyway 历史及 ready 控制表，不拥有业务／控制表。只使用空库、公共参考数据和登记的合成夹具，没有复制旧账号或真实数据。没有 repair、clean、目标回退、提高席位上限或审计约束例外。

Flyway 9.22.3 对 PostgreSQL 18.6 的支持范围警告继续单列为生产兼容性门禁；本次迁移通过不等于真实生产数据库兼容性验收完成。

## 6. 失败定位、MFA 事实与未覆盖项

### 6.1 已确认的工具问题

入口传入 `-Dsurefire.reportsDirectory=<phase-directory>`，预期写入 `build/a1/reports/independent`。实际 Surefire 3.1.2 将 XML 和 stdout 写在 `build/a1/surefire-reports`。

只读检查封存插件的 `META-INF/maven/plugin.xml`：`reportsDirectory` 默认值为 `${project.build.directory}/surefire-reports`，没有绑定上述用户属性的表达式。项目 POM 也未显式配置 `reportsDirectory`。因此该参数没有改变真实报告目录。

入口报告 `REPORT_PARSE_FAILED / REPORT_MISSING_OR_UNREADABLE`；收尾读取预期 stdout 文件又报 `FIXTURE_OUTPUT_MISSING`，最终批次分类为 `FIXTURE_RECONCILIATION_FAILED`。这是文件位置绑定问题，不是 XML 正则解析回归，也没有证据将本次停止归因于产品逻辑失败。

原始 FAILED 结果 SHA-256：`886e1d588055f5335b6736ee5c16427bb1c981f5d612794aad900d4b77ec6f7e`。原始记录中的 1 项无法核实、105 次未执行保留；补充复核不重写它。

### 6.2 同次执行的只读补充复核

采用禁止 DTD／外部实体的既有结构化解析器，从真实默认路径读取 XML，同时核对本次进程退出结果、实际 testcase 和预期方法。结果：唯一目标方法通过，失败／错误／跳过均为 0。

- 实际 XML SHA-256：`800c038c777a825030d84a2945a83ddcfbb1a632244216b3d16fb746c36b93b0`。
- 实际 stdout SHA-256：`2bc78dff369aab1dfe1fdf3ab19996a70f6ccf0fdbbb634b8eb8175956c6f121`。
- 夹具开始：有效平台账号 0、待接受有效邀请 0。
- 调用前：有效超级管理员 1；待接受超级管理员邀请 0；全部待接受有效邀请 0；目标账号匹配 1；有效目标挑战匹配 1；账号和挑战失败次数均为 4。
- 实际异常类型：`PlatformAdminInvitationMfaCredentialException`；实际错误码：`AUTH_FAILED`。精确分支断言通过。
- 原方法真实数据库断言通过：账号失败次数变为 5、锁定期限超过当时 14 分钟、挑战次数变为 5、没有创建本次邀请；`MFA_FAILED` 审计服务调用一次的 Mockito 断言通过。
- Java 夹具收尾日志记录本次账号 ID 1、邀请 ID 空、租户 ID 空，状态 `RETIRED_AUDIT_RETAINED`。完整开始／结束标识匹配。

审计服务在这个目标用例中是 MockBean；上述审计结论是调用断言，不是此目标用例新增了真实不可变审计行的证明。用户、审计哨兵的真实保留验证属于第 5 节的迁移演练，不能替代其他业务审计套件。

批次额外的套件后 SQL 快照及 `targetStateSql` 尚未运行。虽然业务方法自身持久化断言与 Java 收尾日志已通过，仍不能声称独立阶段完整归属闭环验收通过。关闭后没有重新连接数据库补采。

顺序及完整阶段都未执行，无法比较三次 MFA 有效席位、挑战状态、准确异常和持久化结果；也无法验收前序套件收尾后的隔离一致性。不会要求累计历史记录总量一致，更没有为了继续测试而认领、删除或忽略未知残留。

**r5 的实际异常码当时未留存，仍是未知。本次 r6 的 AUTH_FAILED 不得倒填为 r5 的异常码，也不能据此把 r5 的运行原因改写为已确认。**

## 7. 正常关闭与证据边界

按已核实身份对 r6 专用集群执行 `pg_ctl stop -m fast -w -t 30`；退出码 0，身份校验及正常关闭状态为 STOPPED。PID 45912、专用监听及 `postmaster.pid` 均消失。关闭后再次只读核对，结果一致。

既有 `0.0.0.0:5432` PID 8980 前后未变；检查范围内 8080、5173、55439、18091、15173 没有新增监听。没有启动前端／HTTP 服务、重启其他环境、连接真实数据库或操作真实账号／租户。随机初始化密码临时文件已移除；报告及补充记录没有归档密码、JWT、MFA 密钥、恢复码或完整认证请求。

r6 数据目录保留为已停止的失败验收环境，不复用或覆盖；r2～r5、夹具修复 a1/a2 快照及原失败记录经指纹复核保持原样。补充记录仅含允许公开的计数、测试 ID、异常类型／码和文件哈希，原始 XML／stdout 留在原位置，不搬入文档归档。

主要记录位于 `docs/releases/platform-db-acceptance-20260911/r6/`：

- `backend-input-manifest.json`：源码／依赖／迁移／历史证据指纹及方法修订清单。
- `backend-build.json`：新编译及 343 项无数据库检查。
- `environment-started.json`：专用进程、目录、集群及监听身份。
- `database-result.json`：原始 FAILED、迁移、阶段和正常关闭结果。
- `phases/independent.json`、`phases/sequential.json`、`phases/full.json`：各阶段原始独立汇总。
- `independent-readonly-review.json`：同次 XML 和允许公开日志的补充复核，不重新计入验收。
- `closeout-check.json`：源码／产物、历史证据及环境关闭后的复核。
- `delivery-ledger.json`：上述记录、报告及本次日志／实际 XML 的路径和哈希索引，不复制原始认证材料。

## 8. 下一批建议与停止点

本批已停止，不继续顺序／完整阶段，不修改后偷偷重跑。建议下一批先仅授权测试工具修复和**无需 PostgreSQL 的真实 Maven 报告落盘回归**：

1. 在专用测试 POM／测试配置适配中显式绑定 Surefire 的 `reportsDirectory`，不修改产品逻辑、公共 POM 默认行为、依赖锁或现有 63 个方法。
2. 使用实际固定 Maven／Surefire，执行小型无数据库用例，确认三个新报告目录真正分别产生 XML／stdout；核验旧目录不会被写入，后续运行不能覆盖前阶段文件。不能只做字符串包含或模拟 XML 检查。
3. 将报告位置错误和夹具 SQL 收尾错误分别记录，避免后者遮蔽原始报告错误；已有进程、XML、方法清单和失败即停规则保持。
4. 封存新输入、在新目录编译，完成实际入口及全部无数据库门禁后，再单独确认全新批次目录和完整数据库重跑。仍按同一份产物执行 1／42／63；不复用 r6 数据库，不拼接本次 1 项成绩。

本报告只提出范围，不授权或实施上述修复，也没有创建下一批集群。

前端地址测试 TS2769 继续交接，本批未重跑正式前端类型检查／整包构建，也未删除或排除该测试。地址测试和 pnpm 锁文件指纹未变。邀请完整人工激活流程仍待验；MFA／恢复码等已完成的人工项目不因本次工具问题无故重复。恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界待确认。本批不扩展 ERP 功能或后续阶段。

**最终状态：r6 FAILED，环境正常关闭；尚非发布候选版本，不可上线。**
