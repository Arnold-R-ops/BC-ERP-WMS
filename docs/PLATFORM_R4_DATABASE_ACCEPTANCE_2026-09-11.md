# r4 平台后端隔离数据库验收结果

日期：2026-09-11。结论：**整批 FAILED；完整迁移与升级哨兵通过，实际 37 个数据库业务方法通过、26 个未执行；专用集群已正常关闭。尚非发布候选版本，不可上线。**

失败发生在第三个业务套件执行成功之后：专用工具对 JUnit XML 的正则解析漏识别方法，误报覆盖不足并停止后续套件。不是迁移失败、哨兵失败或已发现的产品断言失败。原始失败结果及 XML 不改写；另附独立 XML 复核证据，不把整批改记为通过。

## 1. 执行范围与改动归属

依据用户完整 r4 授权，完成测试目标绑定、重新封存、新目录编译、无数据库回归、新集群初始化/启动、迁移、业务测试及失败后正常关闭。本批没有修改产品安全逻辑、迁移 SQL、公共默认数据源、POM、依赖锁文件或全局工具配置。

相对已确认的哨兵修复 a2，实际修改仅为：

- `src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java`：r4 角色、控制 schema 及目标说明。
- `src/test/java/com/wms/platformverification/PlatformR2Isolation.java`：固定 r4 目录、库名、角色及目标校验。
- `src/test/java/com/wms/platformverification/PlatformR2IsolationTest.java`：同步 r4 合成配置，增加旧 r3 URL 拒绝用例；旧 r2 拒绝检查保留。
- `src/test/java/com/wms/platformverification/PlatformR2SpringInitializer.java`：专用导出产物路径绑定 r4。
- `src/test/scripts/platform-upgrade-sentinels.test.mjs`：静态契约读取本批 r4 执行器。

新增两个专用工具 `src/test/scripts/platform-r4-backend-snapshot.mjs`、`src/test/scripts/platform-r4-database-batch.mjs`，以及本批交付记录。业务 63 个方法正文指纹保持不变；哨兵生成/核验模块和产品 SQL 未修改。

保留 `PlatformR2*` 类名、`p3.r2.*` 参数名和 `P3_R2_*` 进程内变量名，避免无关重命名；实际连接目标只允许 r4，不构成向 r2/r3 或日常数据库回退。未开展租户 ERP 开发、其他任务修复或跨任务整合。未提交、合并、推送或开放生产。

## 2. 固定输入与新产物

| 项目 | 本批实际值 |
|---|---|
| 分支 / HEAD | `2-1G` / `9c12f6f893105632c4de24ad8bb4f6f43092f84e`，未提交 |
| 上批哨兵修复输入 | `60cdea547dab3eef4df871d593f3d4a1892b5fac486e990cdc2f6768bde60e73` |
| r4 输入标识 | `2d7a96127ca86b446d36a2d91aff0ef89da9a7ed755fcc59ac2e7be8819f2ac8` |
| 源码/测试/资源/工具 | 917 文件；源码组指纹 `aeb199b2de80165b96f73f65c775ab95a93596787914c2d99d26c6c67f2a3f8d` |
| 专用冻结依赖副本 | 3337 文件；136 个测试类路径 JAR；Maven 分发 90 文件 |
| 工具链 | Node 24.12.0、Java 17.0.17、Maven 3.9.9；PostgreSQL 18.6 |
| JAR SHA-256 | `c3d901e5977126433a34eac26748e95c7ce6456454ad01fe82200d347f3160f1` |
| 1178 个 class/SQL 聚合指纹 | `74ac8a25e6b352bf24cf0c8c671480561af41624e5e5872f16aac1ff1bc843cc` |

完整路径：

