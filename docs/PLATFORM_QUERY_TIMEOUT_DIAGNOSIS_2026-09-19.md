# r10 查询／超时延迟诊断及测试工具取证修复

日期：2026-09-19（北京时间）。本轮没有创建 r11、初始化／启动／连接 PostgreSQL、执行迁移或业务测试，没有更改现有服务、电源策略、UAC 或日志设置。

**结论：发现与异常窗口高度重合的 Modern Standby 电源事件，但尚未证明唯一根因或根因已经解决。取证修复已接入实际入口，29 项新增针对性测试通过；受影响回归出现 4 项旧测试夹具分类失败，已停止，不能宣告本批全部通过。**

## 1. 已确认事实与未知项

### 1.1 r10 原始时间线

所有时间为北京时间 2026-09-18。原始 `database-result.json` 和 `failure-review.json` 保持不变。

| 时间 | 实际证据 |
| --- | --- |
| 23:07:16.909 | System / Kernel-Power 506，记录进入 Modern Standby，原因为 Idle Timeout |
| 23:07:41.760 | r10 命令 267 开始，父 Node PID 45940，查询 PowerShell PID 46128 |
| 23:07:41.763 | 父进程记录 TIMEOUT_ARMED，预算仍为 15,000 ms |
| 23:07:41.976 | Kernel-Power 566，PreviousSessionType=1、NextSessionType=2 |
| 23:14:54.076 | 父进程记录 TIMEOUT_FIRED，已耗约 432,316 ms，回调迟到约 417,315 ms；随即发出终止请求 |
| 23:14:54.107 | 父进程收到查询子进程退出及管道关闭事件，终止已确认 |
| 23:14:55.291 | Kernel-Power 507，记录退出 Modern Standby，原因为 Input Keyboard |

电源事件确认了该窗口确实发生低功耗进入／转换／退出，与延迟区间高度重合，支持宿主电源状态是重要环境因素。这比先前仅凭计时推测更具体，但保留以下限制：

- 506 事件之后仍有正常查询执行，不能把整个 506→507 区间等同于所有进程都停止运行。
- 超时回调比 507 事件时间早约 1.216 秒，不能写成“先记录恢复，再触发回调”的精确因果链。
- 父进程记录的 exit 时间是收到通知的时间，不是独立的内核进程终止时间证据。
- 没有逐进程调度／挂起跟踪，不能确定父子进程各自受影响的准确区间，或排除独立查询阻塞、父事件循环阻塞及资源调度因素。

冻结代码使用异步 spawn 和 setTimeout。单纯的 WMI 子进程返回慢，不足以解释父定时回调同时晚执行；beforeSuite 的同步产物指纹检查发生在该查询开始前，也不能直接解释 TIMEOUT_ARMED 后的延迟。未将此认定为产品逻辑缺陷。

### 1.2 有限系统日志取证

严格只读查询 UTC `2026-09-18T15:05:00Z`～`15:17:00Z`，即批准的北京时间 23:05～23:17：

- System 中指定电源／时钟相关提供程序：5 条。
- WMI-Activity/Operational：19 条；部分含客户端 PID／错误码，没有直接匹配 46128 或 45940 的记录。其他 PID 的错误不能认作本次查询原因，也不能据此排除 WMI 问题。
- TaskScheduler/Operational：日志未启用，无匹配事件。未启用不等于没有调度异常；任务计划程序日志也不是 CPU 调度跟踪。
- Diagnostics-Performance/Operational、Kernel-EventTracing/Admin：启用，但窗口内无匹配事件。不表示系统全无异常。

没有读取窗口外历史事件、原始 WMI 查询、任务名称或认证材料。事件读取共三次：首轮最小字段；第二次补读 UserData 中的 PID／结果码及指定电源事件描述；第三次仅修正当前 PowerShell 子进程 UTF-8 输出。前两份记录原样保留，未修改系统全局编码。最终可读事件以 `events-utf8.json` 为准，全部事件均在相同授权窗口。

### 1.3 三次同路径只读查询

使用同一个 `inspectWindowsEnvironment → PowerShell inline → ProcessProof` 路径及原 15 秒预算。实际上下文每次均核验为 `ARNOLD\Arnold`，elevated=true、administratorEnabled=true、restrictedToken=false。未修改权限或使用 RunAs。

