# 平台专用结果解析工具修复交付

日期：2026-09-11。结论：**工具修复、实际入口接线和无数据库回归完成；本轮数据库业务测试执行 0 项。r4 原始 FAILED 结果及证据保持不变。**

本批只修复测试工具，不修改产品逻辑、迁移、原 63 个业务方法、覆盖要求、公共安全逻辑或默认数据源。没有启动/连接数据库，没有复用或操作 r2/r3/r4 环境，没有创建下一批集群；没有租户 ERP 开发、服务操作、提交/合并/推送或上线。

## 1. 实际修复与接入

相对 r4 封存输入，只修改一个已有文件：

- [实际批次入口 platform-r4-database-batch.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-r4-database-batch.mjs>)：移除正则 testcase 拆分，接入结构化读取、单套件判定和最终汇总；构建前置验证增加解析器回归门槛。入口仅在直接执行 build/database 时初始化，导入其结果执行/汇总函数不检查或操作数据库。原封存路径、目标身份、授权、现有目录/端口拒绝及正常关闭保护仍保留。

新增五个测试工具/夹具文件：

| 文件 | 作用 |
|---|---|
| `src/test/scripts/platform-junit-xml.ps1` | 复用独立复核采用的 .NET XML 解析方式；XML 通过 stdin JSON 输入，结构化安全字段通过 JSON 输出 |
| `src/test/scripts/platform-test-results.mjs` | 安全解析包装、进程证据规范化、方法覆盖核对、执行循环及最终汇总 |
| `src/test/scripts/platform-test-results.test.mjs` | 67 项无数据库回归，包含实际批次导出函数及最终汇总调用 |
| `src/test/scripts/fixtures/platform-junit/mixed.xml` | 4 个合成方法的最小混合元素夹具，无环境属性或认证材料 |
| `src/test/scripts/platform-result-parser-repair-verification.mjs` | 专用输入封存、全新编译、无数据库验证、旧证据只读回归与指纹保护 |

接线不是仅测试一个未使用的新解析器：

1. 实际构建入口用结构化解析读取 Java 防护测试报告，并要求解析器回归通过后才允许后续数据库门槛检查。
2. 实际数据库业务循环调用 `runPlatformBusinessAcceptance`，通过 `runSuitePlan` 使用同一解析/判定逻辑；保留固定八个 selector，其中邀请仍为 `#invitation*`，不扩大或减少原 63 方法。
3. 实际数据库 finally 中调用 `finalizePlatformBusinessResults`，从结构化证据重新计算各套件及汇总，不能把缓存状态或头部计数改成通过。
4. 本批直接导入上述实际入口的导出函数，以合成执行结果验证完整 8/63 循环及失败停止分支，并调用实际最终汇总函数。静态接线检查同时确认这些函数确实被数据库循环/finally 使用。

**这验证了无数据库的执行与汇总接线，不是新一轮实际数据库验收。** 工作区入口已修复；r4 目录中的旧封存执行器不改写。未来真实重跑仍需新的目标绑定、封存和授权。

## 2. XML 安全和判定规则

使用 `XmlReaderSettings.DtdProcessing=Prohibit`，XmlReader 与 XmlDocument 的外部解析器均为 null；限制输入/文档大小及解析进程时限。DTD、外部实体/外部 DTD、损坏 XML、非法计数、错误根元素/命名空间及非法 testcase 结构均失败，不尝试联网解析或恢复损坏报告。

PowerShell 通过 .NET 结构 getter 读取元素类型/名称，避免 XML 的同名属性遮蔽结构属性。输出只保留套件/方法名、实际结果、头部计数及哈希；不转发 properties、system-out/system-err 内容、异常明细或解析器原始异常消息。

成功必须同时满足：

- 进程 exitCode 为 0，已退出、无超时/中断/启动错误/信号退出，输出收集及终止已确认。
- 实际 testcase 结构、套件及 classname 正确；预期方法逐个出现且仅出现一次，无缺失、额外或重复方法。
- XML 头部四类计数与实际元素一致。
- 无业务失败、错误或意外跳过。Maven exit 0 或 XML 头部数量单独满足都不能通过。