- 源码：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r4/snapshot/source`
- 依赖：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r4/snapshot/maven-repository`
- 新构建：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r4/build/a1`

从冻结源码重新编译 580 个主源码、193 个测试源码并打包，BUILD SUCCESS。没有复用旧 classes；相对哨兵修复 a2，产品 class/迁移 SQL 差异为 0，测试 class 差异为 10 个，包括目标常量内联造成的变化。详细逐文件指纹及差异见输入/构建清单。

执行后复核：冻结输入、工具、依赖和历史受保护文件一致；当前工作区对应的 917 个输入文件无漂移；1178 个编译文件的清单及哈希无漂移；JAR 与构建记录和数据库批次记录一致。本输入仅为后端隔离验收快照，**不是正式 R2 全源码快照**。

## 3. 启动前实际检查

| 检查 | 本批结果 |
|---|---|
| 隔离防护 Java 测试 | 38/38，失败/错误/跳过均 0 |
| 基线角色夹具无数据库 Java 测试 | 7/7，失败/错误/跳过均 0 |
| 生命周期 Node 回归 | 32/32，失败/取消/跳过均 0 |
| 哨兵与迁移静态契约 Node 回归 | 65/65，失败/取消/跳过均 0 |
| 六个专用脚本语法检查 | 全部通过 |
| 原业务方法正文核对 | 8 套件、63 个方法不变 |

Java 两个无数据库套件另经安全 XML 读取复核，实际 testcase 元素分别为 38、7，失败/错误/跳过元素均为 0。这些检查与数据库业务 63 个方法分别计数，不相加冒充数据库执行。65 项契约回归也不替代真实数据库约束/事务测试。

## 4. 新环境与身份核验

| 项目 | 实际核验值 |
|---|---|
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r4/pgdata` |
| 唯一监听 | `127.0.0.1:55449` |
| 主库 / 增量库 | `p3_m1_platform_release_r4` / `p3_m1_platform_upgrade_r4` |
| 专用角色 | `p3_r4_initializer` / `p3_r4_migrator` / `p3_r4_app` |
| PostgreSQL PID / 启动 epoch | `32068` / `1789130421` |
| 集群号 | `7684256638752159388` |
| 启动判定 | `READY_VERIFIED`，启动器/执行器成功 |

封存及初始化前目录不存在、端口无占用；没有覆盖既有环境。使用随机专用凭据、SCRAM 初始化，只载入源码公共参考数据和合成夹具。新集群的维护库 `postgres` 仅用于角色/库初始化及身份检查。

核验包括 OS 进程 PID、启动时间、可执行文件、命令行目录、PID 文件、集群号、监听归属，以及 SQL 返回的目录、地址、端口、数据库和角色；不以端口通或启动器退出码单独判定成功。没有启动前端或 HTTP 服务，也没有连接真实数据库。

运行角色独立权限查询通过：不是超级用户、无 BYPASSRLS；无 Flyway 历史 INSERT/UPDATE/DELETE 权限、无专用 ready 标记 UPDATE 权限；不拥有 public/control schema 下的关系。该查询不能替代尚未执行的委派授权角色竞态套件。

## 5. 实际迁移与哨兵结果

冻结迁移输入为 94 个 SQL（93 个 V、1 个 B），文件、版本及校验和均保持既定清单。新空库路线实际执行 B4_71、V4_72、V4_85–V4_90 共 8 步，**不是执行全部 94 个 SQL，也不是生产迁移方案**。

| 路线 / 阶段 | 新应用迁移数 | 历史行数 | 校验 / 重复执行 | 结果 |
|---|---:|---:|---|---|
| 主库空库至 V4_90 | 8 | 8 | 94 项校验成功；重复 migrate 0 步 | 通过 |
| 增量库空库至 V4_88 | 6 | 6 | 94 项校验成功；重复 migrate 0 步 | 通过 |
| 增量库 V4_88 → V4_89 | 1 | 7 | 94 项校验成功；重复 migrate 0 步 | 通过 |
| 增量库 V4_89 → V4_90 | 1 | 8 | 94 项校验成功；重复 migrate 0 步 | 通过 |

最终两库迁移历史均为以下顺序，success 均为 true：

| 版本 | Flyway 校验和 |
|---|---:|
| B4_71 | -1902150811 |
| V4_72 | -1014316113 |
| V4_85 | 1952011876 |
| V4_86 | 1901493228 |
| V4_87 | 1941766358 |
| V4_88 | -1639265665 |
| V4_89 | 178422383 |
| V4_90 | 618333881 |

本次升级哨兵标识 `aff1f455-bb2e-47db-837c-d8cedfb01180`，不是认证凭据。真实执行的结果：

- V4_88 后，以明确事务准备禁用合成用户与合法 `WRITE` 审计；唯一标识置于资源字段和 detail_json，不新增动作例外。
- 按精确用户/资源标识读取，升级前后各 1 个用户、1 条本批审计，字段保持一致；不依赖全表只有一条记录。
- V4_89 后，以明确事务准备唯一 UUID 旧会话；按本次用户、审计、会话的确切标识核验 1/1/1 条。
- V4_90 后，旧会话原字段保持不变，新增 `browser_session_hash` 保持 NULL；没有回填成可重新使用的浏览器会话。
- 4 个迁移阶段及所有哨兵准备/核验阶段均单独记录为 SUCCEEDED，无部分夹具准备失败。

