# 平台专用报告目录绑定修复与真实 Maven 无数据库验证

本批已完成：专用报告绑定修复、新源码编译、403 项无数据库回归和同一小型测试的三次真实 Maven 落盘验证。数据库业务测试执行数为 0。本报告不构成数据库验收、发布候选版本或上线批准。

## 1. 本批实际结果

正式结果取自冻结的 a2 输入，完成时间为 2026-09-13 15:52:48（北京时间）。不累计封存前开发预检，也不拼接旧批次成绩。

| 项目 | 实际通过 | 失败／错误／跳过／未执行／无法核实 |
| --- | ---: | --- |
| Java：隔离目标及连接保护 | 40 | 均 0 |
| Java：基线角色夹具 | 7 | 均 0 |
| Java：夹具归属与收尾 | 29 | 均 0 |
| Java：MFA 前置条件及准确异常 | 22 | 均 0 |
| Node：源码夹具隔离 | 24 | 均 0 |
| Node：启动器生命周期 | 32 | 均 0 |
| Node：升级哨兵契约 | 65 | 均 0 |
| Node：安全 XML 解析及入口 | 67 | 均 0 |
| Node：三阶段编排及归属 | 57 | 均 0 |
| Node：报告目录、首因、POM、执行后证据异常 | 60 | 均 0 |
| 无数据库回归合计 | 403（98 Java＋305 Node） | 均 0 |

另有 6 个 JavaScript 语法检查通过。编译阶段使用 `-DskipTests package`，实际编译 580 个主源码和 198 个测试源码；测试随后按本批批准的专项清单显式执行。不是全工作区所有测试通过的声明。

### 三次真实落盘

实际执行同一个 `PlatformReportLandingTest.writesPhaseBoundFixtureEvidenceWithoutDatabase`，每次使用独立的调用 UUID 和阶段标识：

| 阶段目录 | 实际 testcase | 通过 | 失败／错误／跳过／未执行／无法核实 | XML、stdout、夹具读取 | 保护目录 |
| --- | ---: | ---: | --- | --- | --- |
| independent | 1 | 1 | 均 0 | 通过 | 默认目录保持原样 |
| sequential | 1 | 1 | 均 0 | 通过 | independent、默认目录保持原样 |
| full | 1 | 1 | 均 0 | 通过 | independent、sequential、默认目录保持原样 |

这是 **1 个无数据库方法的 3 次执行**，不是 3 个业务方法，更不是 1／42／63 数据库阶段通过。探针只调用真实夹具生命周期的空内存 Store：无 JDBC、无 Spring 启动、无账号数据、无网络或数据库事务。

报告根目录：`D:/ERP_WMS/BC warehouse/2G/target/platform-report-binding-repair-20260913/a2/backend/phase-reports`。各阶段实际产生 XML、`*-output.txt`、文本摘要及 Surefire dumpstream，各 4 个文件；完整路径和哈希均已登记。不是仅检查 Maven 退出码：同时核对 testcase 元素、唯一预期方法、计数、作用域完整性及本次调用标识。

| 阶段 | XML SHA-256 | stdout SHA-256 |
| --- | --- | --- |
| independent | `7bd6e7601afa9c1be48323b0ddd22a42ce7b90f10dd83a25aa0f29da0a16a8b2` | `6f705208c5840d4b0aeba1905adb6511d951a944ee2b108d4ea872887cadb712` |
| sequential | `d73e70f05673f7feba3eec51dcda304467db843a9cea8af30b7adc2e37838725` | `c9eaf7737d5fde82449525c85d2915c606a79d110f8a0b9a3e333a6e43b3e5ac` |
| full | `204fc7982b31852eeda57a76db52e170ae3d2f1e0f8a84f868064a8008555f0a` | `475967df761396d515b052a112f37bcdbabf862079514363a85f86e6d425ee26` |

默认报告目录 `D:/ERP_WMS/BC warehouse/2G/target/platform-report-binding-repair-20260913/a2/backend/surefire-reports` 中有前置 Java 门禁生成的 10 个文件。三次落盘调用前后，该目录文件集合和各文件哈希均未变化；集合指纹均为 `570462e67cda70a18e38ff474c797551cb47198fbd318f5100ef3676d123f70f`。不是声称默认目录原本为空。后续阶段同样核对前序目录是否存在、完整文件集合和内容哈希，包括文本及 dumpstream；没有回退读取默认或旧报告。

## 2. 修复清单与真实调用链

仅修改 3 个已有测试脚本：

- `src/test/scripts/platform-r6-database-batch.mjs`：实际业务回调接入共享执行器；读取其指定位置的结果和夹具诊断；最终汇总保留首个原因。
- `src/test/scripts/platform-r6-stages.mjs`：报告路径、解析、夹具日志和收尾错误分别记录；证据不完整也会停止后续阶段。
- `src/test/scripts/platform-r6-stages.test.mjs`：更新实际入口绑定回归，不再把无效的旧 CLI 属性当成配置成功证据。

