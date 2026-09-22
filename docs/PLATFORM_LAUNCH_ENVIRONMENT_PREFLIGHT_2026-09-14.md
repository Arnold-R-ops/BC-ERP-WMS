# 专用启动环境核对与身份查询前置检查

日期：2026-09-14。范围仅为平台专用测试工具、操作系统只读核对及无数据库回归。

**本批完成；默认沙箱不满足身份查询条件，已获单独许可的沙箱外只读查询通过。尚未验证新的数据库启停，不是实库验收通过，不是发布候选版本，不可上线。**

## 1. 已确认事实与历史边界

### r7 四个环节的身份依据

| 环节 | 可确认的历史事实 | 不能补写的内容 |
|---|---|---|
| 初始化 | 原 `initdb.log` 明确记载文件由 `CodexSandboxOffline` 拥有；本轮只读 ACL 查询为 `ARNOLD\CodexSandboxOffline`。日志最终显示初始化成功 | 当时完整访问令牌未保存，不能用本轮令牌属性替代历史属性 |
| 启动 | 冻结脚本从同一 Node 执行器直接调用 `pg_ctl`，没有 `RunAs`、服务账户切换或提权步骤。原启动器退出码 1，错误 87/3 | 当时 `pg_ctl` 进程令牌及内部受限令牌创建失败原因未取得完整证据；没有经核实的服务器进程身份 |
| 就绪查询 | 同一执行器的既定调用是 PowerShell/CIM、物理身份查询及限定 `psql`；r7 未走到 SQL 就绪探测 | 不能声称就绪查询在某个数据库身份下成功执行；当时没有就绪 SQL 成绩 |
| 收尾 | 代码使用同一执行器调用进程查询；记录为 `CLEANUP_FAILED`，`attempted=false`、`completed=false`；未执行 `pg_ctl stop` | 查询失败时不能说“已正常关闭”，也不能以之后未发现残留替代正常关闭证据 |

“同一执行器”是对冻结源码调用链的核对，不是缺失历史令牌的补录。所有相关子进程通过 `runProcess → spawn(windowsHide: true)` 启动，继承相同父进程上下文；清除的是指定环境变量，并不是 Windows 身份切换。PostgreSQL 自身的令牌处理与父进程继承不是同一件事。

本轮新增发现：r7 的原始 `initdb.log` 开头也包含：

```text
initdb: error: could not create restricted token: error code 87
initdb: error: could not re-execute with restricted token: error code 3
```

其后仍完成初始化，不能只保留成功结尾而忽略上述错误。该日志 SHA-256 为 `3a10f96c4df36bc11a2a3126041d54f52dae4e11aee541ae76f5c34345ca6511`；原文件未修改。

r7 原结论仍为 **FAILED / STARTUP_UNVERIFIED / CLEANUP_FAILED**。r7 的只读未发现残留记录与正常关闭失败记录分别保留。

## 2. 本轮真实只读权限检查

使用实际链路：固定 Node `D:\JAVA\NODE\node.exe` 24.12.0 → 共用 `runProcess` → 固定 Windows PowerShell 5.1 → 本机 CIM / `GetOwnerSid` / TCP 监听快照 / 文件只读检查。没有调用 PostgreSQL 程序或连接数据库。

下表为**封存后的正式探针**结果，时区 Asia/Shanghai：

| 项目 | 默认沙箱，10:27:51 | 单独批准的沙箱外只读查询，10:30:30 |
|---|---|---|
| Windows 用户 | `ARNOLD\CodexSandboxOffline` | `ARNOLD\Arnold` |
| 受限令牌 `IsTokenRestricted` | true | false |
| 管理员组启用 / TokenElevation | false / false | true / true |
| PowerShell PID | 53192 | 37620 |
| 自身及父 Node 的命令行、PID、可执行路径、所有者 | CIM 拒绝，`0x80041003` | 实际可读，父子 PID、Node 路径和账户 SID 一致 |
| PostgreSQL 进程清单 | CIM 拒绝，数量未知，不能写成 0 | 10 个，命令行/路径/启动时间缺失 0 个 |
| 监听端口 | 可读，但所属 PID 的 CIM 查询失败 | 可读，5432 的 PID 10452 也完成命令行及身份读取 |
| 新拟议 s1 目录 / PID 文件 | 只读文件查询不存在；**不能抵消进程查询失败** | 不存在；55449 无监听、目标进程匹配 0 |
| 门禁结果 | `BLOCKED / IDENTITY_QUERY_ACCESS_DENIED` | `AVAILABLE`；对拟议 s1 目标为 `CONFIRMED_ABSENT` |