| 次数 | 实际耗时 | 超时回调 | 退出与管道 |
| --- | --- | --- | --- |
| 1 | 6.587 秒 | 未触发，迟到值不适用，不能写成 0 | 退出 0，终止／完整输出已确认 |
| 2 | 5.166 秒 | 未触发，迟到值不适用 | 同上 |
| 3 | 4.721 秒 | 未触发，迟到值不适用 | 同上 |

三次均读取原 r10 目录的 OS 信息，没有 SQL／数据库连接；均确认 postmaster.pid 不存在、目标进程匹配数及 55449 监听数为 0。查询执行次数到 3 即停止，没有追加试跑，也没有重启 r10。这是当前状态证据，不改写 r10 历史结果。

**本轮未复现约 432 秒延迟，尚未证明根因解决。**

## 2. 实际工具修复及接入

仅修改测试工具，产品、Java 测试业务正文、数据库约束／迁移和前端未改。

| 修改文件 | 本次内容 |
| --- | --- |
| `platform-process-lifecycle.mjs` | 新增白名单查询执行元数据投影、脱敏 failureDetails、失败步骤包装；不改 runProcess 调度／终止逻辑，不增加预算 |
| `platform-launch-preflight.mjs` | 查询异常保留命令索引、预算、实际计时、PID／父 PID、退出与终止结果；查询解析／权限／身份失败均保留类型与错误码；拒绝 null／数组／标量报告，避免 TypeError 丢失取证 |
| `platform-r6-stages.mjs` | 身份查询错误区分为 ENVIRONMENT_PRECONDITION_ERROR／ENVIRONMENT_CLOSEOUT_ERROR，不再归为夹具问题；未知异常保持 PRECONDITION_ERROR／CLOSEOUT_ERROR；业务首因与收尾追加错误分开保存 |
| `platform-r10-database-batch.mjs` | 实际 command 返回本次命令索引；前置产物、身份查询、夹具读取／校验分别标注步骤；afterSuite 保存脱敏结构而非任意异常消息 |
| `platform-report-runner.test.mjs` | 已给一个既有合成夹具异常明确标记类别，保留原断言；另两处未完成，见第 3 节 |

新增 5 个专用测试／诊断文件：`platform-query-failure.test.mjs`（15 项）、`platform-query-evidence.test.mjs`（14 项）、`platform-query-event-window.ps1`、`platform-query-diagnostics.mjs`、`platform-query-repair-verification.mjs`。

实际链路为：`commandIndex → queryExecutionDetails → EnvironmentInspectionError → 阶段 failureDetails → firstFailure → 最终批次结果`。针对性测试从真实批次导出的 `runR10Acceptance` 进入，并验证身份、首因和完整阶段未执行计数。没有仅测试一个未接入的新格式。

记录限于类型、错误码、失败步骤、命令索引及白名单计时／PID 等字段，不复制 message、stack、原始命令参数、命令行、输出或认证信息。字段和数组有界。beforeSuite 失败仍不运行该 suite 的 afterSuite；批次 finally 的独立环境收尾保持原有机制，没有虚构该套件已经收尾。

本次只改当前工作区工具，旧 r10 冻结源码、报告和指纹未改。因此新字段仅用于以后另行批准的执行；r10 历史未捕获的原始异常对象仍未知，不能倒填。

## 3. 无数据库验证：准确成绩与停止原因

固定 Node 24.12.0。冻结 107 个测试脚本／夹具文件，逐命令核对工作区与冻结副本；新输出目录 `target/platform-query-diagnostics-20260919/a1`。本轮没有 Java 编译、Maven 调用、迁移或数据库业务测试，不复用旧 classes 充当新验收证据。

| 检查 | 成绩 |
| --- | --- |
| 修改模块语法检查 | 6/6 通过 |
| 新增取证／真实入口针对性测试 | 29/29 通过 |
| 生命周期／前置／父链／查询／真实合成计时／阶段编排等 7 套件 | 222/222 通过 |
| 报告执行器回归 | 60 项已执行，56 通过、4 失败 |
| 停止后未执行 | s2 35 项、r10 绑定 18 项、r10 环境 12 项，共 65 |
| 总计 | **计划 376，执行 311：307 通过、4 失败，跳过／取消 0，65 未执行** |

上述计数不包含独立审阅者较早跑过的 57 项，也不拼接 r10 历史成绩。原自动结果只汇总失败前的 9 个成功套件共 251 项；失败套件在命令退出 1 后未进入 suites 数组，其原始 TAP 完整保留。`regression-review.json` 单独补充该套件 56／4 和准确总计，原 `regression.json` 的 FAILED 不改写。