| 状态 | 含义与统计 |
|---|---|
| `PASSED` | 进程、结构、完整方法覆盖和业务结果均通过 |
| `BUSINESS_TEST_FAILED` | 实际 testcase 有 failure/error；即使 exit 0 也失败 |
| `REPORT_PARSE_FAILED` | 已尝试执行但报告缺失、不可读、不安全或损坏；相关方法列为 unverified，不当作通过或确定未执行 |
| `REPORT_VALIDATION_FAILED` | 结构可读但方法清单、归属或头部计数不符；保留能核实的方法结果，整套件不能通过 |
| `EXECUTION_FAILED` | XML 可读也不能掩盖进程失败、超时或输出未完整确认 |
| `UNEXPECTED_SKIP` | 跳过单独统计，不能当作通过，也不合并成后续套件“未执行” |
| `NOT_EXECUTED` | 未进入该套件执行；忽略可能存在的旧报告，不将其充当本轮测试 |

报告错误与进程错误可同时记录；汇总中的两类错误标志不代表把同一业务方法重复计数。方法统计分别保留 passed、failures、errors、skipped、unexecuted、unverified；最终结果还区分 INCOMPLETE 和完全 NOT_EXECUTED。

## 3. 固定输入与产物

| 项目 | 实际记录 |
|---|---|
| 分支 / HEAD | `2-1G` / `9c12f6f893105632c4de24ad8bb4f6f43092f84e`，未提交 |
| 本批输入 ID | `93af3c2cf2cedd8461d674bb51f5136614f4bda190f430c207b8d8f25897c476` |
| 上批 r4 输入 ID | `2d7a96127ca86b446d36a2d91aff0ef89da9a7ed755fcc59ac2e7be8819f2ac8` |
| 源码/测试/资源/工具 | 922 文件；源码组 SHA-256 `4d5100de70e414cf1d0853fa1ef09e0332034cb69fbc03b73deedc694c17cc50` |
| 专用工具链 | Node 24.12.0、Java 17.0.17、Maven 3.9.9；Windows PowerShell/.NET XML 内置解析，无新增依赖 |
| 冻结依赖副本 | 3337 文件；Maven 分发副本 90 文件 |
| 全新编译 | 580 主源码 + 193 测试源码，BUILD SUCCESS |
| 新 JAR SHA-256 | `e46ac3380e0f8d9a68105e5de5ef61ae500d0f495d64b0645873ca1dcab99976` |
| 1178 个 class/SQL 聚合指纹 | `74ac8a25e6b352bf24cf0c8c671480561af41624e5e5872f16aac1ff1bc843cc` |

目录：

- 冻结源码：`D:/ERP_WMS/BC warehouse/2G/target/platform-result-parser-repair-20260911/a1/source`
- 新编译输出：`D:/ERP_WMS/BC warehouse/2G/target/platform-result-parser-repair-20260911/a1/backend`
- 冻结依赖：同批 `dependencies`，Maven：同批 `apache-maven-3.9.9`。

全部 class/SQL 与 r4 对应产物内容一致，差异 0；不是复用旧 classes。原八个业务测试文件整体哈希一致（比仅方法正文一致更严格），原 63 方法及 94 个迁移 SQL 保持不变。依赖离线使用专用副本，不改全局 PATH、公共版本声明、锁文件或产品配置。

编译仍有既存 `javax.annotation.meta.When.MAYBE` 注解相关警告；未因此修复范围外依赖或放宽检查。前端未编译/验证，本快照不是全源码发布候选版本。

## 4. 本批实际无数据库结果

冻结输入验证完成于 UTC `2026-09-11T14:54:14.920Z`（北京时间 22:54:14）。

