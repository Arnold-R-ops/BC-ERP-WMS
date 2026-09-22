# 专用 JVM 参数与停止后 PID 复用判定——无数据库验证记录

日期：2026-09-20；本次执行于北京时间 22:57:34 结束，失败后只读指纹核对于 23:00:02 完成。

**本批 FAILED，已停止，不能交付为工具验收通过。** 参数及停止后判定修正已写入专用测试工具并重新封存编译；既有门禁通过，新增 35 项中 30 项通过、5 项失败。失败原因是本批新增的合成环境夹具漏掉模拟身份前置检查，不是新发现的产品或真实启动结论。原始输出和 FAILED 保留，没有修补后重跑。

整个批次未启动或连接 PostgreSQL、Spring 服务或前端，没有创建下一轮运行环境，没有临时唤醒、系统策略修改、真实账号操作、产品构建或 git 提交合并。真实子进程仅为编译器、语法检查、既有无数据库合成测试及 JDK-only 探针。

## 1. 本批实际改动与接入

修改两个专用工具：

- `src/test/scripts/platform-b1-batch.mjs`：实际后端启动走 `launchBrowserBackend`，由共享构造函数生成命令；启动前把实际参数与封存命令逐项比较。新门禁 35 项必须全通过，未来启动入口才接受准备结果。
- `src/test/scripts/platform-b1-prepare.mjs`：封存命令走同一构造函数；封存新增依赖、JDK 探针与门禁；对 r11 派生的浏览器适配器只接入停止后核验分支。

新增四个专用文件：

- `src/test/scripts/platform-browser-runtime-safety.mjs`：共享 JVM 参数、停止后进程分类和明确的适配器接入变换。
- `src/test/scripts/platform-browser-runtime-safety.test.mjs`：35 项明确测试，包含真实 JDK 探针及实际环境／停止入口的合成调用。
- `src/test/scripts/platform-browser-runtime-safety-verification.mjs`：本批一次性无数据库封存、编译与验证入口。
- `src/test/java/com/wms/browseracceptance/PlatformJvmEntryProbe.java`：仅 JDK，类初始化和 main 入口检查系统属性，仅输出布尔值与参数个数。

实际 JVM 参数以 `-Dspring.devtools.restart.enabled=false` 开头，在 `@runtime.args` 与主类之前。封存入口和实际 launch 函数共用该构造；未删除 DevTools 依赖、未修改公共配置、未放宽原 Harness 的零参数／工作目录守卫。

共享的 `platform-process-ancestry.mjs`、`platform-process-lifecycle.mjs`、`platform-launch-preflight.mjs` 和 `PlatformB1Harness.java` 与 b3 封存内容逐字节一致。没有修改产品、迁移、业务断言或 ERP 功能。

## 2. 停止后判定的实现边界

只在正常停止命令回执明确成功后启用新分支：退出 0、命令及输出完成、终止确认、无超时／中止／spawn 错误，且停止命令来源、时间和后续报告匹配。没有正常停止回执时继续走原严格父链逻辑。

新增分类区分：

| 分类 | 判据／行为 |
| --- | --- |
| ORIGINAL_PRESENT | 创建时间及完整原身份一致；不得宣称已关闭 |
| CONFIRMED_ABSENT | 完整查询明确返回该 PID 不存在 |
| PID_REUSED | 原身份完整，新进程身份完整，创建 ticks／UTC／epoch 自洽，创建晚于原进程且不早于停止命令开始 |
| UNCONFIRMED | 查询失败、字段缺失、时间不自洽、同一创建时间却身份不同、较早创建等；拒绝成功结论 |

PID_REUSED 本身不是收尾成功：还要同时满足正常停止回执、确切数据目录无目标进程、无监听、PID 文件不存在、无 PostgreSQL 后代残留、原 server 与 relay 均已不存在或被确证复用。新进程的完整身份与原身份分别留证；没有针对新 PID 的 kill/stop 操作。

启动前与停止前继续复用原 `verifyLauncherAncestry`、两次严格 observe／assertOwned；不会将启动前的 PID 复用视为安全。额外收集原完整 server／relay 证明仅供停止后对照。原正常停止命令不变。

当前仍采用保守残留判断：即使某个 PID 被证实复用，只要 PostgreSQL 目录／后代／监听等检查仍显示残留或无法分清关系，就不会判定完成。未承诺所有 PID 复用场景都自动成功。

以上是已写入的实现规则；**实际环境适配器完整无数据库调用验证尚有五项未到达目标分支，不能据此称接入验收完成。**

## 3. 新冻结副本和实际结果

准备根：`D:/ERP_WMS/BC warehouse/2G/target/platform-browser-runtime-safety-repair-20260920/a1`。

证据根：`docs/releases/platform-browser-runtime-safety-repair-20260920/a1`。