新增 5 个测试文件：

- `src/test/scripts/platform-report-pom.ps1`：禁止 DTD／外部实体的结构化 POM 生成及有效配置读取。
- `src/test/scripts/platform-report-runner.mjs`：统一 Maven 参数构造、真实执行、有效配置核验和报告读取。
- `src/test/scripts/platform-report-runner.test.mjs`：60 项报告相关回归。
- `src/test/java/com/wms/platformverification/PlatformReportLandingTest.java`：唯一无数据库落盘探针。
- `src/test/scripts/platform-report-binding-verification.mjs`：本批封存、编译及真实验证入口；只有 seal／build／verify，无数据库启动模式。

专用 POM 由冻结的公共 POM 派生，放在相同源码根目录：
`D:/ERP_WMS/BC warehouse/2G/target/platform-report-binding-repair-20260913/a2/source/platform-acceptance-pom.xml`。

仅显式增加 Surefire 版本 `3.1.2`、`reportsDirectory=${platform.verification.reportsDirectory}` 和 `redirectTestOutputToFile=true`。生成器反向移除这三项后，结构化核对其他配置完全一致。公共 POM 没有修改，includes／excludes、依赖、源码目录、资源和公共默认行为均保留。

真实验证直接调用冻结源码内的 `platform-r6-database-batch.mjs` 导出的 `executePlatformSuite`。运行时核对它与共享 `executePlatformMavenSuite` 是同一个函数对象；实库回调也使用该函数。未向三次真实调用注入模拟进程或模拟 XML。故障契约中的受控注入只用于不启动 Maven 的异常回归。

每次真实调用均走同一链路：

1. 校验专用 POM 哈希、拒绝已有报告、登记默认和其他阶段文件集合。
2. 使用固定 Java 启动冻结 Maven 3.9.9，离线执行固定 help-plugin 3.4.1 的 effective-pom。
3. 读取真正生成的有效配置，核对 Surefire 3.1.2、报告目录、stdout 设置、冻结源码目录及新 classes／test-classes。
4. 用同一参数构造执行固定 `maven-surefire-plugin:3.1.2:test`。
5. 实际解析器及夹具读取器只读本次确切路径，核对进程结果、XML 元素、方法清单和调用标识，检查前序／默认文件哈希。

三阶段共 6 次 Maven 调用（3 次有效配置＋3 次探针），实际进程均 exitCode=0、已退出、输出完整、terminationConfirmed=true，无超时或跳过。精确可重现参数数组、可执行文件、cwd、有效配置文件路径和哈希均在各阶段 JSON 的 `commands` 与 `effectiveConfiguration` 中；未归档原始环境变量或认证请求。

当前原始 r6 冻结执行入口仍原样保留，不能直接拿它重跑或访问旧库。工作区修复接线已由本次新快照实际验证；下一批需为新目标生成新快照及专用 POM 绑定，不修补旧 r6 封存件。

### 异常与首因处理

- `REPORT_CONFIGURATION_ERROR`：有效 POM 或参数绑定失败。
- `REPORT_PATH_ERROR`：指定文件缺失、已有文件拒绝、默认／前阶段被改动。
- `REPORT_PARSE_ERROR`：XML 不安全、损坏、头部计数或方法清单不符。
- `FIXTURE_LOG_ERROR` 与 `FIXTURE_CLEANUP_ERROR`：日志证据和实际夹具收尾分别记录。
- `REPORT_EVIDENCE_IO_ERROR`：已执行之后保存日志或采集证据失败，保留真实 process 和已采集结果。
- 执行器尝试调用后未返回结果时，记录 `TEST_EXECUTION_RESULT_UNAVAILABLE`、启动状态 UNKNOWN、结果无法核实，不误写成未执行。

后续错误追加而不覆盖首因；若已验证 XML 显示业务通过但收尾不完整，保留这个局部事实，同时阶段仍失败。没有旧报告回退、复制成绩、放宽业务断言或凭退出码判通过。

## 3. 封存、工具链及产物对应

