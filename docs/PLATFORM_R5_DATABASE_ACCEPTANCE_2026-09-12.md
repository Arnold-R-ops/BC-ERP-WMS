# 平台 r5 后端隔离数据库验收记录

日期：2026-09-12（Asia/Shanghai；机器证据使用 UTC）。

## 1. 结论与停止点

**本批结果：FAILED，已正常关闭。** 未将失败改写为通过，未补跑或拼接历史成绩。

- 新后端输入封存、新目录编译和全部无数据库前置检查通过。
- 主库空库迁移至 V4_90、增量库 V4_88 → V4_89 → V4_90 均通过；权限、迁移历史/校验和、升级哨兵及重复执行零迁移检查通过。
- 原计划 8 个套件、63 个业务方法：**41 通过、1 失败、0 错误、0 跳过、21 未执行、0 无法核实**。实际启动 5 个套件，4 个套件全通过；第五个套件的真实断言失败后，停止后三个套件。
- 解析器正常工作：实际 XML 元素、方法清单及进程结果相互核对；报告解析/校验失败为 0。Maven 的 1 次非零退出对应同一项断言失败，不是额外一个业务失败。
- 专用 PostgreSQL 已按核实身份正常关闭；55449 监听、所属进程和 PID 文件消失。现有 5432 服务保持原 PID 8980。
- 本批没有修改产品逻辑、迁移、原 63 个业务测试方法或放宽断言。发现失败后仅进行文件证据与源码只读核对。
- **尚非完整发布候选版本，不可上线。** 前端正式检查、整包构建、人工待验项及生产上线条件独立保留。

原始结果：[database-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/database-result.json>)；只读复核：[closeout-evidence.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/closeout-evidence.json>)。

## 2. 授权环境及实际执行