检查确实读取了运行中的 PostgreSQL 进程信息，不是仅依赖一个空结果或模拟测试。既有 5432 监听属于 PID 10452；其 Windows 所有者 SID 为 Network Service 对应 SID，未对该服务作任何操作。保存的是命令行可读性和 SHA-256，**不保存完整命令行**。

沙箱外实测通道恰好使用管理员组已启用的账户令牌。**没有因此证明数据库需要管理员权限，也没有证明未提权的 Arnold 上下文具有相同查询能力。** 本批没有执行 `RunAs`、更改 UAC、服务权限、ACL、执行策略或其他系统安全配置。

初始工具探针还记录了一次 `-File` 调用失败：Windows PowerShell 禁止执行脚本文件。随后采用原批次已有的内联 `-Command` 查询方式；没有使用 `ExecutionPolicy Bypass` 或更改策略。原失败证据 `sandbox.json` 保留，正式证据使用另一个文件名，没有覆盖它。

实际只读探针结果并不证明：

- 沙箱外 `initdb` / `pg_ctl` 能成功执行，或错误 87/3 一定消失。
- 将来 PostgreSQL 子进程的账户、目录、集群号、SQL 身份一定正确。
- r7 完成正常关闭。
- 普通非管理员令牌可用；该对照尚未实测。

## 3. 测试工具修正

本批修改三个已有测试工具文件，新增四个测试工具文件。均位于 `src/test/scripts/`：

| 文件 | 修改内容 |
|---|---|
| `platform-r7-database-batch.mjs` | 在实际入口接入统一初始化前置检查，先检查再写密码文件/调用 initdb；启动前再次检查上下文及目标；停止确认必须经过能力核验；未尝试启动不再用“端口空闲”伪造正常关闭 |
| `platform-process-lifecycle.mjs` | 保留启动首因、退出码和允许公开的 Windows 错误码；就绪错误、收尾错误分别记录，后续错误不覆盖首因 |
| `platform-process-lifecycle.test.mjs` | 新增 3 项启动首因与后续查询/收尾失败组合回归；原有 32 项保留 |
| `platform-windows-identity.ps1` | 只读 Windows 身份、令牌属性、父子进程及命令行可读性、PostgreSQL 清单、监听/PID 证据；明确区分拒绝访问、查询失败、信息不足、存在、不存在 |
| `platform-launch-preflight.mjs` | 共享查询及能力门禁、精确目标和上下文绑定、可靠缺失判断；PID 文件读取失败不得被当成文件不存在；另提供固定目标只读探针 |
| `platform-launch-preflight.test.mjs` | 40 项契约，覆盖实际入口接线、所有查询类别失败、目标占用、目录已存在、上下文变化、信息不足、超时/损坏输出及初始化不得先执行 |
| `platform-launch-preflight-verification.mjs` | 新目录封存、白名单变更检查、旧目录逐项哈希、从封存副本运行受影响的无数据库回归 |

门禁条件是**具备实际查询能力**，不是“必须启用管理员组”。初始化时绑定实际上下文；启动前不允许偷偷换账户/令牌。之后若查询权限丢失或信息不足，失败关闭，不降级成端口检查。

本轮只修改工作区中的测试工具。r7 原封存副本、失败结果、初始化目录保持原样；旧 r7 构建不满足新门禁指纹/数量，不能借此重新执行 r7。下一次执行必须另建获批目标并重新封存。

未修改产品逻辑、公共 POM、数据源默认值、权限/审计/迁移约束、94 个迁移 SQL 或 63 个业务方法。未增加或缩减业务覆盖目的。