| 项目 | 完整指纹／实际值 |
| --- | --- |
| 分支／HEAD | `2-1G`／`9c12f6f893105632c4de24ad8bb4f6f43092f84e` |
| a2 后端输入 ID | `d2e6fc93b2828501ede32cb8a4a5ba7aed9442dd9cd821636ff7a38cf735b523` |
| 输入清单 SHA-256 | `44baa7b4228ded0431a4315f62ac0aa7ced31820c31eea87e0b41c977ae4089c` |
| 正式验证结果 SHA-256 | `7c0e850b995d417349d4a335fe9007e6a1d52bcb3b3a68f51344cac51e40ce88` |
| 新 JAR SHA-256 | `d7b7beb37145c0fc8cae2526f41ead040ac09bfe5f6e3e2b8011d9a15f62f993` |
| 1,190 项 class／SQL 集合指纹 | `3bd67bcd17e8ce186045361c7793748ed59b9ba5da8062d06e6e7306812b11c3` |
| 专用 POM SHA-256 | `7bee6ca8caa125c02307327b1bbe8f9a522b0c3dbaa5563ce4e2ad96a5d378d8` |
| 共享执行器 SHA-256 | `393f91001eb42d50c3f173d674f6a35e009a4d7f90bbdfe7b35532fc5eead2dc` |
| 实际批次接线 SHA-256 | `fd710291569c8f8dd7e5d5201ad6b9bb22c6ae1726f32c5b2a824d99e9de0c3c` |
| 公共 POM SHA-256（未变） | `bd369b7547102dd780fce6c7f872f8854e5f6a6d78b589487408633fac5b54a4` |

冻结源码：`D:/ERP_WMS/BC warehouse/2G/target/platform-report-binding-repair-20260913/a2/source`；新构建目录：`D:/ERP_WMS/BC warehouse/2G/target/platform-report-binding-repair-20260913/a2/backend`。939 份后端源码／测试／资源／工具、3,337 份冻结 Maven 缓存、90 份 Maven 发行文件，94 份迁移资源，逐文件哈希保存在输入清单。专用派生 POM 另纳入输入指纹，未改变公共源码 POM。

实际版本输出核对为 Node 24.12.0、Java 17.0.17、Maven 3.9.9；Surefire 3.1.2 来自三份真实有效配置。未执行 pnpm、未升级依赖、未改锁文件、全局 PATH 或公共版本声明。

新编译相对 r6 只增加探针及其空内存 Store 两个测试 class；产品 class／SQL 变化为 0。所有三次调用使用同一份新产物，阶段之间及结束后 class／SQL、JAR 对应关系和源码指纹未漂移。现行 63 个业务方法正文未再修改；相对最初 r5 已批准的 7 处正文变化仍完整保留，不能改称最初 63 个正文全部没变。

### 开发预检与 a1 边界

a1 输入 `40935d2e9c5ea22b733609ff7ac81d5b1a545ad181f39ece09819aa530ca7a76` 仅封存、未编译。复核发现执行后证据 I/O 可能被误记为未启动，因此保留 a1 原样并另建 a2。

开发新增故障回归首轮实际为 60 项中 51 通过、9 失败，发现共享执行函数局部 `process` 变量遮蔽导致正常调用被阻断。已在测试工具中修复；开发第二轮 60 项通过。两轮仅有开发工具输出，没有封存 TAP 文件，不伪造文件哈希或累加成绩。正式 a2 从其冻结源码重跑，`reports.tap` 实际 60／60 通过，计入上表。

## 4. 历史、环境与剩余风险

r2～r6 环境和证据、r6 原 FAILED、同次目标用例只读通过补充、未完成的额外收尾 SQL 分别保留，没有修改历史记录。r6 原始 `database-result.json` SHA-256 仍为 `886e1d588055f5335b6736ee5c16427bb1c981f5d612794aad900d4b77ec6f7e`，只读补充仍为 `c1dd33ca9fae47d236a593e21a053a0543e4a6e97ddf7f033993ec906e327188`。不能用本批无数据库验证倒填 r6 阶段通过，也不能倒填 r5 未留存的实际异常码。

本批未启动或连接 PostgreSQL，未运行迁移、未创建下一批集群，数据库业务执行为 0；没有待关闭的本批数据库进程。所有本批构建／测试命令已正常结束。只读监听观察前后均为既有 `0.0.0.0:5432` PID 10452；55449、55439、8080、5173、18091、15173 无新增监听。这里使用本批开始时的 PID，不与 r6 历史记录中的旧 PID 混为一谈。

Surefire 在各阶段留下 Windows 绝对类路径相关 dumpstream 提示；实际用例和进程均通过。提示文件及哈希原样保留，未采用提示中的 `-Djdk.net.URLClassPath.disableClassPathURLCheck=true` 参数，也没有升级插件或关闭相关检查。不将“有提示但执行通过”写成“无任何提示”。

本批故障契约验证工具如何分类和保留首因；尚不证明真实数据库事务、并发、夹具 SQL 收尾或三阶段状态一致性。完整 63 项数据库回归仍待新批次，不拼接历史成绩。

前端地址测试 TS2769 继续交接；本批不修改、删除或排除测试，也未重新执行正式前端类型检查／整包构建。地址测试和锁文件前后哈希未变，前端阻塞状态没有解除。邀请完整人工激活流程仍待验；既有 MFA、恢复码等人工结果保留，不无故重复。恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界待确认。

