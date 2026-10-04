# r11 平台后端隔离验收：完整通过并结束本轮

日期：2026-09-19。**本次冻结版本的后端隔离验收通过：独立 1/1、顺序 42/42、完整 63/63；专用集群正常关闭，临时系统及屏幕唤醒请求同线程释放，包装进程退出已确认。**

这是后端隔离验收结论，不是完整发布候选版本完成，也不是生产上线批准。没有追加下一批环境或重跑。

## 1. 本次范围与固定输入

- 分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；保留未提交成果，没有提交、合并或推送。
- 新根：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260919/r11`。
- PG 数据目录：新根下 `pgdata`；只监听 `127.0.0.1:55449`。
- 双库：`p3_m1_platform_release_r11`、`p3_m1_platform_upgrade_r11`。
- 角色：`p3_r11_initializer`、`p3_r11_migrator`、`p3_r11_app`；测试控制 schema：`p3_r11_control`。
- 导出临时根：新根下 `export-fixture-temp`。只用空库、公共参考数据和登记合成夹具；随机凭据、SCRAM，没有复制真实数据或旧验收账号。
- 冻结源码：新根下 `snapshot/source`，1007 个源文件；依赖与 Maven 分别冻结到 `snapshot/maven-repository`、`snapshot/apache-maven-3.9.9`。
- 全新编译：`build/a1`；无旧 classes 复用。产物包含 1204 个 class/SQL 指纹条目，产品 class/SQL 与 r10 比较差异为 0。11 个测试 class 差异对应目标绑定及常量重新编译，不代表修改了业务方法正文。
- 固定版本：Node 24.12.0、Java 17.0.17、Maven 3.9.9、Surefire 3.1.2、PostgreSQL 18.6；没有运行前端或 pnpm。
- 实测执行上下文：`ARNOLD\Arnold`，管理员组启用、令牌提升、非受限；通过工具权限流程执行，没有 RunAs、系统权限或策略变更。

### 测试层变更账本

相对 r10 后端及已批准 a2 诊断修复基线，修改 8 个既有测试文件、新增 9 个 r11 专用文件。完整路径及前后指纹见 [输入清单](releases/platform-db-acceptance-20260919/r11/backend-input-manifest.json)。

1. 四个 Java 测试工具仅绑定 r11 数据库、角色、schema 和目录；隔离测试增加旧 r10 URL 拒绝参数。
2. `platform-r10-binding.test.mjs` 的历史检查固定读取原 r10 冻结 Java；`platform-r9-fixture.test.mjs` 对齐新增隔离参数数量。
3. `platform-query-evidence.test.mjs`、`platform-query-failure.test.mjs` 接入实际 r11 入口；原 manifest 只提供受保护的预期方法名称，不借用成绩。
4. 新增 r11 的 plan、snapshot、batch、environment、binding/environment 测试，以及临时唤醒 PowerShell/Node 包装器和专项测试。

沿用共享清单、报告解析、Maven、生命周期、父链、哨兵和阶段引擎，没有另写参数化清单解析器。a2 冻结目录中两个旧结果 JSON 继续受历史保护，但不作为本次执行源码或成绩来源。

63 个业务方法正文及迁移文件与 r10 冻结清单完全相同。历史累计 **8 个方法、11 次修订**保留，本轮新增业务方法正文修改为 0；不能将此描述成“最初 63 个方法从未修改”。产品逻辑、数据库约束、ERP、前端和公共默认配置未修改。

## 2. 实际命令、预算与无数据库结果

项目根目录内执行：

```text
D:/JAVA/NODE/node.exe src/test/scripts/platform-r11-backend-snapshot.mjs seal
D:/JAVA/NODE/node.exe target/platform-db-acceptance-20260919/r11/snapshot/source/src/test/scripts/platform-r11-awake.mjs run --authorized-20260919-r11-arnold-elevated-platform-backend-only
```

第二条经工具权限批准在沙箱外运行。临时请求取得后，固定子进程只执行冻结批次的 `combined` 入口；入口核对 inputId、父 PID、持有线程及尚未释放的事件证据，先编译门禁再进入数据库，没有分阶段人工重启或换产物。

原操作预算保持：编译/Java 门禁各 300 秒，Node 每套 120 秒，语法/版本各 15 秒，迁移各 180 秒，Maven 每次 240 秒，Surefire fork/退出 120/20 秒；初始化 60 秒，启动命令/包装 35/37 秒，就绪 40 秒，收尾 60 秒，停止命令 32 秒，SQL/statement 30/15 秒，完整查询/单 PID/就绪 SQL 15/5/4 秒。没有运行中加时。

唤醒包装器另行冻结总观察上限 18,000,000 ms、外层执行上限 18,060,000 ms，不延长内部步骤预算，不宣称宿主暂停时具备硬性墙钟保证。

| 本次前置验证 | 实际结果 |
| --- | ---: |
| 新源码 package 编译 | 通过，Maven 约 31.209 秒 |
| Java 无数据库门禁（8 套件） | 164/164 |
| Node 无数据库门禁（27 套件） | 698/698 |
| JavaScript 语法检查 | 26/26 |
| 真实 Maven 三阶段落盘探针 | 1/1、1/1、1/1 |

Node 数量为原 613＋取证 29＋r11 绑定/环境 30＋临时唤醒 26。全部无失败、错误、跳过或取消；唤醒专项同时做 PowerShell AST/C# 编译检查，不借该检查额外调用唤醒 API。

探针位于 `build/a1/landing-reports/{independent,sequential,full}`，业务报告位于 `build/a1/reports/{independent,sequential,full}`。三次实际 Maven 均核验 XML、stdout、进程结果、早先文件哈希与默认报告目录保护，未互相覆盖、未误读旧报告。探针与无数据库门禁不计入业务 106 次。

## 3. 真实迁移及 1／42／63

主库使用空库基线迁移至 V4_90，成功历史 8 条；增量库依次为 V4_88 的 6 条、V4_89 的 7 条、V4_90 的 8 条。94 个源码迁移条目完成校验，既有校验和断言及各步骤重复迁移零变更均通过。

四次迁移进程均退出 0、没有超时。13 个迁移/哨兵子阶段全部成功；按确切夹具标识核对用户、审计和旧会话各 1 条，字段保持，升级前旧会话的 `browser_binding_hash` 仍为 null。没有 repair、clean 或放宽约束。

运行角色 `p3_r11_app` 已实际确认：非 SUPERUSER、非 BYPASSRLS；不拥有业务/控制表；无 Flyway 历史写权限、无 ready 更新权限。

| 阶段 | 套件 | 计划/通过 | 失败 | 错误 | 跳过 | 未执行 | 无法核实 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 独立目标 | 1 | 1/1 | 0 | 0 | 0 | 0 | 0 |
| 前序 41 项后目标复测 | 5 | 42/42 | 0 | 0 | 0 | 0 | 0 |
| 完整回归 | 8 | 63/63 | 0 | 0 | 0 | 0 | 0 |

**106 是本次累计执行次数，只有 63 个不同业务方法。完整 63 项独立成立，不拼接前两阶段或历史成绩。**

完整阶段实际分布：

| 套件 | 通过 |
| --- | ---: |
| TenantSecurityPostgresTest | 4 |
| TenantSecurityCommandsPostgresTest | 6 |
| PlatformSessionPostgresTest | 27 |
| PlatformInvitationSessionPostgresTest | 4 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 |
| PlatformCompanyDataServiceIntegrationTest | 6 |
| TenantSecuritySpringPostgresTest | 14 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 |

14 次套件执行均完成夹具收尾和报告核验。三阶段 MFA 对照一致：调用前有效超级管理员 1、待接受邀请 0、匹配账号/挑战各 1、失败计数各 4；实际异常为 `PlatformAdminInvitationMfaCredentialException` / `AUTH_FAILED`。持久化后账号/挑战计数各 5、锁定生效、挑战消耗、邀请数 0。没有要求历史累计记录总量相同，没有删除未知残留、公共角色或不可变审计。

本次成功不能倒填 r5 未捕获的实际异常码，也不补写 r8 未取得的历史导出底层异常。

## 4. 正常关闭、临时请求释放与证据

- 批次数据库区间：北京时间 **11:42:43～12:04:23**；随后仍保持请求，完成源码/产物/历史指纹复核与最终结果保存。
- 集群号 `7687086839958429888`；只初始化/启动一次，只正常停止一次。
- 已核实数据库 PID `42212` 后使用 fast 正常停库，日志为 `server stopped`；`stop.status=STOPPED`、`normalShutdownProven=true`。
- 最终目标进程及父链消失、专用监听为空、`postmaster.pid` 不存在。选定原有监听与开始时一致；未操作其他服务。
- 临时密码文件已清除，执行程序内凭据引用清空；导出根剩余文件 0，合成夹具及配置收尾通过。保留新 pgdata 及证据，没有删除历史环境。
- `snapshotStillMatches=true`、`compiledAndLandingUnchanged=true`；历史保护清单核验通过，原 FAILED 未改写。
- 实际取得请求：北京时间 **11:38:19.153**，包装 PID `55484`、OS 线程 `64068`，标志 `0x80000003`，返回非 0。
- 实际释放：北京时间 **12:05:22.536**，同 PID/线程，标志 `0x80000000`，返回 `2147483651`，`sameThread=true`。
- 执行子进程 `5112` 退出 0；包装器退出 0，退出回执于 **12:05:22.583** 确认；`releaseConfirmed=true`、`wrapperExitConfirmed=true`、`issues=[]`。
- 临时请求持续约 27 分 3 秒。没有改电源/锁屏/安全策略、模拟键鼠、注册服务/任务或长期助手。

包装器聚合 stdout 文件为空；本次结论基于各命令独立日志、JUnit/TAP、阶段 JSON、唤醒事件及进程退出回执，不把空聚合输出当作业务通过证据。

最终只读敏感信息模式扫描于 UTC `2026-09-19T04:07:03.660Z` 完成：539 个证据/日志文件全部读取、0 读取失败，包含14份业务 XML及关闭后的服务器日志；JWT、Bearer、私钥、明文认证赋值及敏感 XML 属性候选命中 0。未读取 pgdata。此为模式扫描结论，不是对所有潜在秘密绝对不存在的证明。

### 关键指纹

| 对象 | SHA-256/标识 |
| --- | --- |
| 后端输入 ID | `f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62` |
| 输入清单 | `6faefc5ab260931cbc1d378fbef826f82a4c3b1ef391dd62220b7c427aa9c228` |
| 新 JAR | `7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6` |
| class/SQL 聚合 | `36a46c4ac127806483861b4032133c7074c9a10dcb4791c4bf1177d82658dfc7` |
| 编译/前置结果 | `03bb27e945129c824c9a0e47338a662f6b9a9789c104781b1b08c57da2753e8b` |
| 实库结果 | `eacd796c4a4d4096120da4468aa4eca38ff502c489121536cf350b2afa14af0f` |
| 唤醒/退出结果 | `f7068a60e3775588142b37c27adb2664379e02f77f0abbf1fda1f827261fc5aa` |
| 唤醒事件日志 | `884329e0f822f760a226b812b2e10620f28ba9fd85fc3e451abbd713f186d113` |

原始机器证据：

- [输入及预算](releases/platform-db-acceptance-20260919/r11/backend-input-manifest.json)
- [全新编译及无数据库成绩](releases/platform-db-acceptance-20260919/r11/backend-build.json)
- [真实迁移、三阶段及关闭结果](releases/platform-db-acceptance-20260919/r11/database-result.json)
- [独立阶段](releases/platform-db-acceptance-20260919/r11/phases/independent.json)、[顺序阶段](releases/platform-db-acceptance-20260919/r11/phases/sequential.json)、[完整阶段](releases/platform-db-acceptance-20260919/r11/phases/full.json)
- [唤醒请求与包装进程结果](releases/platform-db-acceptance-20260919/r11/awake-result.json)、[实际事件](releases/platform-db-acceptance-20260919/r11/awake-journal.jsonl)

## 5. 本轮停止点与未覆盖边界

本轮平台后端隔离验收结束，不自动创建 r12、不再次启停或追加无关测试。没有产品缺陷需要在本批擅自修复，实库首因及收尾错误均为空。

以下仍独立保留，未经本批验证或批准：

1. 前端 `addressLength.audit.test.tsx` TS2769 的范围外交接及正式类型检查/整包构建；本批没有重新检查前端解决状态，不能标成已解除阻塞。
2. 邀请激活完整人工浏览器流程及受后续变更影响时的必要人工回归；不无故重做已完成 MFA、恢复码等人工子项。
3. 恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界待确认；没有实施完整自动核对。
4. 统一发布整合、正式域名/HTTPS/密钥、管理员与 MFA 应急流程、真实邮件告警、生产数据库权限/兼容、备份恢复及生产迁移演练；沿用统一运维方案，需分别确认。
5. 临时唤醒及本次成功不能证明 Modern Standby 是历史延迟的唯一原因，也不证明所有宿主异常下都能保证硬性墙钟时限。

本批未启动前端或 HTTP 服务，未连接真实数据库、操作真实账号/租户、改变现有服务或开放生产。**该冻结版本后端隔离通过，不等于完整发布候选版本完成或可以上线。**