| 项目 | 本批实际值 |
| --- | --- |
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r5/pgdata` |
| 监听 | 仅 `127.0.0.1:55449` |
| 主库 | `p3_m1_platform_release_r5` |
| 增量库 | `p3_m1_platform_upgrade_r5` |
| 角色 | `p3_r5_initializer`、`p3_r5_migrator`、`p3_r5_app` |
| PostgreSQL / 认证 | 18.6 / SCRAM；一次性随机凭据 |
| 集群号 | `7684313611194871252` |
| 主进程 | PID 52972；启动 epoch 1789143686 |
| 数据来源 | 全新空库、源码公共参考数据及合成夹具；未复制真实库或旧验收账号 |
| 批次时间 | 2026-09-12 00:21:23.274 ～ 00:24:29.738 +08:00 |

启动前确认专用数据目录不存在、55449 无冲突；初始化后核对进程、命令目录、集群号、监听及 SQL 身份。运行角色不是超级用户、无 BYPASSRLS、不是数据库所有者，不能写迁移历史或篡改准备标记。测试层禁用自动 Flyway/DDL，迁移由专用准备器执行。

仅启动本批 PostgreSQL；没有启动前端或独立 HTTP 服务。Spring 测试使用 MOCK/MockMvc，不监听 HTTP 端口；定时任务不启动，邮件和外部依赖保持专用测试适配。未操作真实账号、真实租户、真实库、原有服务或旧隔离环境。

## 3. 输入、改动和产物对应

这只是 **r5 后端隔离验收快照**，不是正式 R2 全源码快照或发布版本。

- 分支：`2-1G`；HEAD：`9c12f6f893105632c4de24ad8bb4f6f43092f84e`。工作区原有未提交改动保留，没有提交、合并或推送。
- 输入 ID：`a0c2606972ec4a3fccd7a2d1ab2f8c6902f5b82f27ec6172a43da7fed335fa4d`。
- 封存范围：924 份源码/测试/资源/工具文件；94 份 SQL；3337 份冻结 Maven 依赖文件；90 份 Maven 分发文件；136 个测试 classpath JAR。
- 工具链：Node 24.12.0、Java 17.0.17、Maven 3.9.9。后端批次不执行 pnpm 或前端构建，既定 pnpm 11.16.0 方案不变。
- 封存源码：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r5/snapshot/source`。
- 新编译目录：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r5/build/a1`。
- 实际编译 580 份主源码、193 份测试源码；BUILD SUCCESS，39.271 秒。不复用旧 classes。
- 1178 条编译后 class/SQL 文件指纹聚合：`9a7953bdce42d699a820f94cdfd3787491794c54db07dc747ddd8b08669ecf42`。
- 新 JAR SHA-256：`6948267cb3ebee688a50d0afde715af1a963581be9a24ebbe09aa3a5a4937c43`。
- 与上一解析器修复产物比较：产品 classes/SQL 差异为 0；测试 class 差异 10 个，来自 r5 测试目标常量及引用类重新编译，不等于修改了业务方法。
- 结束后重新核对：当前工作区的 924 个封存输入无漂移，封存源码、依赖、外部工具、1178 条产物和 JAR 一致；原 63 个方法的源码指纹不变。

输入全清单：[backend-input-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/backend-input-manifest.json>)；构建/方法及产物证据：[backend-build.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/backend-build.json>)。

### 本批文件变更

分组仅说明测试交付归属，不代表各组可以独立部署。

| 分组 | 文件 | 改动 |
| --- | --- | --- |
| 测试目标绑定 | `src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java` | r5 库、角色/绑定 |
| 测试目标绑定 | 同目录 `PlatformR2Isolation.java`、`PlatformR2SpringInitializer.java` | 固定 r5 身份，保留禁止回退 |
| 防护测试 | 同目录 `PlatformR2IsolationTest.java` | 增加旧 r4 URL 拒绝；旧 r2/r3 拒绝保留 |
| 实际入口回归 | `src/test/scripts/platform-test-results.test.mjs` | 导入并检查实际 r5 入口；r4 XML 只读回归保留 |
| 哨兵入口回归 | `src/test/scripts/platform-upgrade-sentinels.test.mjs` | 对实际 r5 入口核对 |
| 新批次工具 | `src/test/scripts/platform-r5-backend-snapshot.mjs` | 独立封存、指纹核验、旧证据保护 |
| 新批次工具 | `src/test/scripts/platform-r5-database-batch.mjs` | r5 固定目标、构建/前置检查、迁移/业务执行与正常关闭 |
| 交付记录 | 本报告及 r5 专用 evidence JSON | 记录真实结果、失败分析与关闭证据 |

`PlatformR2*` 类名、`p3.r2.*` 属性及 `P3_R2_*` 环境变量标签为旧测试工具名称，保留名称避免无关重构；允许的实际 URL、角色和目录严格指向 r5，不代表可连接 r2。旧 r4 执行器、封存输入和证据未改写。

## 4. 本批无数据库前置检查

所有下列检查来自本次新封存输入及新产物，完成后才启动数据库；不计入 63 个数据库方法。

| 检查 | 实际通过 | 失败/错误/跳过 |
| --- | ---: | --- |
| 隔离、目标及权限防护 | 39 | 0 / 0 / 0 |
| 角色夹具契约 | 7 | 0 / 0 / 0 |
| 进程生命周期 | 32 | 0 / 0 / 0 |
| 升级哨兵契约及入口 | 65 | 0 / 0 / 0 |
| 结构化 XML 解析器、实际入口及最终汇总 | 67 | 0 / 0 / 0 |
| 脚本语法检查 | 8 个文件 | 均退出 0 |

合计 210 个无数据库测试用例，另有 8 个语法检查。Node 测试取消数均为 0。r4 留存 XML 的 4/6/27 识别只是解析器回归，不计作本批数据库业务通过数。

## 5. 迁移、权限和哨兵真实结果

| 阶段 | 新执行迁移数 | 历史行数 | 重复执行 | 结果 |
| --- | ---: | ---: | --- | --- |
| 主库空库 → V4_90 | 8 | 8 | 0 新迁移 | 通过 |
| 增量库空库 → V4_88 | 6 | 6 | 0 新迁移 | 通过 |
| 增量库 V4_88 → V4_89 | 1 | 7 | 0 新迁移 | 通过 |
| 增量库 V4_89 → V4_90 | 1 | 8 | 0 新迁移 | 通过 |

94 是校验的迁移资源数量，不是每个空库应逐一执行 94 次。实际空库采用 B4_71 基线，然后 V4_72、V4_85～V4_90。各阶段迁移历史的版本、类型、脚本、成功标记和校验和符合预期，重复 migrate 明确为 No migration necessary，没有 repair/clean。

| 历史版本 | Flyway 校验和 |
| --- | ---: |
| B4_71 | -1902150811 |
| V4_72 | -1014316113 |
| V4_85 | 1952011876 |
| V4_86 | 1901493228 |
| V4_87 | 1941766358 |
| V4_88 | -1639265665 |
| V4_89 | 178422383 |
| V4_90 | 618333881 |

升级哨兵结果：

- 使用合法审计动作，唯一标识放在测试资源/明细字段；准备阶段为明确事务。
- 按本次确切 fixture 标识核对 1 名用户、1 条审计及 1 条旧会话，不依赖全表只有一条记录。
- 用户和审计字段升级前后保持；V4_89 旧会话字段在 V4_90 后保持。
- 新字段实际名称是 **`browser_binding_hash`**；旧会话该字段保持 NULL，没有被错误赋予浏览器身份。
- 各阶段准备、读取和一致性断言全部成功；字段、约束及目标历史检查通过。

本次仍出现 Flyway 9.22.3 对 PostgreSQL 18.6 高于其已验证版本范围的兼容性提示。实际隔离迁移通过不替代生产 PostgreSQL 版本、权限、数据规模和回退演练的确认。

## 6. 原 8 套件 / 63 方法实际结果

| 套件（省略包名） | 计划 | 通过 | 失败 | 错误 | 跳过 | 未执行 | 无法核实 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| TenantSecurityPostgresTest | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| TenantSecurityCommandsPostgresTest | 6 | 6 | 0 | 0 | 0 | 0 | 0 |
| PlatformSessionPostgresTest | 27 | 27 | 0 | 0 | 0 | 0 | 0 |
| PlatformInvitationSessionPostgresTest（invitation*） | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| PlatformCompanyDataServiceIntegrationTest | 6 | 0 | 0 | 0 | 0 | 6 | 0 |
| TenantSecuritySpringPostgresTest | 14 | 0 | 0 | 0 | 0 | 14 | 0 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 | 0 | 0 | 0 | 0 | 1 | 0 |
| **总计** | **63** | **41** | **1** | **0** | **0** | **21** | **0** |

前四套件进程正常退出 0；第五套件正常结束但 Maven 退出 1，XML 包含一个 AssertionFailedError。后三套件未启动，不能写成跳过或通过。

对本批 5 份实际 XML 进行只读结构化复核：DTD 禁止、外部解析器禁用；实际 testcase、类名/方法名、失败/错误/跳过标记、头部计数、进程证据及封存方法清单一致。没有重复/额外/缺失已执行方法，没有报告解析错误。本次核对不是一次新的业务执行。

41 项通过涉及平台统一状态、命令与数据库并发、平台会话/浏览器退出及邀请会话边界；不能据此声称下列完整 Spring 集成、受控读取/导出、暂停恢复必要联动和权限竞态的剩余 21 项已通过。包名包含 tenant 的测试验证平台安全边界，不构成本批开展 ERP 功能开发。

## 7. 失败证据、原因边界与建议

失败方法：

`PlatformAdminInvitationMfaTransactionIntegrationTest.credentialFailureSurvivesRollbackOfInvitationCreationTransaction`

实际断言：`platform_users.mfa_failed_attempts` **expected 5 / actual 4**，测试源码第 146 行。后续锁定期限、challenge 次数、邀请未创建、MFA_FAILED 审计等断言未到达，不记为通过。

只读源码核对：

1. 前面的 PlatformSessionPostgresTest.user() 创建默认 enabled 的合成用户及 PLATFORM_SUPER_ADMIN 绑定，无套件/方法级自动回滚或统一收尾；PlatformInvitationSessionPostgresTest 的四个用例也调用该创建方法。八套件共用本批主库，批次在套件之间检查身份但不自动重置数据。
2. 失败用例创建 SUPER_ADMIN 邀请。产品在 MFA 核验前执行 requireSecondSuperAdminSlot；已启用超级管理员加有效待接受邀请达到 2 时，先抛出 PLATFORM_ADMIN_SUPER_ADMIN_LIMIT。
3. 失败用例只要求抛出任意 BusinessException，未确认是预期的 MFA 凭据失败异常。因此更早的业务拒绝也可能满足该异常断言，随后失败次数仍为 4。

**推断：高度怀疑跨套件合成超级管理员残留造成席位前置条件冲突。** 但本批没有记录实际被捕获的 BusinessException 错误码；仅凭当前 XML 不能把它写成已证实的具体运行时错误，更不能宣布“MFA 失败记录事务存在产品缺陷”。详细分层记录：[failure-analysis.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/failure-analysis.json>)。

按授权已停止，没有调整超级管理员上限、重排产品检查、删除公共数据、改变事务或放宽任何断言，也未重启查询 r5 数据库。

建议下一批另行批准的最小范围：

- 仅修复专用测试夹具隔离/归属及诊断：确认目标分支前置条件，保留并核验准确的凭据失败异常类型/错误码，不记录认证材料。
- 审核前序套件自己创建的数据与外键依赖，只收尾精确登记的本套件合成数据；不按全表/宽泛角色删除，不删除公共角色或其他用例数据。未知残留明确失败，不能通过随意换邀请角色或放宽席位规则绕开。
- 保留原 8 套件、63 方法及失败次数持久化、锁定、审计、唯一性、权限和并发断言；新增无数据库夹具契约与诊断检查、新封存编译。修改业务方法内的异常断言需包含在该批明确批准范围内。
- 此建议不包含产品逻辑修改或立即重启数据库。修复完成后，再提交新目录/固定目标的一次完整重跑方案，获得批准后执行；不得拼接 r5 已通过 41 项与后续局部测试称为完整通过。

## 8. 正常关闭与历史证据保护

- 启动器输出管道尚未关闭时，通过限时就绪及真实进程/目录/集群/SQL 身份核验判定 READY，不只看端口或退出码。
- 失败后正常关闭使用同一已核实 PID 52972、启动时间、目录、集群号和监听身份。关闭进程退出 0，耗时 907 ms，状态 STOPPED。
- 最终 55449 无监听、r5 PID 文件不存在；独立只读进程检查无本批所属进程。
- 独立检查时间：2026-09-12 00:25:53.577 +08:00；同时确认 r2/r3/r4 PID 文件不存在。
- 原有 5432 PID 8980 保持；没有启动、重启、停止其他服务。
- 临时初始化密码文件已删除；本批 pgdata 和失败记录保留，不复用或清除。未将原始 JWT、密码、MFA 密钥、恢复码或原始 XML 环境属性复制进 docs 交付证据；原始运行材料留在本批隔离目录，不作为可公开分发材料。
- r2/r3/r4 环境、封存和原始报告通过指纹/清单复核，无修改。r4 仍保留 37 通过、26 未执行的原 FAILED 记录；字段勘误只在补充记录中说明。

关闭证据：[independent-closure-check.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r5/independent-closure-check.json>)。

| 文件 | SHA-256 |
| --- | --- |
| r5 backend-input-manifest.json | 9dd5188e8b8a5df5b91bc38b69f4318e210a06331aa2014c1a4de38f519b860e |
| r5 backend-build.json | 8945d50c309a5832b2b9f28c6021354e7f71a144688660a2b1e099538e88b618 |
| r5 原始 database-result.json | 85da59a85f2a669c15bf8f8767c327f7c4075bb8d24f6aabe8e46c5bf326bccf |
| r5 independent-closure-check.json | 4f40acf23019c0aa87e25aa0c9849a3feeacc90732632159c25af49ede259633 |
| r4 原始报告 | 90854795447ce0cd968631b114ad81b2076dbad45968e95d59bb68d9ec58d8b5 |
| r4 原始 database-result.json | ef4a425dc8f986a466f9f99fa384eecea3ef193a65e18cc57049ebfeb91441ad |

## 9. 未覆盖及发布边界

- 本批剩余 21 个数据库方法未执行；上述失败方法的目标 MFA 事务链条尚未证明通过。
- 地址测试 TS2769 继续由对应任务处理。本批未修改/删除/排除该测试，未执行前端正式类型检查或整包构建，不能宣称其阻塞已解决。地址测试与 pnpm 锁文件指纹未改变。
- 邀请完整人工浏览器激活流程仍待发布候选版本验收。4 项邀请自动数据库测试不等于人工流程完成。
- 既有 MFA、恢复码、退出等人工子项保留历史通过记录，本批没有重做或累加为新验证。
- 最新普通退出规则仍是“同浏览器、同账号会话一起退出”；不同登录的空闲计时独立，不代表跨标签自动共享登录或共享计时。
- 恢复核对仍是“缺单报告＋人工核验修改、取消和退款”；最终业务边界待用户确认，本批未扩展自动核对。
- 正式域名、HTTPS、生产密钥、管理员/MFA 应急、真实邮件告警、生产数据库兼容性、备份恢复/迁移及回退演练均独立待验。
- 不提交、不合并、不推送、不开展租户 ERP 开发、不开放生产。下一批修复/重跑需另行确认，本批到本报告停止。

