# 平台专用工具：父链、查询诊断与超时收尾修复

日期：2026-09-16。范围：仅平台专用测试工具；不是数据库验收、发布候选版本或上线批准。

## 1. 本批结论与边界

最终工具源码快照 **a2：446/446 项无数据库测试通过，失败 0、跳过 0、取消 0**；9 个 JavaScript 文件语法检查通过，实际 PowerShell 查询载荷语法检查通过。

- PostgreSQL 初始化、启动、连接、迁移和数据库业务测试均为 **0**。没有创建 s2/r8，没有复用 s1。
- 没有产品逻辑、数据库约束、迁移、63 个业务方法、公共 POM、锁文件、ERP 功能修改；没有操作现有服务或提交、合并、推送。
- 本批真实合成子进程运行于 `ARNOLD\CodexSandboxOffline`，SID 尾号 `1003`；Node `24.12.0`。未申请或使用沙箱外启动权限。
- s1 原始完整结果仍为 **FAILED**；补充的正常关闭仍单独为 **PASSED_CLOSEOUT_ONLY**。不能把后者倒填成原批通过。
- s1 约一小时延迟的原因仍 **未确认**。本批没有证据证明它由休眠、执行器挂起或特定系统故障造成。

## 2. 工具修复清单与实际接线

修改 4 个既有测试工具：

| 文件（`src/test/scripts/`） | 修复内容 |
| --- | --- |
| `platform-process-lifecycle.mjs` | 实际子进程句柄回执；墙钟/单调时钟经过时间；超时触发、终止请求、请求结果、退出确认、管道关闭及执行器结束事件；启动、就绪与独立收尾预算轨迹；首因与收尾错误分开保留。 |
| `platform-launch-preflight.mjs` | 实际全环境查询和单 PID 查询共同使用新 PowerShell helper；诊断记录阶段、PID、缺失字段、类别、允许公开的异常类型/数值返回码；查询失败不转成 ABSENT。 |
| `platform-windows-identity.ps1` | 全局 postgres 清单中的不完整记录逐 PID 留下诊断；监听所有者查询也使用同一 helper；保存精确创建时间、命令行哈希，不保存查询对象的完整命令行。 |
| `platform-s1-start-stop.mjs` | 实际 `observe → probeReady → stop → closeout` 路径使用共享父链核验，保存计时和回执；SQL 前及停止前保留完整身份检查；停止后和最终复核同时确认已识别的 cmd 中间进程消失。 |

新增 6 个测试工具/测试：`platform-process-ancestry.mjs`、同名 `.test.mjs`、`platform-process-query.ps1`、`platform-process-query.test.mjs`、`platform-process-timing.test.mjs`、`platform-process-repair-verification.mjs`。

父链规则：

1. 支持直接 `pg_ctl → postgres` 和已观察到的 `pg_ctl → cmd.exe → postgres`，不接受任意进程名或端口匹配。
2. 已退出启动器由本次真正创建的子进程句柄回执绑定：PID、父执行器、路径、执行上下文所有者、启动/确认退出时间。再次查询该数值 PID 若已出现别的进程，即拒绝认领；信息不完整也拒绝。
3. cmd 和 postgres 保留精确路径、数据目录、所有者、父 PID、创建窗口及精确创建 ticks 核验。再次检查与首次绑定不符即停止；中间进程消失而未留下可核实身份，不凭猜测补链。
4. 原 PID 文件、物理集群号、目录、监听地址/端口/所有者和 SQL 身份检查继续保留。全局清单与单 PID 证明的精确创建时间也比对。
5. 收尾检查发现 cmd 仍在、查询不完整或 PID 被复用，不能宣称正常关闭；不据此终止未知进程。

查询诊断规则：首次不完整后，只为定位原因再查同一 PID；即便第二次恢复完整，也不把第一次失败静默改成成功。区分 `EXITED_DURING_QUERY`、`ACCESS_DENIED`、`PID_REUSED`、未知查询失败/缺失字段。错误摘要不保留凭据、原始认证请求、完整敏感命令行或任意异常消息。

旧 s1 入口仍受原目标、旧清单和已执行结果保护，本批没有解除这些保护。下一批必须在获批后绑定新的 s2 目标并重新封存；**不能直接运行旧 s1 命令**。公共 r7 工具仍使用同一生命周期/前置查询模块，其受影响回归已执行；没有创建新的完整实库批次。

## 3. 实际验证结果

最终 a2 运行：UTC `2026-09-16T08:59:52.467Z` 至 `09:01:20.892Z`，每个套件执行器退出码均为 0，输出完整且确认退出。

| 套件 | 实际通过/执行 |
| --- | ---: |
| process-lifecycle | 35/35 |
| launch-preflight | 40/40 |
| s1-start-stop | 33/33 |
| process-ancestry | 33/33 |
| process-query | 16/16 |
| process-timing | 8/8 |
| fixture-isolation | 24/24 |
| upgrade-sentinels | 65/65 |
| test-results（结构化 XML） | 67/67 |
| r6-stages | 57/57 |
| report-runner | 60/60 |
| r7-binding | 8/8 |
| **合计** | **446/446** |

