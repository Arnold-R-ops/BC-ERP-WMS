# 平台测试夹具隔离与 MFA 分支断言修复交付

日期：2026-09-12。范围仅为测试夹具、前置检查、准确异常断言及无数据库验证；不是实库验收或发布候选版本。

## 1. 结论

**a2 新源码编译及 285 项无数据库测试通过。数据库启动、连接、迁移、业务测试均为 0。**

- 新建测试归属工具已接入原 8 套件；合成用户、邀请及租户根记录登记归属，不以全表删除恢复环境。
- 目标用例核验席位、挑战、账号前置条件；异常要求收紧为准确 MFA 凭据失败类型和 AUTH_FAILED。
- 原失败次数达到 5、锁定期限、挑战次数、邀请未创建及审计调用断言保留。
- 产品源码、94 个迁移 SQL 未修改；新编译的产品 classes/SQL 与 r5 差异为 0。
- **不能再称 63 个方法正文全部未变**：7 个正文变化，其中 1 个加强前置及异常断言，6 个仅加入归属登记；另 56 个正文未变。8 套件、63 方法名称、覆盖目的保留。
- r5 原 FAILED、41 通过/1 失败/21 未执行和正常关闭记录原样保留。**r5 实际 BusinessException 错误码没有留存，仍未知**；本轮没有倒推为已确认，也没有证明真实事务行为通过。
- 本轮到报告停止；没有启动后续 r6 环境，没有产品修复、ERP 开发、提交、合并、推送或上线。

结果：[verification-results.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-fixture-repair-20260912/a2/verification-results.json>)；收尾：[closeout-check.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-fixture-repair-20260912/a2/closeout-check.json>)。

## 2. 实现细节与安全边界

### 调用前与准确分支断言

新增 PlatformMfaFailureFixture，调用 invitation creation 之前明确读取并要求：

| 项目 | 期望 |
| --- | --- |
| 有效超级管理员绑定数（沿用产品计数口径） | 1 |
| 有效待接受超级管理员邀请数 | 0 |
| 所有有效待接受平台邀请数 | 0 |
| 目标账号存在、启用、MFA 启用、security_version 合法且未锁定 | 恰好 1 个匹配 |
| 目标挑战存在、归属当前账号、purpose=ADMIN_INVITE、未消费且未过期 | 恰好 1 个匹配 |
| 账号失败次数 / 挑战失败次数 | 4 / 4 |

前置不符即停止调用，不能把席位上限等较早拒绝当作预期凭据失败。

捕获调用结果后，要求准确类 PlatformAdminInvitationMfaCredentialException、错误码 AUTH_FAILED、reason=MFA_VERIFICATION_FAILED，且异常内部账号/挑战归属一致。通用 BusinessException 即使同为 AUTH_FAILED，也不能通过。

只输出固定前缀的安全诊断：

- MFA_FIXTURE_PRECONDITIONS：必要计数与匹配状态。
- MFA_FIXTURE_EXCEPTION：异常类名、ErrorKeys 中允许公开的错误码；非标准错误码显示 UNRECOGNIZED_ERROR_KEY。
- 不序列化 Throwable、完整请求、params、密码、验证码、JWT 或挑战哈希；哈希仅在进程内做布尔一致性判断。

失败之后原有数据断言不变。没有提高超级管理员上限、改变产品校验顺序、改为普通管理员邀请，或改动 MFA 事务/计数逻辑。

### 合成数据归属与收尾

新增 PlatformFixtureScope；每个 scope 使用套件、用例/夹具标签及唯一 UUID，输出安全的用户 ID、邀请 ID、租户 ID 归属记录。会话、MFA 挑战、角色绑定、授权和平台审计以所属用户为根；公司资源/连接/任务等以登记租户为根，保留既有外键。