## 4. 封存与实际验证结果

分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；没有提交、合并、推送。

工具验证源码快照：

`D:\ERP_WMS\BC warehouse\2G\target\platform-launch-preflight-20260914\a1\source`

946 个输入文件；相对本轮基线只改变上述三个既有脚本，新增四个脚本。源码快照 ID：

`b6e31e35017fde759a4e43963105cf730ee57f808f310d8909c687ceb35542b1`

这是**工具验证输入快照，不是 r8 后端验收快照或完整发布候选版本**。本批没有运行 Java/Maven 编译，没有生成新的业务 JAR/classes，也没有拿旧 classes 冒充新编译成果。

正式命令（本批已执行；其验证子命令不连接数据库）：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-launch-preflight-verification.mjs' baseline
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-launch-preflight-verification.mjs' seal
& 'D:\JAVA\NODE\node.exe' 'target/platform-launch-preflight-20260914/a1/source/src/test/scripts/platform-launch-preflight-verification.mjs' verify
& 'D:\JAVA\NODE\node.exe' 'target/platform-launch-preflight-20260914/a1/source/src/test/scripts/platform-launch-preflight.mjs' inspect sandbox-sealed
# 以下只读命令另经沙箱外权限批准，不能作为以后启动数据库的批准：
& 'D:\JAVA\NODE\node.exe' 'target/platform-launch-preflight-20260914/a1/source/src/test/scripts/platform-launch-preflight.mjs' inspect outside-readonly-sealed
```

| 无数据库套件 | 通过 |
|---|---:|
| 夹具归属隔离 | 24 |
| 进程生命周期 | 35 |
| 升级哨兵契约 | 65 |
| XML 结果解析器 | 67 |
| 1/42/63 编排契约 | 57 |
| 报告执行入口 | 60 |
| r7 绑定契约 | 8 |
| 新身份查询门禁 | 40 |
| **本轮正式合计** | **356** |

失败 0、跳过 0、取消 0。6 个 JavaScript 语法检查及 1 个 PowerShell 语法检查通过。生命周期回归包含短命的合成 Node/native 子进程，均为专用测试夹具，不是 PostgreSQL、HTTP 或现有服务。

新输出目录为 `target/platform-launch-preflight-20260914/a1/verification`。每个套件的命令、退出状态、实际数量、TAP 路径及 SHA-256 均在 `verification-results.json` 中。先行开发试跑的 40 项不重复累计。历史 Java 99 项、历史浏览器/Maven落盘探针、r7 的 412 项无数据库检查均不算入本批。

本批初始化 0、数据库启动 0、数据库连接 0、迁移 0、数据库业务测试 0。r7 的独立 1 项、顺序 42 项、完整 63 项仍为未执行；不能用本批 356 项补足。

### 证据索引

目录：`docs/releases/platform-launch-preflight-20260914/a1/`。

| 文件 | SHA-256 |
|---|---|
| `baseline.json` | `2bb2605ee29fd93ac0cc28dfde47d0597bbb81f6e381c54e889ba59b85e3bf2d` |
| `input-manifest.json` | `91f7a168fda16e0ad4c4c8fa6d9415ba23c43d777be6dbcc105e98014681d933` |
| `verification-results.json` | `a1fcdb27a88dc780bd69eabc4d2438268adf6cc96fe744c7131528f62ec9ee61` |
| `sandbox-sealed.json` | `d13c3970a42e788df1188fedb2e2cd333178b770500575068d64c6146c093df5` |
| `outside-readonly-sealed.json` | `902557f9007cba9cc6d073dc05398c733009867e271721bce6bd2bc8c52c1d27` |

两个正式权限探针记录了实际执行脚本指纹，均与封存清单吻合；调用参数和上下文不是凭经验填写。没有归档密码、验证码、JWT、MFA 密钥、恢复码或完整认证请求。

基线及结束复核覆盖 r2～r7 目录内 47,808 个文件，逐项哈希/清单一致。r7 顶层原报告哈希仍为 `87de7556d462757dedc809e7040f434d09da72ab8df48bc9394af812aa756217`；原 `database-result.json` 仍为 `4c5fc190dcc340db835d2e34293c8a07d5b120f7cef0f3a257046dca6a76df98`。

此外，实际调用 r7 **原冻结副本**的只读 `verify()`，其源码、依赖、工具及所引用历史清单完整复核通过，返回原输入 ID `0eb8b0f444609c0a1ff20e9da6bba42159f21925a8868555cd14fc37bcbfd7e9`；没有执行其 build/database 入口。该哈希核对不是把 r7 的业务结果改为通过。

本轮准备工具第一次枚举把 r2 文档位置误写为 `/r2`，读取时明确报 ENOENT；当时尚未写入基线或创建工具输出目录。修正为实际根目录文件后重新核对，未补建、搬动或跳过 r2 原证据。该准备错误不计为数据库运行失败，也不省略最终历史清单检查。

## 5. 仍未确认的原因及风险

1. 当前默认沙箱实际无法完成 CIM 身份查询，不能在其中继续数据库启动。仅延长超时没有意义。
2. 已确认沙箱外只读能力；**尚未确认该上下文的 initdb、pg_ctl、就绪和正常关闭**。错误 87/3 的完整底层原因仍未查明，不能断言切换上下文必然修好。
3. 未提权 `ARNOLD\Arnold` 没有实测。本工具不要求管理员权限、不自行提权；如指定普通令牌，应先在该上下文用同一探针只读验证，未通过即不启动。
4. 若未来真实进程查询、身份或关闭失败，必须停止后续验收并保留首因；无法证明正常关闭时仍记未核实，不按名称/端口强杀，不修改安全设置。
5. 前端 `addressLength.audit.test.tsx` 的 TS2769 继续交接，未修改、删除或排除；没有本轮正式前端类型检查/整包构建通过证明。邀请完整人工激活仍待验。恢复核对仍为缺单报告＋人工核验修改/取消/退款，最终业务边界未确认。

## 6. 下一批最小启停验证方案——待单独批准

**只做一次新集群启停，不创建 r8，不复用 r7，不执行迁移或 1/42/63 业务套件。** 以下均为拟执行计划，本轮没有执行。

### 固定目标、执行身份与数据来源

- 新根目录：`D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1`。
- 数据目录：上述根目录的 `pgdata`；日志/证据为该根目录中的专用文件。2026-09-14 10:34 的检查为根目录不存在；执行前必须重新检查，存在即停。
- 唯一监听：`127.0.0.1:55449`；任何地址上的该端口被占用即停，不自动换端口。
- Windows 账户建议使用沙箱外当前 `ARNOLD\Arnold`，所有初始化、启动、只读查询及收尾由同一个专用 Node 进程及其子进程执行。已观测该通道令牌 `restricted=false / administratorEnabled=true / elevated=true`；**审批须知悉该实际上下文，不是默认要求管理员或授权自行提权**。不用 `-Verb RunAs`，不改账户/系统策略；开始时重查身份并在本轮内固定。
- 若要求改用未提权 Arnold：先在该上下文只读检查，不沿用已提权上下文的通过结果；该选择改变执行条件，不能静默切换。
- 仅全新空集群、默认维护库 `postgres`、初始化 SQL 角色 `p3_s1_initializer`。没有业务库、迁移/运行角色、用户业务夹具、旧验收账号或数据复制。
- 随机生成一次性强凭据，只供本次子进程；临时密码文件为根目录 `init-password.ephemeral`，使用后移除且不归档。SCRAM，不使用 trust。专用目录内仅允许本次角色从 IPv4 回环访问维护库，其余拒绝。
- 需要另行批准**沙箱外启停执行**；本轮只读许可不覆盖这一步。需要专用执行器时，仅适配该最小方案并先无数据库回归，不提前启动。

### 精确底层命令

下列参数由同一个专用 `runProcess` 调用链构造，不使用交互式 `Start-Process`，不借用旧 r7 执行器的授权开关。固定 PostgreSQL 18.6；工作目录限定新 s1 根目录，输出独立落盘。

初始化前，先在预定上下文调用本批 `inspectWindowsEnvironment → initializationPreflight`：自身/父进程查询成功、全部必需信息完整、s1 目录不存在、目标进程/端口/PID 文件均不存在，才允许创建目录及凭据文件。任何查询拒绝、不完整或指纹漂移即停。

```powershell
& 'D:\ITsoftware\PostgreSQL\bin\initdb.exe' `
  -D 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\pgdata' `
  -U p3_s1_initializer `
  '--pwfile=D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\init-password.ephemeral' `
  --auth-host=scram-sha-256 --auth-local=scram-sha-256 --encoding=UTF8 --locale=C `
  -c listen_addresses=127.0.0.1 -c port=55449 -c max_connections=10 `
  -c log_statement=none -c log_min_error_statement=panic -c log_parameter_max_length_on_error=0

