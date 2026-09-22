# s2 最小启停及 SQL 身份验证交付

日期：2026-09-16。结论：**PASSED；一次初始化、一次启动、一次只读 SQL 身份核验、一次正常关闭，完整闭环取得实际证据。**

这只是专用 PostgreSQL 启停环境验证，不是完整实库业务验收、发布候选版本或上线批准。

## 1. 授权目标与实际身份

- 新根目录：`D:\ERP_WMS\BC warehouse\2G\target\platform-start-stop-validation-20260916\s2`。
- 数据目录：上述目录下 `pgdata`；创建前确认为不存在，没有复用 s1 或其他旧环境。
- PostgreSQL：18.6；仅监听 `127.0.0.1:55449`。
- 唯一连接的数据库：默认维护库 `postgres`；初始化/连接角色：`p3_s2_initializer`。
- 仅新空集群；没有复制真实数据、创建业务库或执行迁移。
- 实际执行通道经工具流程申请沙箱外权限，身份为 `ARNOLD\Arnold`，SID `S-1-5-21-3770167052-703495069-1631994977-1001`；`restrictedToken=false`、`administratorEnabled=true`、`elevated=true`、`elevationType=1`。初始化输出所有者亦为 Arnold。
- 初始化目录创建前、初始化前、启动前及后续实际核验均经过身份查询；没有使用 RunAs、切换用户或修改系统安全策略。该上下文选择不表示 PostgreSQL 必须使用管理员权限。
- 新集群使用随机临时凭据与 SCRAM；HBA 仅允许本角色从 `127.0.0.1/32` 访问 `postgres`，其他 IPv4/IPv6 连接拒绝。没有使用 trust。

## 2. 本批源码与准备结果

分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；没有提交、合并或推送。

仅新增以下专用工具，既有 956 个输入文件未修改：

| 新文件（`src/test/scripts/`） | 内容 |
| --- | --- |
| `platform-s2-start-stop.mjs` | 在已修复入口基础上绑定 s2 目录、角色、授权标识和新门禁；采用批准的就绪 40 秒、独立收尾 60 秒。 |
| `platform-s2-start-stop.test.mjs` | s2 目标、预算、权限、父链接线、失败/收尾与新门禁验证，共 35 项。 |
| `platform-s2-preparation.mjs` | 新目录封存、固定工具指纹、实际无数据库回归和历史证据核验。 |

未改产品逻辑、公共安全逻辑、迁移、数据库约束、63 个业务方法、公共 POM 或锁文件；没有 ERP 开发。本批是工具源码快照，没有 Java 编译，不冒充完整后端或 R2 发布候选版本。

封存源码：`target/platform-s2-tool-validation-20260916/a1/source`，**959 个文件**。

输入 ID：

```text
f0b73f8c0e5965440f33537a54f833d3a06a54256573651c4463dcb6f167821b
```

前一工具修复输入 ID：`c95f8135c9ea9889d2377f9873751c87a2bcee230d0b98bbdd910ab178e1a08a`。

无数据库验证 UTC `09:25:56.286`～`09:27:53.971`：**481/481 通过，失败 0、跳过 0、取消 0**，所有套件执行器退出码为 0，并核对输出完整性和终止确认；3 个新增文件语法检查通过，PostgreSQL 版本查询为 18.6。此阶段没有启动或连接数据库。

| 套件 | 本次通过/执行 |
| --- | ---: |
| process-lifecycle | 35/35 |
| launch-preflight | 40/40 |
| s1-start-stop 工具回归 | 33/33 |
| process-ancestry | 33/33 |
| process-query | 16/16 |
| process-timing | 8/8 |
| fixture-isolation | 24/24 |
| upgrade-sentinels 契约 | 65/65 |
| test-results（XML） | 67/67 |
| r6-stages 编排 | 57/57 |
| report-runner | 60/60 |
| r7-binding | 8/8 |
| s2-start-stop | 35/35 |
| **合计** | **481/481** |

以上均使用本次冻结输入重新运行，没有累加历史 446 项或旧数据库成绩，也不是执行了 481 个数据库业务用例。

## 3. 实际单次执行结果

工具命令（已执行，仅作记录，**不可对现有 s2 重跑**）：

```powershell
& 'D:/JAVA/NODE/node.exe' 'D:/ERP_WMS/BC warehouse/2G/target/platform-s2-tool-validation-20260916/a1/source/src/test/scripts/platform-s2-start-stop.mjs' '--authorized-20260916-s2-arnold-elevated-empty-cluster-only'
```

实际执行 UTC **09:28:45.106～09:29:46.526**，北京时间 **17:28:45.106～17:29:46.526**，总计约 **61.420 秒**；外层执行器退出码 0。

| 项目 | 实际结果 |
| --- | --- |
| 创建前置检查 | 通过：新目录不存在、端口未占用、查询能力和执行身份符合绑定 |
| 初始化 | 1 次，退出 0；无受限令牌错误 87/3 |
| 启动 | 1 次，退出 0；`executorState=SUCCEEDED` |
| 物理与父链身份 | 通过；`databaseState=READY_VERIFIED` |
| 只读 SQL 身份连接 | 1 次，完整身份匹配 |
| 正常停止 | 1 次，退出 0；`cleanup.status=STOPPED` |
| 最终残留检查 | 通过；`normalShutdownProven=true`，`finalAncestryAbsent=true` |
| 首因/收尾错误 | `firstFailure=null`；最终收尾错误清单为空 |
| 迁移、业务库创建、业务测试 | 全部 0；未执行 1/42/63，未创建 r8 |

共 35 个实际命令（包含只读查询和控制命令），全部退出 0、输出完整、确认执行器退出；没有 timeout、abort 或 spawn error。进行了 9 次完整环境检查。