唯一执行命令（历史记录，不是重跑授权）：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-browser-runtime-safety-verification.mjs' no-database-runtime-safety-only
```

固定 Node 24.12.0、Java 17.0.17；必要权限审批用于历史受保护目录读取及本批验证。预算：语法每项 15 秒、独立编译 300 秒、Java 契约 60 秒、Node 每套 120 秒、JDK 探针每个 10 秒；未增加预算。

| 检查 | 本次实际结果 |
| --- | --- |
| 冻结文件／新 class | 49／18 |
| 语法 | 38/38 通过 |
| 独立编译 | 通过，仅编译测试包装器／探针 |
| Java 契约 | 16/16 通过 |
| 既有 Node 门禁 | 231/231 通过，本次完整重跑，不拼接旧成绩 |
| 新门禁 | 35 项执行，30 通过、5 失败；跳过／取消／待办均 0 |
| Node 合计 | 266 项，261 通过、5 失败 |
| 本批服务／数据库 | 0，未运行真实环境流程 |

既有 231 项 = 生命周期 35 + 身份前置 40 + 父链 33 + 查询 16 + 环境 12 + 浏览器边界 19 + 依赖完整性 13 + 启动安全 25 + 诊断 38。

三次新真实 JVM 均完成退出和输出排空：

| 探针 | 系统属性类初始化／入口结果 | 退出 | 耗时 |
| --- | --- | --- | --- |
| 正向实际 JVM 前缀 | true／true，参数 0 | 0（预期） | 107 ms |
| 移除该属性的负向对照 | false／false，参数 0 | 2（预期） | 92 ms |
| 安全参数个数对照 | true／true，参数 1，无参数内容输出 | 2（预期） | 91 ms |

这三次复用实际 `launchBrowserBackend` 产生的 JVM 参数前缀，仅替换 main／classpath 为新编译的 JDK-only 探针，不加载 Spring／JDBC，不调用实际 Harness。后两次的退出 2 是明确预期，不是失败。

原 38 项诊断门禁另包含 16 次真实合成 Java 异常进程，均通过。本次真实 Java 合成进程累计 19 次，但不是 19 次后端启动。

## 4. 五项失败定位与未完成部分

五项失败均来自新套件第 29—33 项：

1. 实际 lifecycle + final closeout 的 server PID 复用路径；
2. 实际适配器的 relay PID 复用路径；
3. 实际适配器拒绝原进程仍存在；
4. 实际适配器保持查询失败为未确认；
5. 实际停止前严格拒绝复用 server、不得发出停止命令。

相同异常：`TypeError: Cannot read properties of undefined (reading 'sid')`。

位置：冻结 `platform-b1-environment.mjs:69` 的启动回执绑定，以及新测试夹具 `platform-browser-runtime-safety.test.mjs:141` 的 started()。夹具直接 configure → adapter.launch → observe，遗漏真实执行链在 launch 之前通过 `environment.inspect` 初始化 context 的步骤。启动回执访问 context.sid 时失败，尚未进入本来要验证的停止后业务分支。

这是我新增的合成夹具准备不完整；没有理由为此放宽实际身份检查，也不是产品启动故障。应在测试夹具内按真实顺序补充模拟 inspect（数据仍来自可控 command，不执行 OS 查询），而不是给实际 context 填默认身份或跳过校验。

目前仅记录建议，**没有修补后再次执行**。本次 Node 命令非零退出、failure.json、完整 TAP、快照和 class 保留。其余同套件方法均实际执行，不能把这五项写成“未执行”；但它们预定的停止分支行为确实未覆盖。

门禁失败后，原验证流程的最终汇总／合成文件总扫描未执行。另行只读完成了源码、产物、来源、历史与产品指纹核验，以及三个 JVM 回执复核，单独记入 `post-failure-integrity.json`，不把它冒充原流程成功。

## 5. 指纹及历史保护

```text
输入聚合（49 文件）
234c7cc6cea20521da48ff25d7d88802c4ec99aa2db31dafb145621b1aa1c4ec
新编译 class 聚合（18 文件）
743bea36480531ee4a8a94fbed1f25b5781bb91bc5ffe344af59e1276d712941
input.json
7047ec58420e0a259e3ab44e306c89966ef590d3820eb47ba0a7148cd1cbcdd4
compiled.json
a1ca13d120701462176f9c9bd7569e42724845c24a6f8915e907acf3370ec47c
failure.json
96c19bcabf0353c349df63d10efdc19e44da089f9e9965fa0736cacecb8ea242
新门禁原始 TAP
444efbab397106606056aa8f791e1130bd9bb474d72843805ac0b02cf1043748
新门禁命令回执
4a28f8b536a2c828fe211dbb8b5a95f8289f53745c3377647f999dc9bccb5c6a
```

input.json 包含每个来源及新封存文件哈希、依赖、工具、原产品关系和完整门禁清单；环境文件通过同一明确适配函数从原 b3 冻结来源生成，其 sourceSha 与 sealedSha 分别记录。

失败后只读复核：新冻结源码／class、工作区来源和依赖一致；b3 准备目录 104 文件、证据 183 文件、已停止运行目录 1910 文件逐文件／清单不变。b3 原 FAILED_WITH_CLEANUP_ISSUES、b2 原因未知及更早记录均不改写。F3／r11 指纹仍一致，仅沿用原产品，没有重新安装、构建或验收。

本批修复副本仍保留 b3 身份作为测试输入，**不是用于再次启动 b3 的许可或新运行包**。原 b3 冻结启动入口／classes 保持原样。

## 6. 后续建议及停止点

本批未全通过，暂不提交可执行的新环境启动／邀请验收方案。

建议下一步只修正上述合成夹具的模拟前置顺序，不修改实际身份守卫、产品或业务断言；保留 a1 失败，批准后使用新的专用验证目录重新封存编译，完整重跑 16 项 Java、266 项 Node 和新清单中的语法检查，并完成最终指纹／合成证据扫描。不得只补跑五项后拼接本次成绩。

这些验证全部通过后，再提出一次完整新目标的隔离启动／邀请验收方案，等待启动授权。不创建新数据库／服务环境，不自动进入下一阶段。

**即使后续工具修正与无数据库验证全部通过，也不等于真实启动故障已消除，不能替代邀请人工验收，更不能据此宣布可上线。**