- 开始时拒绝未知有效平台账号或有效待接受邀请。
- 创建账号后立即登记 ID 与精确邮箱身份；不能认领开始前已经存在的账号。租户也不能认领开始前的 ID。
- 邀请发出前登记精确的“发起人 ID＋目标邮箱”意图；收尾时按该组合核验邀请 ID，已接受账号还须与邀请 FK 和邮箱一致，不能按角色或邮箱域批量认领。
- 收尾检测本 scope 新增用户、邀请、租户是否全部登记；**未知新行即使已停用，也失败**。未知有效行、身份变化、重复邀请或错误外键归属也明确失败，不能自动修复。
- 所有归属核验先于收尾写入，并在同一明确事务中执行。只停用登记账号、提升其测试 security_version、撤销其会话并消费其未消费 MFA 挑战。
- 只撤销登记的未接受邀请；已接受邀请不改成撤销。仅删除精确 invitation_id＋normalized_email 对应的可变邮箱预留索引，邀请、激活、公共角色和不可变审计不删除。
- 一般保留账号和租户根记录，避免连带删除审计/外键。原角色竞态夹具本来就精确删除本例目标账号、角色关联、授权和租户，该既有收尾保留；没有新增公共角色或审计删除。
- 先前已失效的记录作为不可认领、不可变动的只读基线保留。该设计用于**全新合成集群、串行执行**，不是生产清理或多任务共用数据库清理工具。后续执行入口需核对首轮身份表为空，并将前序 scope 登记与阶段基线对账，不能把未知历史残留当作合法夹具。

真实事务提交、数据库约束和八套件连续执行下的收尾效果，仍须第 6 节实库验证；内存回归不替代实库证明。

### 八套件归属清单

| 套件 | 登记与保留范围 |
| --- | --- |
| TenantSecurityPostgresTest | 套件共用的两个合成租户；探针记录依 tenant 根归属，租户保留 |
| TenantSecurityCommandsPostgresTest | 每例租户、两名合成管理员；挑战、请求及审计依根归属，账号收尾停用 |
| PlatformSessionPostgresTest | 每例新建平台账号及其会话/MFA 挑战；测试后停用，审计保留 |
| PlatformInvitationSessionPostgresTest | 继承账号 scope；登记邀请意图、邀请及通过该邀请生成的账号，保留邀请/审计 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 登记目标管理员及邀请意图；挑战以管理员 FK 归属；读取公共角色，不再自动补建或删除 |
| PlatformCompanyDataServiceIntegrationTest | 平台读取账号、合成租户；授权、仓库及导出依根归属，既有临时导出文件清理保留 |
| TenantSecuritySpringPostgresTest | 新建租户、平台测试账号及现有交接兼容测试返回的合成租户 ID；只加登记，不改 ERP 行为 |
| PlatformAccessGrantRoleRaceIntegrationTest | 目标用户、租户，公共角色只读核验；保留原精确自有数据收尾 |

## 3. 文件与方法变更

新增：

- src/test/java/com/wms/platformverification/PlatformFixtureScope.java
- 同目录 PlatformFixtureScopeTest.java
- src/test/java/com/wms/system/platform/service/PlatformMfaFailureFixture.java
- 同目录 PlatformMfaFailureFixtureTest.java
- src/test/scripts/platform-fixture-isolation.test.mjs
- src/test/scripts/platform-fixture-repair-verification.mjs

原 8 套件测试文件的 setup/helper/teardown 接入归属工具；没有修改 src/main、pom.xml、公共数据源默认值、迁移或前端文件。

方法正文变化为：

1. credentialFailureSurvivesRollbackOfInvitationCreationTransaction：增加前置检查与准确异常断言。
2. realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor：只加账号登记。
3. alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate：只加账号登记。
4. provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode：只加返回租户 ID 登记。
5. platformHttpTwoPersonRecoveryCommitsWithRealAuditRepository：只加账号登记。
6. failedConnectionStaysDisabledAndGapNeedsCurrentVerifiedEvidence：只加账号登记。
7. fullApplicationWiresAndRealJpaAdmissionRejectsOldWork：只加租户登记。

源码契约逐个比较原 63 个方法：除精确允许的上述插入/替换外，正文必须一致；异常分支之后的原业务断言逐字保留。

目标方法正文 SHA-256：

- r5：`6e6a30a47525b99d61b7de5c7f6f9420a321f5e5b05d8abfc7624496850d25fe`
- 本轮：`cda3e5e7c3eb45246a4be13eceda1cf59eb4d8f91ec67fb85ad1585df40305fe`

全部七项前后指纹列于 manifest 的 methodBodyChanges。新 manifest 使用 businessTests[].tests 表示当前期望方法，明确 original63MethodBodiesUnchanged=false；不沿用“unchangedTests”名称作全部正文未变的暗示。后续批次工具需适配新清单，不可直接沿用 r5 封存输入。

