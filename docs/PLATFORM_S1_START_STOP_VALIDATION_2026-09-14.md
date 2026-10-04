# s1 最小启停验证：启动成功，验收失败，补充正常关闭完成

日期：2026-09-14。时间为 Asia/Shanghai。

**本批完整验证结果仍为 FAILED；补充收尾为 PASSED_CLOSEOUT_ONLY。** 未执行 SQL 身份查询，不把启动日志或正常关闭成功当成完整身份验收通过。未创建 r8、未重试初始化/启动、未执行迁移/业务测试，尚非发布候选版本，不可上线。

## 1. 实际成绩与最终环境状态

| 项目 | 实际结果 |
|---|---|
| 专用工具封存 | 949 个输入文件；相对上批既有源码变化 0，仅新增三个 s1 工具文件 |
| 正式无数据库回归 | 108/108，失败 0、跳过 0、取消 0；3 项 JavaScript 语法检查通过 |
| PostgreSQL 版本 | 固定程序实测 18.6 |
| 实际初始化 | **1 次，成功**；日志所有者 `Arnold`，SCRAM；没有错误 87/3 |
| 实际启动 | **1 次，启动器退出码 0**；PID 60768，仅监听 127.0.0.1:55449；服务日志出现 ready |
| 物理/OS 身份 | 目录、PID、启动时间、所有者、可执行路径、集群号及回环监听匹配；发现实际存在中间 `cmd.exe` 父进程 |
| SQL 身份验证 | **0 次，未执行**；工具在进入 psql 前拒绝进程父链 |
| 首轮自动收尾 | **失败**；查询信息不完整，`pg_ctl stop` 尚未执行；原结果未改写 |
| 补充身份核查与首次正常停止 | **成功**；完整核对后执行一次 `pg_ctl stop -m fast`，退出码 0 |
| 最终残留核验 | PostgreSQL PID 60768、父命令 PID 36212、postmaster.pid、55449 监听均消失；目标后代检查通过 |
| 临时明文凭据 | `init-password.ephemeral` 已移除；未输出或归档密码，不再保留执行器中的临时密码变量 |
| 既有服务 | 核对的原有监听仍为 0.0.0.0:5432、PID 10452；没有启动、重启、停止原服务 |
| 数据及目录 | s1 目录保留，仅初始化空集群和默认维护/模板数据库；没有另建业务库、复制真实数据或旧账号 |

本批只有一次初始化、一次启动和一次实际正常停止。补充收尾不是第二次启动，也不是重新执行 s1 验收。正常关闭成功不会把原 FAILED 改为 PASSED。

## 2. 实际执行上下文与固定目标

初始化/启动执行器及补充收尾执行器均通过工具流程取得沙箱外许可，实际核验为：

- Windows 用户：`ARNOLD\Arnold`。
- SID：`S-1-5-21-3770167052-703495069-1631994977-1001`。
- `restrictedToken=false`、`administratorEnabled=true`、`elevated=true`、`elevationType=1`。
- 固定 Node 24.12.0，固定 Windows PowerShell 5.1；初始化、启动、查询、收尾均由专用 Node 调用链执行。
- 未调用 RunAs，未修改 UAC、执行策略、系统权限或公共数据库配置。该上下文是用户明确批准的本次环境，**不证明 PostgreSQL 必须使用管理员权限**。

唯一集群目标：

```text
根目录：D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1
数据目录：上述根目录\pgdata
监听：127.0.0.1:55449
维护库：postgres
初始化 SQL 角色：p3_s1_initializer
物理集群号：7685305294091642656
```

初始化前、创建 s1 根目录前、启动前均进行了真实 OS 查询，未遇到目录存在、端口冲突或账户不符。密码仅通过临时文件交给 initdb；没有 psql 调用，因此也没有实际 SQL 认证会话。保留目录中的 PostgreSQL SCRAM 认证资料是这个合成空集群自身的一部分，不是报告中的原始明文凭据。

## 3. 失败定位：专用工具问题，不是已证明的产品问题

### 3.1 首因：错误地要求直接父子关系

原始结构化首因保留为 `READINESS / IdentityError`，没有追加伪造的原始错误码。结合冻结源码、同次 PID 证据及后续只读核查，定位到 s1 工具的这一额外判断：

```javascript
if (local.parentPid !== launchPid) throw new IdentityError(...);
```

实际进程链为：

```text
pg_ctl.exe 60380
└─ cmd.exe 36212
   └─ postgres.exe 60768
```