& 'D:\ITsoftware\PostgreSQL\bin\pg_controldata.exe' -D 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\pgdata'

& 'D:\ITsoftware\PostgreSQL\bin\pg_ctl.exe' start `
  -D 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\pgdata' `
  -l 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\postgres-private.log' -w -t 30

& 'D:\ITsoftware\PostgreSQL\bin\psql.exe' -X -w -h 127.0.0.1 -p 55449 `
  -U p3_s1_initializer -d postgres -v ON_ERROR_STOP=1 -q -A -t

& 'D:\ITsoftware\PostgreSQL\bin\pg_ctl.exe' stop `
  -D 'D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260914\s1\pgdata' -m fast -w -t 30
```

`psql` 仅通过子进程 stdin 执行下列身份查询，不执行迁移/业务 SQL；`PGPASSWORD` 仅置于本次子进程环境，不放入参数、终端输出或报告。清除继承的 PG/应用连接配置，固定 `PGCONNECT_TIMEOUT=2`、`PGOPTIONS=-c statement_timeout=2000`，不得回退：

```sql
select json_build_object(
 'database',current_database(),'user',current_user,
 'port',inet_server_port(),'address',host(inet_server_addr()),
 'listenAddresses',current_setting('listen_addresses'),
 'directory',current_setting('data_directory'),
 'clusterId',(pg_control_system()).system_identifier::text);