### 父链和身份

实际核验链为：

```text
pg_ctl 37132 → cmd.exe 46560 → postgres 45728
```

- 启动器句柄确认退出：UTC `09:29:02.059`。
- postgres 创建 ticks：`639251477418487390`。
- cmd 创建 ticks：`639251477418263230`。
- 所有者、可执行路径、精确数据目录、创建窗口、父 PID、监听归属、PID 文件及物理集群号均匹配；SQL 后及停止前再次核验。
- 物理与 SQL 集群号均为 **`7686062744813917768`**。

只读 SQL 实际返回：

| 字段 | 结果 |
| --- | --- |
| database / user | `postgres` / `p3_s2_initializer` |
| address / port | `127.0.0.1` / `55449` |
| listenAddresses | `127.0.0.1` |
| directory | `D:/ERP_WMS/BC warehouse/2G/target/platform-start-stop-validation-20260916/s2/pgdata` |
| clusterId | `7686062744813917768` |

### 预算与实际耗时

| 阶段/命令 | 预算 | 实际 |
| --- | ---: | ---: |
| 初始化命令 | 60 秒 | 2.620 秒 |
| 启动阶段 | 37 秒 | 0.437 秒 |
| 就绪阶段（SQL 前后身份核验） | 40 秒 | 15.637 秒 |
| 只读 SQL 命令 | 4 秒 | 0.185 秒 |
| 独立收尾阶段 | 60 秒 | 23.426 秒 |
| 正常停止命令 | 32 秒 | 0.129 秒 |

全环境查询仍为每次 15 秒，单 PID 查询每次 5 秒；本次所有命令最大耗时 6.027 秒。完整墙钟、单调时间、计时器设置、退出确认及阶段轨迹保存在原始结果中。实际启停没有触发超时；故障注入的超时/终止轨迹保存在本次无数据库验证输出中，不伪造成本次数据库的故障事件。

本次正常完成不解释 s1 的一小时异常，也不构成所有异常环境下硬性墙钟时限保证。

## 4. 正常关闭、凭据及历史保护

实际 `pg_ctl stop -D <s2/pgdata> -m fast -w -t 30` 于 UTC `09:29:34.581` 发出，退出码 0。服务器日志在北京时间 `17:29:34.637` 明确记录数据库关闭完成。

后续证据确认：

- postgres PID 45728 与已识别 cmd PID 46560 消失。
- `postmaster.pid` 消失；本批监听消失；目标 postgres 后代清单为空。
- `finalAbsence.confirmed=true`、`finalAncestryAbsent=true`，不是只凭停止命令退出 0 推断。
- 本批观察范围内其他监听与启动前一致：`0.0.0.0:5432` / PID 10568；没有启动、重启或停止其他服务。本项不扩张为所有服务健康检查。
- 临时明文 `init-password.ephemeral` 已删除，执行器中的随机凭据引用已清除；报告、快照和日志未归档原始密码、JWT、MFA 密钥或恢复码。
- **s2 目录和脱敏证据保留，不得复用或自动重跑。** 除本次临时明文凭据外未删除材料。

执行后通过冻结准备器再次只读核验：r2～r7 **47,808 个文件**、s1 **1,945 个文件**、已批准工具修复快照/输出均未改变。原 s1 仍为完整 FAILED，补充正常关闭仍单独 PASSED_CLOSEOUT_ONLY；r5 未留存异常码仍未知。

## 5. 交付证据与指纹

证据根：`docs/releases/platform-start-stop-validation-20260916/s2`。

| 文件 | SHA-256 |
| --- | --- |
| `input-manifest.json` | `9ac206ea017f58608d5606d2356166777018feb0ec4b7c2747d816832b9802af` |
| `no-database-results.json` | `b16a5ae58c2c2c1f44884ba33ec133a0bd4fe700b2cc47809b2c3a9ecd18df44` |
| `result.json` | `e5c1e017c1f7e2455054c19ad345cb5a080065c8ca12710de53728ef1be5c63d` |
| `post-run-review.json` | `015a1da8040e9b926040549cf139de9105dfdd06be34c65e70908d67860ceab8` |
| s2 根目录 `postgres-private.log` | `a442c2264f6133b19f7b11adbbf15a0288184d4c83cbb63af48ef4313476d607` |

新增入口源码 SHA-256：`4f3fa7719f5d8816fd88549bf94e2c9b9418f6288e7dfe33e72b4436d226a16f`。

清单列出全部 959 个源码文件及固定工具指纹；无数据库汇总列出本次 TAP/合成进程证据的命令、路径和哈希；实际结果保存每个命令与查询证据、输入 ID、父链、SQL、阶段预算和关闭记录。实际运行前后均核验工作区、冻结输入和工具一致。只读 `post-run-review.json` 为补充核对，没有改写 `result.json`。

## 6. 停止点与未覆盖项

**本批已到停止点：环境已正常关闭，实际证据完整。没有自动进入完整实库验收。**

仍未覆盖、也未因此宣布解决：

1. 新完整后端批次的空库/增量迁移及同一新产物下的 **1/42/63** 三阶段业务验收。需要另行确定目标、输入和授权；不能把本次最小启停通过当成事务、并发、权限和业务回归通过。
2. 前端地址测试 TS2769 交接、正式类型检查及整包构建阻塞；本批未执行前端检查或修复。
3. 邀请完整人工激活等待验项，以及恢复核对最终业务边界。现有“缺单报告＋人工核验修改、取消、退款”的边界仍待确认。
4. 正式配置、生产数据库兼容性、迁移/备份恢复演练、发布候选版本最终验收和上线审批。

后续只能先讨论独立的完整验收方案，再取得授权；本报告不授权新建 r8、迁移真实数据库、操作真实账号/租户、代码提交合并或上线。