初次就绪检查已取得 PostgreSQL 的匹配身份，但其父 PID 是 36212，不是启动器 60380，因此上述判断拒绝进入 SQL 查询。17:32 的只读检查以及补充停止前的两次校验均取得了中间 `cmd.exe` 的 PID、父 PID、命令目标、创建时间及所有者，确认该实际链路。

**这是本批新增 s1 工具把 Windows 启动链假设得过于简单造成的拒绝。** 没有发现或修改平台产品逻辑。本轮没有修改这条判断后重跑验收；冻结输入和原始失败保留。

### 3.2 首轮收尾：全局进程查询不完整

自动收尾的第一次目标核验成功；停止前第二次完整检查返回 PostgreSQL 进程数 20、信息不完整 1，触发：

```text
EnvironmentInspectionError
code = IDENTITY_QUERY_INCOMPLETE
queryStage = postgres
```

当时未执行 stop，正确保留了 `attempted=false / completed=false / CLEANUP_FAILED`。原报告没有单独记录缺失信息的具体 PID 和字段，不能倒推出已确认的具体原因。后续独立只读检查为 19 个进程、信息不完整 0；这证明后续可重新完整核验，不会把前一次不完整结果改成通过。

### 3.3 额外未解决问题：终末查询出现异常长的墙钟耗时

原最后一次查询配置了 15 秒超时，但实际记录：

- 开始：16:30:08.677。
- `elapsedMs=3602988`（约 60 分 3 秒）。
- `timedOut=true`，最终终止已确认。
- 原结果落盘：17:30:13.960。

不能把配置的 15 秒写成此次实际限时保证已经通过，也不能未经证据将长时间差归因于系统休眠、执行器挂起或某一特定原因。本轮没有修改系统电源/安全设置，未继续扩展环境诊断；该问题列为工具/执行环境待处理项。启动拒绝的首因发生在此前，后续长耗时不覆盖首因。

## 4. 补充正常关闭：独立记录，不改写失败

用户已授权失败后的必要收尾；工具流程又单独批准了精确目标的只读核查和首次正常停止。本次没有放宽产品规则、重试启动或补跑 SQL。

补充收尾工具先绑定原 `result.json` 的 SHA-256，并核验原记录中的 PID、父 PID、启动时间、集群号、输入和工具指纹。随后两次检查：

1. 当前执行上下文仍是获批 Arnold 令牌；CIM、进程和监听信息完整。
2. PostgreSQL 确为 PID 60768、父 PID 36212、同一启动时间和所有者。
3. 中间进程确为 `C:\Windows\System32\cmd.exe`、父 PID 60380、同一目录和所有者；时间与本次启动一致。
4. PID 文件、可执行路径、物理集群号及唯一回环监听一致。

17:35:48 执行的唯一停止命令：

```powershell
& 'D:\ITsoftware\PostgreSQL\bin\pg_ctl.exe' stop `
  -D 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\pgdata' `
  -m fast -w -t 30