迁移中的约束 DDL 实际创建成功，合法哨兵被真实数据库接受；字段/非空/外键/唯一/时间规则的静态契约检查与上述真实迁移结果分开记录。不将此表述成所有业务拒绝边界均已完成实库覆盖；完整业务套件仍有 26 项未执行。

Flyway 9.22.3 对 PostgreSQL 18.6 输出版本兼容警告：其所声明已测试支持版本到 PostgreSQL 15。实际迁移成功不等于该组合生产兼容性已验收；本批未升级依赖或调整生产方案。

## 6. 数据库业务测试实际执行

以下仅统计本 r4 新编译产物在本 r4 主库上的实际测试，不累计历史结果。

| 套件（按执行顺序） | 计划 | 通过 | 失败 | 错误 | 跳过 | 未执行 |
|---|---:|---:|---:|---:|---:|---:|
| TenantSecurityPostgresTest | 4 | 4 | 0 | 0 | 0 | 0 |
| TenantSecurityCommandsPostgresTest | 6 | 6 | 0 | 0 | 0 | 0 |
| PlatformSessionPostgresTest | 27 | 27 | 0 | 0 | 0 | 0 |
| PlatformInvitationSessionPostgresTest#invitation* | 4 | 0 | 0 | 0 | 0 | 4 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 | 0 | 0 | 0 | 0 | 1 |
| PlatformCompanyDataServiceIntegrationTest | 6 | 0 | 0 | 0 | 0 | 6 |
| TenantSecuritySpringPostgresTest | 14 | 0 | 0 | 0 | 0 | 14 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 | 0 | 0 | 0 | 0 | 1 |
| **合计** | **63** | **37** | **0** | **0** | **0** | **26** |

三个已执行套件的 Maven 退出码均为 0，无超时。第三个日志明确记录 `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` 及 BUILD SUCCESS。后三列为 0 不代表后五套件通过；它们没有测试 XML，明确标为未执行。

已实际覆盖的内容包括基础暂停状态/会话版本隔离、写事务与暂停并发、双人恢复原子性与审计回滚，以及平台后端会话期限/活动并发/撤销/浏览器分组/相关 MFA 原子边界等。不能据此宣布邀请、受控导出、Spring 集成联动、委派授权角色竞态整批已通过。

最新普通退出规则保持：**同浏览器、同账号的会话一起退出；不同登录的空闲计时独立，不是跨标签自动共享登录或计时。** 会话套件保留内部单会话撤销测试，不能将其方法名误读成当前页面普通退出规则。

## 7. 失败原因与独立复核

原失败字段：`failedStage=PlatformSessionPostgresTest`，`failure=Test execution/coverage mismatch: PlatformSessionPostgresTest`。原 `database-result.json` 的批次及业务阶段仍保留 FAILED。

定位到 r4 专用执行器第 84 行：用正则读取 `<testcase>`。该表达式的贪婪属性匹配在遇到自闭合元素时可以吃掉结尾斜线，再跨越后续 testcase，直到后面的闭合标签。实际 XML 混用了自闭合 testcase 与含 system-out 的完整元素，因此 **27 个元素只识别到 9 个**，触发缺失方法保护并关闭数据库。

诊断后仅进行只读证据核对：采用 .NET XML 解析器，禁止 DTD、禁用外部解析器；逐个比较 testcase 元素、套件头部计数、failure/error/skipped 子元素、重复/缺失/多余方法，以及冻结清单中的预期方法名。结果三个套件分别完整识别 4、6、27 个方法，全部一一对应，无重复、缺失或额外方法。

原始 XML SHA-256：

- TenantSecurityPostgresTest：`62658fa349f9697aa2faedf275080e14a628b44dcd87659cb6169de1bd3e422c`
- TenantSecurityCommandsPostgresTest：`52841ca5209bbd5722d71ae6fe3593ac8dbd8fe267ec3ca2388064148befc04e`
- PlatformSessionPostgresTest：`90c78426d81f3bb59f0eedc3791d23cf3706d2ddfd14c23bf5802b4369135c59`