| 验证 | 实际执行 | 失败 / 错误 / 跳过 |
|---|---:|---|
| Java 隔离防护 | 38/38 通过 | 0 / 0 / 0 |
| Java 基线角色夹具 | 7/7 通过 | 0 / 0 / 0 |
| 生命周期 Node 回归 | 32/32 通过 | 失败、取消、跳过均 0 |
| 哨兵静态契约 Node 回归 | 65/65 通过 | 失败、取消、跳过均 0 |
| 结果解析器＋实际入口/汇总 Node 回归 | 67/67 通过 | 失败、取消、跳过均 0 |
| 四个新增/受影响 JS 工具语法检查 | 全部通过 | 非业务测试计数 |
| 数据库业务测试 | **本轮 0 项执行** | 不用上表代替实库验收 |

67 项解析回归覆盖自闭合/完整元素混合、system-out/CDATA、实体解码/Unicode、属性遮蔽、非法/损坏报告、DTD/外部实体拒绝、失败/错误/跳过、重复/缺失/额外方法、头部计数不符、进程异常、错误同时出现、最终汇总重算、入口未执行状态及失败后停止。

开发记录另外保留，不累计进上表：最初单样本冒烟暴露 PowerShell XML Name 属性遮蔽，已用结构 getter 修复并加入回归；开发轮 63 项通过后新增四项边界检查，正式冻结轮为 67 项。开发 TAP 留在专用 development 目录，不作为正式冻结输入通过的替代证据。

## 5. r4 留存证据只读回归

未重新执行任何 r4 业务方法，仅读取原始 XML、既有进程退出码/超时记录和已哈希的 BUILD SUCCESS 日志，并与原预期方法清单对照。

| r4 套件 | 结构化识别 | 缺失 / 重复 / 额外 | 原业务结果 |
|---|---:|---|---|
| TenantSecurityPostgresTest | 4 | 0 / 0 / 0 | 4 通过 |
| TenantSecurityCommandsPostgresTest | 6 | 0 / 0 / 0 | 6 通过 |
| PlatformSessionPostgresTest | 27 | 0 / 0 / 0 | 27 通过 |

通过修复后的实际入口/汇总函数只读回放，得到 **37 passed、26 unexecuted、0 failures/errors/skipped、0 报告错误，回放状态 INCOMPLETE**。这不是新数据库结果，更不把原始 r4 FAILED 改成 PASSED。

原 r4 套件记录保存的是 exitCode/timeout 与日志哈希，不是新格式的完整进程字段。回放的已退出/输出完整条件由历史执行器成功门槛及保留的 BUILD SUCCESS 日志支持；没有伪称本轮又产生了业务进程证据。新批次将保存规范化进程字段。

原三个 XML 哈希、r4 数据目录/运行文件完整清单、旧快照、构建、失败结果、报告和关闭证据均已校验。r2/r3 旧证据按原保护清单继续核验，无漂移。原 r4 报告 SHA-256 仍为 `90854795447ce0cd968631b114ad81b2076dbad45968e95d59bb68d9ec58d8b5`。

字段名的独立[勘误补充](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_R4_DATABASE_ACCEPTANCE_ERRATA_2026-09-11.md>)明确：`browser_session_hash` 应为实际字段 **`browser_binding_hash`**。不修改原报告或任何历史哈希。

## 6. 完整重跑方案——仅提议，待另行批准

建议新批次为 r5，不复用 r2/r3/r4，也不使用本轮旧编译输出作为新目标已验证的证明。

| 项目 | 拟固定值 |
|---|---|
| 新批次根目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r5` |
| 新数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r5/pgdata` |
| 新证据目录 | `D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5` |
| 唯一监听 | `127.0.0.1:55449` |
| 主库 | `p3_m1_platform_release_r5` |
| 增量演练库 | `p3_m1_platform_upgrade_r5` |
| 专用角色 | `p3_r5_initializer`、`p3_r5_migrator`、`p3_r5_app` |
| 认证/数据来源 | 随机专用凭据＋SCRAM；空库、源码公共参考数据、合成夹具，不复制旧账号或任何真实数据 |