## 4. 新封存与实际无数据库结果

### 最终 a2 输入与产物

| 项目 | 实际结果 |
| --- | --- |
| 分支 / HEAD | 2-1G / 9c12f6f893105632c4de24ad8bb4f6f43092f84e |
| 输入 ID | `b537eb86b5469a8e128696b6fe0e46394e5c8cd2b59c3d94300e9f8ee87b686a` |
| 封存源码 | `D:/ERP_WMS/BC warehouse/2G/target/platform-fixture-repair-20260912/a2/source` |
| 新构建目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-fixture-repair-20260912/a2/backend` |
| 文件数 | 930 份源码/测试/工具/资源；3337 份依赖；90 份 Maven 分发；94 份 SQL |
| 工具链 | Node 24.12.0、Java 17.0.17、Maven 3.9.9；Maven 离线使用冻结依赖 |
| 实际编译 | 580 份主源码＋197 份测试源码，BUILD SUCCESS；34.959 秒 |
| JAR SHA-256 | `505517399ea0181736f6ea97caf034605a3ab03376e1287d8133f58f9ba74972` |
| 1188 条 class/SQL 聚合 SHA-256 | `da868b76323f735bd134b96e944255558c8cfca4982c65c045981eb2389351ed` |

本次没有执行 pnpm、前端类型检查或整包构建，没有改锁文件或全局 PATH。编译与测试使用明确 false 的数据库/迁移授权属性，并清除专用进程继承的数据库与 Java/Maven 注入环境变量。执行器只有 seal/build/verify，不提供数据库启动入口。

新 JAR 和全部编译 class/SQL 已核验；19 个新增/变化产物均位于 test-classes，产品 classes/SQL 差异为 0。不复用旧 classes 证明新版本通过。

### 最终 a2 成绩（不累加 a1 或 r5）

| 无数据库检查 | 执行/通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| 既有隔离防护 | 39 / 39 | 0 | 0 | 0 |
| 既有角色夹具契约 | 7 / 7 | 0 | 0 | 0 |
| 新归属、收尾及 mock JDBC 契约 | 29 / 29 | 0 | 0 | 0 |
| 新前置状态、准确异常及诊断脱敏 | 22 / 22 | 0 | 0 | 0 |
| 源码/八套件接入/正文变化契约 | 24 / 24 | 0 | — | 0 |
| 进程生命周期 | 32 / 32 | 0 | — | 0 |
| 升级哨兵契约 | 65 / 65 | 0 | — | 0 |
| XML 解析器及既有实际入口/汇总 | 67 / 67 | 0 | — | 0 |
| **合计** | **285 / 285** | **0** | **Java 0** | **0** |

Node 取消数均为 0；另有 2 个新脚本语法检查通过。四份 Java XML 实际 testcase 数为 39、7、29、22，结合执行进程退出 0 校验；Node 实际 TAP 数量与预期一致。

源码：[input-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-fixture-repair-20260912/a2/input-manifest.json>)。产品、八套件业务方法与数据库测试的覆盖边界不能由这些无数据库成绩替代。

### a1 失败记录保留

a1 输入 ID：`2dcc0a5893905529461ac9a04624c8750355c2d8ff5a478e68e9265363f3c487`。

a1 编译成功；Java 实际 97 项：96 通过、1 失败、0 错误、0 跳过。失败是新内存契约测试使用 Map<Long, …>.containsKey(9)，其参数装箱为 Integer，错误判断 Long 键是否存在；不是删除了记录，也不是 MFA 产品问题。

修正为准确断言 Set.of(9L) 与空收尾记录；没有削弱“不能认领已有用户、不能删除它”的检查。a1 封存、产物、原 XML 与 build-failure.json 保留原 FAILED；后续 Node 阶段未在 a1 执行，不记通过。a2 在独立新目录重新封存、完整重编译并重跑全部检查，不拼接 a1 结果。

## 5. 原记录、安全限制与未验证项

- r2/r3/r4/r5 旧环境、源码、失败报告及关闭证据在封存和构建结束时按指纹/清单核对；a1 失败批次也被 a2 指纹保护，没有覆盖或删除。
- 本次数据库方法执行次数为 **0**。r5 具体运行时错误码仍为未知，席位干扰仍是待实库验证的推断。
- 无数据库内存事务模拟和 SQL/mock 检查，不能证明 PostgreSQL 的真实事务、并发、外键和审计保留已通过。
- 现有测试层绑定仍是 r5 标签；本轮未使用它连接数据库。新实库批次必须重新绑定 r6、封存、编译，禁止回连 r5 或混用旧产物。
- 结束只读观察：2026-09-12T04:55:55.9056567Z，55449 监听数 0；r2/r3/r4/r5 postmaster.pid 均不存在，提议的 r6 目录不存在。本轮没有发出服务启动、停止、重启或 SQL 连接命令。
- 报告和新增 evidence 不保存原始认证材料、原始 XML 环境属性或完整请求。
- 地址测试 TS2769 仍在范围外交接；地址测试与 pnpm 锁文件指纹未变。未重跑前端检查，不宣称阻塞解决。
- 邀请完整人工浏览器激活仍待验；历史 MFA、恢复码、退出人工成绩保留，不计作本轮成绩。
- 恢复核对最终业务边界仍待确认；没有扩展完整自动核对。
- 仍非发布候选版本，不具备上线授权。

## 6. 待批准的 r6 实库验证方案（本轮未执行）

### 固定新目标建议

| 项目 | 建议值 |
| --- | --- |
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r6/pgdata` |
| 监听 | 仅 127.0.0.1:55449 |
| 主库 | p3_m1_platform_release_r6 |
| 升级库 | p3_m1_platform_upgrade_r6 |
| 角色 | p3_r6_initializer、p3_r6_migrator、p3_r6_app |
| 数据 | 全新空库＋公共参考数据＋本批登记的合成夹具；随机凭据、SCRAM |
| 产物 | 获批后仅在测试层绑定 r6 与阶段报告目录，重新封存/编译，不直接使用本次 r5 绑定产物 |