独立复核不是再次执行测试，也不是修改原批次判定。它说明 37 项实际通过和执行器失败可同时成立。**本批未发现产品缺陷；仍有未执行的产品验证，不能断言产品不存在缺陷。**

## 8. 正常关闭与证据保护

数据库批次 UTC 12:40:18 开始，UTC 12:42:38 完成收尾（北京时间 20:40:18–20:42:38）。失败后进入既定独立、有时限的关闭流程，再核验本 r4 PID、启动时间、目录、可执行文件、集群号及监听，使用 PostgreSQL 正常 fast shutdown；未采用强杀、按端口杀进程或任意目录停止。

- 关闭命令 exit 0，输出 server stopped，`stop.status=STOPPED`、`completed=true`。
- 批次检查：55449 无监听，r4 postmaster.pid 不存在，原有监听一致。
- UTC 12:49:40 另行只读核对：PID 32068、本批后代/目录匹配进程均不存在；55449 无监听；r2/r3 PID 文件仍不存在。
- 5432 仍为原有 PID 8980（IPv4/IPv6）；没有对其操作。8080、5173、55439、18091、15173 观察时无监听，未启动它们。
- 原始初始化临时密码文件已删除；随机凭据不写入交付报告。保留 r4 数据目录，不覆盖或删除历史 r2/r3 目录。
- r2/r3 数据目录清单与文件哈希、快照、构建、旧失败报告和关闭证据按封存清单逐项复核，无漂移。

47 个本批公共日志/XML/JSON 的 JWT 模式扫描为 0；此模式扫描不是对所有可能秘密的完备证明。交付只记录脱敏元数据、方法名、状态和哈希，不归档密码、原始 JWT、MFA 密钥或恢复码；私有 PostgreSQL 原始日志不复制进交付目录。

## 9. 交付证据入口

所有链接指向本批记录；原失败结果保留原样。

- [输入、工具、依赖、迁移及原业务方法指纹](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/backend-input-manifest.json>)
- [新编译产物及无数据库测试](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/backend-build.json>)
- [原始批次结果：FAILED，含迁移/哨兵/关闭记录](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/database-result.json>)
- [启动身份核验](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/environment-started.json>)
- [独立 XML 方法及计数复核](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/independent-test-reconciliation.json>)
- [独立进程/监听关闭核验](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/independent-closure-check.json>)
- [结束指纹、完整覆盖表与证据文件哈希](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r4/closeout-evidence.json>)

## 10. 未覆盖项、停止点与建议下一批

本批停止于失败结果、独立复核及正常关闭证据交付；没有在失败后修改解析器来把结果改成通过，也没有重新启动 r4 或创建下一批集群。

建议下一批先单独批准**测试结果解析工具修复及无数据库契约回归**：

1. 只改专用验收工具，使用可靠 XML 结构解析；禁止外部实体/DTD，不改产品逻辑、公共配置、63 个业务方法或覆盖要求。
2. 用脱敏最小夹具覆盖自闭合与完整元素混合、system-out/CDATA、属性顺序/换行/实体，以及失败、错误、跳过、重复/缺失/额外方法、头部与元素计数不符、缺失/损坏 XML。错误必须明确失败，不能只相信头部计数或 Maven exit 0。
3. 区分业务测试失败、执行器/报告解析失败及未执行；保留本批 FAILED 原始证据，补充独立工具回归记录。
4. 修复交付后另行确认全新目录/身份、封存编译及完整 8 套件 63 方法重跑；不自动复用 r4。单独复核的 37 项事实保留，但不能用它们拼接新批次为完整通过。

独立保留的发布阻塞与边界：

- 前端 `addressLength.audit.test.tsx` 的 TS2769 仍交由对应任务处理；本批未修改/删除/排除测试、未运行正式前端类型检查或整包构建，不宣布阻塞已消除。结束时该文件哈希 `f7c75aaa8cb8896aea0d6bace0f59b7ba672b51d18111eecf4340603ad3ed44c`，pnpm 锁文件哈希 `7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e`，与既定记录一致。
- 邀请激活完整人工浏览器流程仍待发布候选版本验收；既有 MFA、恢复码、退出人工成果不被本批重复或替代。
- 恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界未确认；未扩展完整自动核对。
- 真实数据库兼容性、统一整合、正式配置、备份恢复/迁移演练、最终人工验收及上线审批未完成或未在本批执行。

**无论已通过的迁移或部分数据库业务测试如何，完整发布候选版本仍未成立，不可上线。**