真实无数据库计时记录，来自 a2 而非历史成绩：

| 场景 | 本次实际结果 |
| --- | --- |
| 正常 Node 子进程 | 70 ms，正常退出并确认；无超时/终止请求。 |
| 保持运行的 Node 子进程 | 配置 200 ms 超时；215 ms 内返回；记录超时、句柄终止请求和终止确认。 |
| 外部 AbortSignal | 216 ms 返回；`aborted=true`、`timedOut=false`，确认退出。 |
| 有意延迟父执行器调度 | 50 ms 预算，约 184 ms 才实际触发计时器，记录约 134 ms 延迟；189 ms 完成并确认终止。此实验不能反推 s1 原因。 |
| 启动超时后的独立收尾 | 超时子进程 165 ms 结束；新收尾子进程 61 ms 完成，均确认退出；首因仍为 LAUNCHER，收尾另记成功。 |
| 收尾自身超时 | 收尾预算 250 ms，子进程约 260 ms 确认退出；收尾仍记 `CLEANUP_TIMEOUT`，不把杀掉执行器当成业务关闭成功。 |
| 启动失败与收尾失败同时存在 | 真实子进程分别退出 7 和 9；启动首因保持 7，收尾失败单独记录。 |

这些场景使用实际 Node 子进程及实际共享生命周期执行链，但数据库身份由明确标注的合成对象提供，**不是 PostgreSQL 关闭成功证据**。PowerShell 诊断分类用真正的 PowerShell 执行共享函数、注入合成查询结果；这不等于本批再次完成了沙箱外 CIM 权限实测。既有原生 Windows 后代持有管道测试亦在本次回归中重新通过。

开发过程如实保留：初次未封存专项运行为 48 项中 39 通过、9 失败，发现 PowerShell 单行结果数组展开导致误判。修复数组归一后专项通过。随后 a1 封存版本 440/440 通过；复核时补上 cmd 退出确认，重新封存 a2 并完整重跑得到上述 446/446。**不累加 a1、专项运行或历史数据库成绩**。a1 证据保持原样。

## 4. 源码与证据指纹

本批是专用工具修复快照，**不是正式 R2 全源码快照**，没有进行 Java 编译、正式前端类型检查或整包构建。

- 源码：`target/platform-process-repair-20260916/a2/source`，956 个文件。
- 输入 ID：`c95f8135c9ea9889d2377f9873751c87a2bcee230d0b98bbdd910ab178e1a08a`。
- 清单：`docs/releases/platform-process-repair-20260916/a2/input-manifest.json`。
- 清单 SHA-256：`8c8d49a103cf970fada6d789a4a487f87c1b44ef43a2694c0bae4de3578d2d26`。
- 汇总：`docs/releases/platform-process-repair-20260916/a2/no-database-results.json`。
- 汇总 SHA-256：`e6befeb21a209c24c78698f2d033ad566c503750fd6053c7edf7ff8d4a119b19`。
- TAP、逐场景时间/退出证据、查询诊断样本及其逐文件哈希：`target/platform-process-repair-20260916/a2/verification`；总表 `outputFiles` 列出路径及哈希。
- 快照纳入的 `platform-s1-checked-stop.mjs` 是此前已有且本次未修改的收尾工具，不能把清单中的“新纳入快照”理解为本批修改。

执行链（仅说明本次已执行的无数据库命令；证据目录不可复用）：

```powershell
& 'D:/JAVA/NODE/node.exe' src/test/scripts/platform-process-repair-verification.mjs seal a2
& 'D:/JAVA/NODE/node.exe' 'target/platform-process-repair-20260916/a2/source/src/test/scripts/platform-process-repair-verification.mjs' verify a2
```

封存清单逐项核对工作区、冻结源码和工具文件哈希；测试汇总还核验输出完整性、进程终止、实际测试数及跳过数，不仅看退出码。

## 5. 历史事实、未覆盖和剩余风险

已重新核验 r2～r7 的 **47,808** 个历史文件及 s1 的 **1,945** 个文件未改变；保护清单中的报告、地址测试和锁文件未改变。

s1 原证据：

- 原始 `result.json` SHA-256：`f7ccfb905ae421e743c6ac3f9b116a36210ae090a4c4b4d2988732ca62c2d0fd`，仍为 FAILED。
- 补充 `checked-stop-result.json` SHA-256：`28d1ec55d3755f3ce2ecf5e0e387cdfd9fabcfe04c896d09daef76b855128514`，仅正常收尾通过。
- 原报告 SHA-256：`a9fe6e4172b909986c477990f227c4532be1fd8a22e376d002bd9d7e324e7dcb`。
- 异常长查询 PID `63864`，UTC `08:30:08.677` 开始；配置 15 秒，记录 `3,602,988 ms`，`timedOut=true` 且最终确认执行器退出。旧记录没有实际计时器触发、终止请求、单调时间等数据，原因仍未确认。
- 旧清单“20 个、1 个不完整”没有保留具体不完整行及缺失字段；不能将新增 PID 或任何猜测写成已确认原因。