## 5. 交付记录

记录目录：`D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2`。

- [input-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/input-manifest.json>)：完整输入、工具、迁移和历史证据指纹。
- [verification-results.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/verification-results.json>)：实际编译命令、403 项回归、产物及三次落盘结果。
- [independent.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/landing/independent.json>)、[sequential.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/landing/sequential.json>)、[full.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/landing/full.json>)：实际参数数组、有效配置、文件哈希、进程和夹具证据。
- [closeout-check.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/closeout-check.json>)：旧证据、源码／产物、前端交接文件及监听核对。
- [delivery-ledger.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/delivery-ledger.json>)：本报告与以上记录、真实输出文件的路径／哈希索引，不复制原始认证材料。

## 6. 下一批完整重跑方案（待单独批准，尚未执行）

建议使用全新 r7，不复用 r2～r6 或本批 a1／a2：

| 目标 | 固定建议 |
| --- | --- |
| 新数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/pgdata` |
| 唯一监听 | `127.0.0.1:55449` |
| 主库／升级库 | `p3_m1_platform_release_r7`／`p3_m1_platform_upgrade_r7` |
| 初始化／迁移／运行角色 | `p3_r7_initializer`／`p3_r7_migrator`／`p3_r7_app` |
| 新输入／产物目录 | `r7/snapshot/source`／`r7/build/a1`（均位于上述 r7 根目录内） |
| 阶段报告 | `r7/build/a1/reports/independent`、`sequential`、`full` |
| 脱敏记录 | `docs/releases/platform-db-acceptance-20260911/r7` |

本轮只读确认 r7 目标根目录及交付目录尚不存在；未创建目录或集群。下一批启动前仍须重新检查，不能沿用现在的端口空闲观察。

批准后的顺序建议：

1. **测试层 r7 绑定与重新封存编译**：只调整目标目录、数据库／角色名及编排产物绑定。保留现行 63 个业务方法和已说明的 7 处历史变化；复核权限、迁移与方法指纹。沿用本批 POM 生成器和共享执行器，生成同源码根目录的专用 POM，将其哈希加入新输入清单。无数据库回归（当前 403 项）及三次真实落盘须在新绑定产物上通过后才允许启动数据库；新目标导致计数调整时先列明实际清单，不能减少覆盖。
2. **新空集群**：固定 PostgreSQL 18.6、随机凭据、SCRAM；只用源码公共参考数据和登记合成夹具。核验目录、端口、PID、可执行文件、集群号、监听和 SQL 身份。没有生产或旧账号复制，也没有 trust 认证。
3. **迁移与权限**：主库完成既定至 V4_90 的空库准备／迁移；增量库至 V4_88、V4_89、V4_90 分步验证。继续核对基线角色、迁移数量、校验和、约束、哨兵精确标识、事务原子性和重复迁移零变更。专用准备器管理迁移，业务测试不自行迁移。
4. **同一份新 r7 产物顺序执行三阶段**：
   - independent：目标 MFA 事务方法 1 次。
   - sequential：前序 41 项后再执行同一目标，共 42 次。
   - full：完整 8 套件、63 项，单独出最终结果。
   106 是累计执行次数，63 是完整回归的方法数；不能将前两阶段或历史结果补入 full。
5. **逐套件归属及收尾**：同一共享 Maven 函数核对真实进程、指定 XML／stdout、方法清单、首因和报告保护；再按登记夹具核验收尾、不可变审计／公共角色、目标有效席位、挑战状态、准确异常和 MFA 持久化结果。比较目标状态，不要求保留的历史记录总量相同；未知残留即停，不自动认领或删除。
6. **失败即停、正常关闭**：任一门禁／阶段失败停止后续阶段；身份或指纹不符、目录存在、端口冲突时停止，不换目标、覆盖、repair、clean、放宽断言或改产品逻辑。成功／失败均走独立限时、核实身份的正常关闭，确认监听、进程和 PID 文件消失并保留脱敏证据。

**“同一份产物”指下一批 r7 绑定后一次封存编译得到的产物在三个数据库阶段之间保持完全一致。** 本批 a2 的 Java 隔离目标仍为 r6，因此不能把 a2 class 直接用于 r7，也不能连接旧 r6 库冒充新验收；下一批必须先绑定、封存和重新编译。报告修复的同一调用链与配置生成方式继续沿用，改变时需先说明并重新验证。

下一批所需批准范围为上述测试层目标适配、封存编译、全新 r7 环境、迁移、1／42／63 真实验证及正常关闭。仍不含前端／HTTP 服务、ERP 开发、真实账号／数据库、其他服务操作、提交合并或生产开放。

本批停止点已达到：工具修复和无数据库真实落盘验证已交付，完整实库重跑待批准。**仍非发布候选版本，不可上线。**