本轮只读核对时 r5 根目录/证据目录尚不存在，55449 无监听，r2/r3/r4 无 PID 文件；**不替代未来启动前的再次核验**。没有为此创建目录、集群或角色。

待批准后的准确执行顺序：

1. 仅在测试层建立 r5 绑定及实际入口/回归对应关系；旧目标必须拒绝。保留当前解析修复，原 63 个业务方法、产品逻辑和迁移不变。若发现其他输入变化，先报告差异并重新确定快照。
2. 封存新的后端源码/测试/资源/工具/依赖/迁移清单；在 r5 全新输出目录重新编译。运行防护、角色夹具、生命周期、哨兵、解析器及入口回归，核对实际数量和零跳过。绑定新增拒绝用例时明确记录计数增加，不降低已有覆盖。
3. 前置检查通过后才允许初始化；目录存在、端口占用、身份/指纹不符即停止。新集群仅监听 127.0.0.1:55449，核验 PID/启动时间/可执行文件/目录/集群号/监听及 SQL 身份。
4. 初始化与迁移/运行角色分离；运行角色非超级用户、无 BYPASSRLS、非表所有者，核验其不能修改迁移历史和 ready 标记。
5. 主库空库迁移至 V4_90（当前路线 8 步）；增量库依次至 V4_88（6 步）、V4_89（1 步）、V4_90（1 步）。核对 94 个输入 SQL、顺序/历史/校验和、重复迁移 0 变更，以及事务哨兵 1/1/1 精确标识保留与 `browser_binding_hash` 为 NULL。
6. 使用新编译产物完整执行下表八个套件；对每套件同时核验实际进程结果、XML 结构和方法清单。报告损坏、断言失败、意外跳过、进程异常或覆盖不符即停止，未执行项明确保留，不拼接历史成绩为新批次通过。
7. 成功或失败都走独立、有时限、经身份再次核验的 PostgreSQL 正常关闭流程；验证本批进程、PID 文件和 55449 监听消失，确认其他监听未改变。移除本批初始化临时明文凭据文件，保留脱敏证据和新数据目录。

| r5 拟完整执行套件 | 原业务方法数 |
|---|---:|
| TenantSecurityPostgresTest | 4 |
| TenantSecurityCommandsPostgresTest | 6 |
| PlatformSessionPostgresTest | 27 |
| PlatformInvitationSessionPostgresTest#invitation* | 4 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 |
| PlatformCompanyDataServiceIntegrationTest | 6 |
| TenantSecuritySpringPostgresTest | 14 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 |
| 合计 | **8 套件 / 63 方法** |

不允许自动换目标、覆盖、repair、clean、改 trust、放宽断言或修改产品逻辑。发现产品缺陷先报告证据与建议范围。仍不启动前端/HTTP，不操作其他服务、真实账号/租户/数据库，不提交合并或上线。

拟重跑停止点：新源码与产物映射、完整迁移与业务结果、失败/未覆盖项及正常关闭证据交付。**当前仅提交方案，需用户单独批准后执行。**

## 7. 证据入口与独立保留项

- [修复输入清单及历史保护哈希](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-result-parser-repair-20260911/a1/input-manifest.json>)
- [正式冻结输入编译与回归结果](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-result-parser-repair-20260911/a1/verification-results.json>)
- [r4 留存 XML/进程证据只读回放](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-result-parser-repair-20260911/a1/r4-readonly-replay.json>)
- [结束校验与证据索引](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-result-parser-repair-20260911/a1/closeout-check.json>)

前端 TS2769/正式类型检查及整包构建仍在范围外交接；本批不修改、删除或排除地址测试，不宣称前端通过。邀请完整人工激活仍待验，既有 MFA/恢复码/退出人工成果不无故重复。恢复核对最终业务边界仍待确认，不扩展自动核对。

生产数据库兼容性（包括既有 Flyway/PostgreSQL 版本警告）、正式配置、运维/迁移演练、最终人工验收和上线审批继续独立保留。**本批不是发布候选版本，也不表示可以上线。**