限制：

1. 新父链及诊断代码尚未运行在新 PostgreSQL 实例上；真实启动、SQL 身份及完整关闭仍待下一批授权。
2. 真实合成进程证明本机本次的正常调度/终止行为，不覆盖系统休眠、系统长时间失去调度、磁盘/内核调用冻结、权限突然变化等全部情况。**不承诺所有环境下的硬性墙钟时限**。
3. 查询权限不足或进程竞争变化时会失败关闭，不为了清理成功而认领未知 PID。
4. r5 未留存的实际异常码仍未知；没有倒填。1/42/63 完整回归仍待单独新批次，不能由本次工具测试替代。
5. 前端地址测试 TS2769 交接、正式类型检查/整包构建阻塞、邀请完整人工激活、恢复核对最终业务边界和其他人工待验项独立保留；本批未重测或宣布解决。不能宣称可上线。

## 6. 提请确认：下一次 s2 最小启停与 SQL 身份验证

**本节仅为新方案，没有初始化、创建目录、启动或连接。用户批准后才实施。**

| 项目 | 固定建议 |
| --- | --- |
| 新根目录 | `D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260916\s2` |
| 数据目录 | 上述根目录下 `pgdata` |
| 监听 | 仅 `127.0.0.1:55449` |
| 数据库/初始化角色 | 仅默认维护库 `postgres`；`p3_s2_initializer` |
| 程序及认证 | 既定 PostgreSQL 18.6；随机临时凭据；SCRAM；不使用 trust |
| 执行上下文 | 申请工具沙箱外权限，重新核验 `ARNOLD\Arnold` / SID 尾号 1001 / 非受限、管理员组启用、令牌提升这一已说明通道；不使用 RunAs、不改 UAC/权限。此选择不表示 PostgreSQL 必须管理员运行。 |
| 输入 | 以本次 a2 工具为基础，仅绑定新的 s2 路径/角色/门禁清单，再冻结并重跑相关无数据库回归；不用旧 s1 执行命令。 |

一次执行顺序：

1. 核验新根不存在、端口未占用、工具指纹、实际进程/监听查询能力及 Windows 上下文；任一不符即停，不创建初始化目录。
2. 初始化一次全新空集群；一次 `pg_ctl start`；错误 87/3 或其他诊断错误仍为失败，即使尾行或退出码看似成功也不忽略。
3. 验证新父链、所有者、精确创建时间、目录、二进制路径、PID 文件、集群号和唯一回环监听；只读 SQL 核验 `current_database/current_user`、地址/端口、`listen_addresses`、`data_directory`、集群号，并再查物理身份。
4. 成功或失败均进入独立身份核验收尾；仅对核实的本批集群执行正常 `pg_ctl stop -m fast -w`。检查 PID 文件、postgres、已识别 cmd 及监听消失，保留首因和独立收尾结果。
5. 清除本次临时明文凭据，保留新目录及脱敏证据；失败不自动重试，不自动进入完整数据库批次。

### 需本次方案明确批准的预算调整

本次没有擅自修改旧 s1 的预算常量。现有证据显示全环境查询通常约 4.7～7.6 秒，单 PID 查询约 1.6 秒；就绪流程还包含 SQL 后第二次身份核验。原 **10 秒总就绪预算** 与完整检查组合偏紧，修复父链后亦可能触发预算失败。

建议 s2 专用配置：初始化命令 60 秒；启动命令 35 秒/启动阶段 37 秒（pg_ctl 内部等待 30 秒）；**就绪阶段 40 秒、独立收尾阶段 60 秒**；每次全环境查询仍上限 15 秒、单 PID 查询 5 秒、SQL 身份查询 4 秒。命令终止确认另记 1 秒等待及真实确认结果。所有事件保留实际墙钟、单调时间及超时触发记录。

这是对完整检查工作量的预算分配，**不是一小时异常的修复结论**，也不会放宽查询、父链、SQL 或关闭判据；超预算仍失败。最终只读复核的各子命令继续使用自己的有界执行器，不等待已超时启动承诺，也不把未核实的退出当成关闭成功。

拟在批准并完成新目标绑定后使用的新封存入口（目前不存在，不能现在执行）：

```powershell
& 'D:/JAVA/NODE/node.exe' 'D:/ERP_WMS/BC warehouse/2G/target/platform-s2-tool-validation-20260916/a1/source/src/test/scripts/platform-s2-start-stop.mjs' '--authorized-20260916-s2-arnold-elevated-empty-cluster-only'
```

下层仍使用已核定 `D:/ITsoftware/PostgreSQL/bin` 的 initdb / pg_ctl / pg_controldata / psql；密码只经临时文件或专用子进程环境传递，不放在命令行、报告或冻结源码。新绑定后的精确命令和输入指纹在启动前复核。

停止点：一次新空集群的最小启停、只读 SQL 身份和脱敏关闭证据。**不创建业务库，不迁移，不执行 1/42/63，不操作真实数据和现有服务，不提交合并或上线。** s2 真正通过后，才讨论完整验收批次的独立授权。