四项失败为 `platform-report-runner.test.mjs` 的 TAP #45、#46、#47、#55，对应两处合成 afterSuite 回调（约第 224、309 行）仍抛普通 Error，却断言该异常必然是 FIXTURE_CLEANUP_ERROR。新逻辑将没有明确归属的错误记为 CLOSEOUT_ERROR；这暴露本次对旧测试注入的适配遗漏，不是产品业务断言失败。

建议修正为：只给这两处明确模拟夹具收尾的异常增加 `category: 'FIXTURE_CLEANUP_ERROR'`，保留全部四项首因、类别和业务计数断言；不把生产分类退回“所有未知错误都是夹具”，不删除测试或放宽断言。**失败后尚未实施此修正，也没有自动重跑或创建下一轮输出。**

真实无数据库合成子进程计时的 8 项在本次通过：正常退出、卡住、外部取消、故意阻塞 JS 调度、独立收尾、收尾超时和首因保留。200 ms 超时用例实测约 200 ms 触发、回调迟到约 0.185 ms，约 206 ms 确认结束；故意延迟调度用例准确记录约 134 ms 迟到。它们证明取证会记录实际延迟，不证明 r10 根因或所有宿主异常下的硬墙钟保证。

## 4. 指纹、保护核验与证据

- 输入 ID：`80d7f06e2968b7aba17c9fd95130f45d3bf881cab461cc04c974e1ffb6f463ff`
- `regression-input.json` SHA-256：`16d4c41daa211ccfba221a1517d4e882f56a509f9a71cc6e310c5165341a024d`
- 本次原始 FAILED `regression.json` SHA-256：`360d15dd844a3bac0674e7d1ca7facc2918fc63fbf01dbe7de7d6ad4d8066777`
- 最终事件补证 SHA-256：`d07e7db8ee7b27e7bce77eaff591fd55451d9bf3858ef30420cc70e6b0a36f2a`
- r10 原始 FAILED SHA-256 仍为：`bc50631ceff9da530d662233a8c0f3d2ae89758e0dadab8afddb444ea00580ff`

开始／结束核对的 1378 个受保护文件（产品、全部 Java 测试／测试资源、前端源码、既有发布证据）指纹及清单一致。所有 63 个业务方法本轮没有改动。查询／收尾预算及 runProcess 正文与 r10 冻结版相同。当前工具源码和新输出有独立指纹；没有更新历史快照或对外提交。

证据在 `docs/releases/platform-query-diagnostics-20260919/a1`：

- `events.json`、`events-detail.json`、`events-utf8.json`：有限只读系统事件及过程记录。
- `query-1.json`～`query-3.json`：三次 OS 查询实际上下文、计时、退出和输入文件指纹。
- `regression-input.json`：固定计划、107 文件指纹及工具版本。
- `regression.json`、`regression-review.json`：原始 FAILED 与单独计数复核。
- `before.json`、`after.json`：保护清单和实际修改指纹。
- `target/platform-query-diagnostics-20260919/a1`：冻结工具副本、原始 TAP 及合成子进程实测证据。

## 5. 停止点及下一次可控执行建议

本批到此停止。取证增强已有局部通过证据，但受影响回归尚未全绿，不作为完整工具修复验收通过。

下一步建议仅授权：补齐第 3 节两处测试异常标记，并在新的、非数据库输出目录重跑这 376 项无数据库计划；保留本次失败记录，不再追加同路径真实 OS 查询。不得直接接续那 65 项并拼成全通过，应对同一新源码重新核对完整计划。

这一步通过后，是否重跑实库仍另行确认。若批准，建议一次性固定新目录和输入，并沿用完整 1／42／63 顺序及现有预算；执行期间由用户确保主机处于唤醒状态、不要主动睡眠或锁屏。此为运行安排，不是宣称能保证硬性时限，也不是修改电源策略的授权。

若需要程序级临时防待机、独立监督进程或调度跟踪，应先提出具体权限、日志范围和停止方式，再单独批准；本批没有实施。任何异常仍保留第一原因并独立收尾，不以提高超时或降低身份检查通过。

前端构建阻塞、完整邀请人工验收、恢复核对最终业务边界和生产上线条件继续独立保留。本批没有 r11、真实账号／租户／数据库操作、ERP 开发、服务变更、提交／合并／推送或生产开放。