```

### 就绪、失败及关闭判据

1. 初始化命令需实际成功，记录所有警告/错误；离线读取并固定新集群号，不能复制旧集群号。初始化后、启动前再核验账户上下文、端口及目标无残留。
2. 启动器退出与输出管道闭合分别判断；采用独立输出文件。启动器预算 37 秒、就绪预算 10 秒；就绪必须同时匹配 PID 文件、OS PID/父子关系、启动时间、可执行路径、确切 `-D`、Windows 所有者、仅一个回环监听、物理集群号以及上述 SQL 的全部身份字段。
3. 任一条件失败即停止后续验证，保留首因；独立收尾预算 45 秒，不使用已取消的启动信号。`pg_ctl stop` 子命令预算 32 秒；停止前重新核验同一个 PID/启动时间/目录/集群号，身份不符拒绝操作。
4. 只有真正执行正常 stop 且命令成功、已固定进程及其后代消失、PID 文件消失、55449 监听消失、所有查询仍成功，才记 `STOPPED`。启动前就没有资源仅记“确认未启动/未观察到资源”；查询失败记“未核实”，不补写正常关闭成功。
5. 不连接 5432，不操作任何原服务。前后记录 5432/8080/5173/55439/15173/18091 等现有监听；发现变化仅报告，不替它们启动或停止。保留新目录及脱敏证据，不自动删除/复用，不自动创建下一轮。

**该批停止点：真实启停及身份链路是否可用、准确失败原因、正常关闭证据明确；不进入完整数据库验收。** 通过后再提交独立完整验收范围，由用户另行授权同一新产物的 1 → 42 → 63 三阶段；累计 106 次执行不等于 106 个不同方法，不拼接历史成绩。

本轮需要确认的是上述最小 s1 批次及沙箱外实际执行上下文；不请求顺带授权完整重跑、真实数据库、前端/HTTP 服务或上线。