r6 目录已存在、55449 冲突、角色/集群/SQL 身份或指纹不符均停止；不换目标、不回退、不 repair/clean，不接触旧 r2～r5。新编排需读取本次修订的方法指纹清单，不能再次要求原 63 个正文完全未改。

### 执行与成绩分离

1. 新封存编译及全部无数据库门禁通过后，才初始化新集群；核验 PID/启动时间/目录/集群号/监听/SQL 身份。
2. 主库空库到 V4_90；升级库 V4_88→V4_89→V4_90。继续核对 94 份迁移资源、实际 8/6/1/1 新迁移、历史/校验和、重复执行 0、哨兵及最小运行权限。首轮主库平台账号/邀请表应为空，公共角色精确存在。
3. **独立阶段：1 个方法。** 主库尚未执行任何业务套件时，独立执行目标 MFA 事务用例；记录允许公开的前置计数、准确异常类型/码、全部原断言以及收尾后状态。
4. **顺序阶段：42 个方法。** 在同一合成主库依次执行前四套件 4＋6＋27＋4＝41 项，然后执行目标用例 1 项。比较独立阶段和前序阶段之后的前置状态与业务结果一致，不能只看是否抛异常。
5. 以上均通过后，**完整阶段：重新执行原 8 套件、63 个方法**。首两个阶段不代替这 63 项，也不与 r5 的 41 项成绩拼接。
6. 三阶段各用全新 Surefire XML/日志目录；使用同一份新封存产物。计划合计 106 次数据库方法执行，但发布回归成绩单仍单独列“完整阶段 63 项”；任一失败即停止后续阶段。
7. 编排入口需逐阶段收集 scope 归属日志，核对初始空身份表与后续累计已登记根 ID；未知残留、重复/缺失归属记录、未知有效账号或邀请均失败，不自动认领/删除。只记录 ID、计数与安全结果，不归档认证材料。完整业务断言之外，还检查账号/邀请收尾、原审计行保留与公共角色不变。
8. 同时核对进程状态、XML 实际元素、完整预期方法清单；分别列通过、失败、错误、跳过、未执行、无法核实。独立、顺序、完整三个结果不可互相替代。
9. 成功或失败均按核实身份限时正常关闭，检查专用监听和进程消失，保留脱敏证据。出现产品缺陷先报告，不擅自修复。

该方案需要单独批准测试层 r6 目标/编排适配、环境创建、迁移、三个验证阶段及正常关闭；没有隐含当前已授权启动。仍不启动前端/HTTP 服务，不操作其他服务、真实账号/租户/数据库，不提交合并或上线。