```

实际退出码 0，286 ms，输出 `server stopped`。服务器日志 17:35:49.012 记录正常关闭；17:35:55.994 补充证据完成。补充收尾总耗时约 29.8 秒。

关闭后重新完整查询，目标 PID、父命令 PID、后代、PID 文件和监听均消失，原 5432 监听仍是 PID 10452。原主执行器最后返回退出码 1，补充停止执行器退出码 0，二者分别保留。

- 原 `result.json`：**FAILED / 原自动收尾失败**。
- 新 `checked-stop-result.json`：**PASSED_CLOSEOUT_ONLY / STOPPED / normalShutdownProven=true**。

两个结论不矛盾：一次验收可以失败，而随后完成安全的正常关闭。此处是 s1 的正常关闭证据，**不用于改写 r7 或其他历史批次的收尾结果**。

## 5. 源码、测试及证据对应关系

分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。没有提交、合并或推送。

### 原 s1 输入和无数据库检查

源码快照：`target/platform-s1-tool-validation-20260914/a1/source`。

输入 ID：`ee14690dca2f12bf0dddaf7f29410053d670a04a9701756650f8b93c324dc2de`。

949 个文件相对上批增加：

- `src/test/scripts/platform-s1-start-stop.mjs`：固定单次目标、上下文、87/3 拒绝、阶段及收尾编排。
- `src/test/scripts/platform-s1-start-stop.test.mjs`：33 项无数据库用例。
- `src/test/scripts/platform-s1-preparation.mjs`：新目录封存、校验和验证工具。

旧源码变化 0。既有公共脚本、产品 Java、业务测试方法、迁移、前端、公共 POM 和默认数据源均未修改。

正式本轮测试：生命周期 35、身份前置门禁 40、s1 专用 33，共 **108 项通过**；三项新脚本语法检查通过。新输出为 `target/platform-s1-tool-validation-20260914/a1/verification`，实际命令、数量、退出结果和 TAP 哈希在 `no-database-results.json`。先行开发试跑不重复累计。

本批不需要 Java 编译，没有新业务 JAR/classes；108 项测试不是迁移、事务、并发、SQL 身份或平台业务验收。

### 补充收尾工具独立追溯

后续为必要收尾增加 `src/test/scripts/platform-s1-checked-stop.mjs`，未修改原 s1 949 个输入文件。补充工具执行前完成语法检查及 **8 条无数据库守卫断言**；这 8 条是补充检查，不拼接为原 108 项测试或数据库成绩。

补充工具源码另存 `docs/releases/platform-start-stop-validation-20260914/s1/checked-stop-source.mjs`，SHA-256：

`56d13b8e827eca00ef74df1acab2a23b04a25d80e8c3f84245c40b246b1a3202`

补充工具只接受已固定的原失败结果和已观测目标，只能执行 stop，不能初始化、启动或 SQL 连接；完整父链约束用于这次收尾，没有用来宣布原 SQL 验证通过。

### 关键证据哈希

证据目录：`docs/releases/platform-start-stop-validation-20260914/s1/`。

| 文件 | SHA-256 |
|---|---|
| `input-manifest.json` | `c413a97babca3317acab91b3b6d6ab5e1664e2167fc7fa33fc0c9fee42375be0` |
| `no-database-results.json` | `b0f8fd9e171ac6ff8d0c7957bd0a144cb31e8308b3a36240965f12a75f26a2f0` |
| 原 `result.json` | `f7ccfb905ae421e743c6ac3f9b116a36210ae090a4c4b4d2988732ca62c2d0fd` |
| `initdb.log` | `fa0f406f90c6d96316ed5da72f293efadcab372975507718ebd6fb638029c850` |
| `pg-start.log` | `46038efb813fbca18a92272496901190638dea3f97b51f7af0daa1ca2eb13818` |
| `checked-normal-stop.log` | `3589d2bd01f8295cc7c9ec3f5e07625fcd347ccb6f33f2d80edafe5f5217c1fb` |
| `checked-stop-result.json` | `28d1ec55d3755f3ce2ecf5e0e387cdfd9fabcfe04c896d09daef76b855128514` |

最终服务器日志保留于新 s1 根目录，SHA-256 为 `b13b62c75b27e12799fd21c9b216a68be7dca8d8aaa64bf727897cb03e8cb4c5`。日志包含正常 fast shutdown 时逻辑复制后台进程结束的消息，不将其单独误判为启动错误。

结束后再次校验原 s1 输入和工作区：949 个文件匹配；再次逐项检查 r2～r7 的 **47,808 个历史文件，全部未变**。临时明文密码文件、postmaster.pid 均不存在，s1 数据目录保留，r8 目录未创建。

## 6. 未覆盖项与建议下一批范围——待确认

已确认：在本次明确批准的沙箱外上下文，PostgreSQL 18.6 能完成这一次空集群初始化、实际启动，以及在完整身份核查后正常关闭；本次启动/停止日志没有再次出现 87/3。

仍未确认：

1. 原 s1 最小自动流程的端到端通过，尤其是 SQL 只读身份核验。
2. 长时间墙钟延迟的原因及异常环境下的限时效果。
3. 全局进程清单偶发不完整的具体字段/进程原因。
4. 未提权 Arnold 上下文的可用性，以及 r7 错误 87/3 的完整底层原因。不能从这次未复现推导为所有环境都已修好。
5. 所有迁移、权限、事务、并发和 1/42/63 完整业务验证——本批均未执行。

建议下一批先限定为**专用工具的无数据库修正与异常诊断**：准确支持有证据的 `pg_ctl → cmd → postgres` 链路；记录查询不完整的定位信息但不把失败当不存在；核对墙钟/超时异常及独立收尾机制。保留原业务约束和全部旧证据，不自动启动新集群。具体实施范围由用户另行确认。

修正后，如需再次最小实测，应另批批准新目录，不能复用/覆盖 s1；即使之后通过，也不自动启动完整 1/42/63 验收。

前端 TS2769/整包构建阻塞、邀请完整人工激活及最终恢复核对业务边界继续独立保留。没有租户 ERP 开发、真实账号/租户/数据库操作、迁移、现有服务变更或生产开放。
