# r10 平台后端隔离验收：迁移通过，完整阶段前置查询超时

日期：2026-09-18。**本批最终 FAILED，专用集群已正常关闭。独立 1/1、顺序 42/42 通过；完整阶段 63 项全部未执行。不能交付为完整平台后端验收通过，更不是发布候选版本或上线许可。**

失败后没有修补、重跑、复用旧目录或创建下一轮。只读取本批证据定位异常并编写本报告，未重新连接数据库。

## 1. 实际执行结果

| 项目 | 本次实际结果 |
| --- | --- |
| 新输入封存 | 通过，993 个源码／测试／资源文件，新增专用 POM |
| 全新源码编译／打包 | 通过，37.340 秒，未复用旧 classes |
| Java 无数据库检查 | 163/163，0 失败／错误／跳过；84.746 秒 |
| Node 无数据库检查 | 22 套件、613/613，0 失败／跳过／取消 |
| 真实 Maven 落盘探针 | 3/3；同一个无数据库方法在独立目录执行三次，不计入业务方法 |
| 新集群初始化、启动和 SQL 身份 | 通过；初始化／启动均只执行一次 |
| 主库空库迁移 | 通过，至 V4_90，8 个已应用历史条目 |
| 增量库迁移 | 通过，至 V4_88 为 6 条，再各增加 V4_89、V4_90，最终 8 条 |
| 校验和、重复迁移、哨兵 | 通过；重复迁移零变更，用户／审计／旧会话按确切标识保留 |
| 运行角色权限 | 无 SUPERUSER、无 BYPASSRLS，不拥有 public／控制 schema 中的表；不能写迁移历史和 ready 标记 |
| 独立阶段 | **1 执行、1 通过**，报告及夹具收尾通过 |
| 顺序阶段 | **42 执行、42 通过**，5 套件报告及夹具收尾通过 |
| 完整阶段 | **0 执行、63 未执行**，首个套件前置检查失败，未启动该阶段 Maven |
| 关闭与残留核验 | **正常关闭通过**，启动 1 次、停止 1 次；目标进程、父链及监听消失 |
| 最终源码／产物及历史核验 | 通过；冻结输入、编译产物、探针及历史证据未变 |

计划 106 是执行次数，不是不同方法数。实际通过 **43 次执行、覆盖 42 个不同方法**，业务断言失败／错误／跳过／无法核实均为 0；完整阶段 63 项未执行。批次 FAILED 来自前置查询／执行环境异常，不能因“业务失败数为 0”判整批通过，也不能把前两阶段拼成完整阶段。

本轮从未执行的 21 个不同业务方法是受控读取／导出 6 项、Spring 联动 14 项和角色竞态 1 项。它们的历史成绩不计入 r10。完整阶段中的其他 42 项也没有在该阶段执行。

## 2. 失败定位和仍未知的原因

原始汇总：

- `failedStage = full:TenantSecurityPostgresTest`
- `category = FIXTURE_PRECONDITION_ERROR`
- `code = FIXTURE_PRECONDITION_FAILED`
- 该阶段无 Maven 调用、无业务 XML，也没有 `fixtures-full-TenantSecurityPostgresTest-before.log`。

只读核对 `database-result.json.commands[267]`（零起始索引），发现异常是针对本批 r10 目录、55449 端口的 **Windows 全量身份查询**。不是业务测试失败、迁移失败或已经证明的产品缺陷。

实际时间（北京时间，UTC+8）：

| 事件 | 记录 |
| --- | --- |
| 查询开始 | 23:07:41.760，PowerShell PID 46128，父进程为本批 Node PID 45940 |
| 固定预算 | 15,000 ms；23:07:41.763 记录 TIMEOUT_ARMED |
| 超时回调实际触发 | 23:14:54.076，已过去 432,316 ms，记录回调延迟约 417,315 ms |
| 终止请求 | 同时记录 TERMINATION_REQUESTED，accepted=true |
| 终止确认及输出管道关闭 | 23:14:54.107，signal=SIGTERM，terminationConfirmed=true、outputComplete=true |
| 实际耗时 | 墙钟约 432.347 秒；单调计时同样约 432.347 秒 |

**预算没有被改大，但实际超时回调和终止明显晚于预算。** 工具最终按超预算失败处理、停止后续验收；不能据此说实际 15 秒墙钟上限已得到保障。

外层终端等待调用曾请求 30 秒，工具返回 wall_time_seconds=436.9221293。这与异常窗口同时出现，但不能据此证明因果关系。查询为何延迟、定时回调为何晚触发仍未确认；本轮没有把原因认定为休眠、系统挂起、权限拒绝或某个进程死锁，也没有改系统设置或读取其他范围的系统事件日志。

现有 `runR6Phases` 对 beforeSuite 的 catch 只保留通用前置失败码，未直接保存底层异常对象。根据冻结调用链，超时命令对应 `inspectWindowsEnvironment` 的执行失败分支；这是代码与命令记录的关联判断，**不能写成已经单独捕获了完整原始异常／查询字段缺失详情**。完整查询报告未成功返回，不能将其当作“没有进程”。本轮没有修改这个工具缺口。

原始结果保留，另写 [只读失败复核](releases/platform-db-acceptance-20260918/r10/failure-review.json)，不覆盖或重写原始 FAILED。

## 3. 迁移、权限及目标 MFA 实测

固定目标：

- 数据目录：`D:\ERP_WMS\BC warehouse\2G\target\platform-db-acceptance-20260918\r10\pgdata`
- 仅监听：`127.0.0.1:55449`
- 主库／增量库：`p3_m1_platform_release_r10`／`p3_m1_platform_upgrade_r10`
- 角色：`p3_r10_initializer`、`p3_r10_migrator`、`p3_r10_app`
- 控制 schema：`p3_r10_control`；导出临时根为 r10 下 `export-fixture-temp`
- 实际 Windows 身份：`ARNOLD\Arnold`，restrictedToken=false、administratorEnabled=true、elevated=true。按工具流程使用获批沙箱外上下文，未 RunAs 或修改 UAC／系统权限。
- 物理集群号：`7686889750547687448`；服务 PID 43368。完整父链为 `pg_ctl → cmd.exe → postgres`，目录、路径、所有者和创建时间均纳入核验。

使用全新空库、公共参考数据和登记合成夹具；没有复制旧验收账号或真实数据。SCRAM、随机凭据；公共角色／TRIAL 参考数据仍只核验，不补建掩盖异常。

四条迁移命令进程耗时分别为 2.616、2.484、1.185、1.545 秒，均未超 180 秒预算。源码含 94 个迁移文件不等于实际逐个执行 94 次；Flyway 本批采用既定 B4_71 空库基线及后续增量，最终历史为 4.71、4.72、4.85～4.90 共 8 条，均成功。未运行 repair、clean 或自动 baseline。

升级哨兵按确切合成标识检查用户 1 条、审计 1 条、旧会话 1 条；字段保留通过，实际字段 `browser_binding_hash` 升级后仍为空，未改写成错误字段名。校验和／迁移顺序、重复执行零变更、准备事务和保留断言均经过本次真实数据库执行。

运行角色 SQL 实际返回：

```text
p3_r10_app|f|f
f|f|f|f
0
```

依次对应非超级用户、无 RLS 绕过；Flyway INSERT／UPDATE／DELETE 和 ready UPDATE 均无权；public／控制 schema 对象拥有数为 0。

独立与顺序阶段的 MFA 结果相同：有效超级管理员 1、待接受邀请 0、目标挑战 1，调用前账号／挑战次数均为 4；实际异常类型为 `PlatformAdminInvitationMfaCredentialException`、错误码 `AUTH_FAILED`；之后账号／挑战次数均为 5、锁定有效、挑战已消费、邀请数 0。两阶段夹具均退役并保留审计。完整阶段未执行第三次对比；r5 未留存的实际异常码仍未知，不能用 r10 结果倒填。

## 4. 正常关闭及证据保留

超时命令终止确认后，于 23:14:54.109 开始独立收尾，预算 60 秒。重新核实目标身份后，仅对本批目录执行一次 `pg_ctl stop -m fast -w -t 30`，未停止其他进程。

- pg_ctl 停止命令 134 ms、退出 0，输出 server stopped。
- 独立收尾 23.107 秒，未超预算；23:15:17.216 确认完成。
- 最终 OS 核对确认目标 PID、父链、后代及 55449 监听消失；postmaster.pid 不存在。
- `normalShutdownProven=true`、`finalAbsence.confirmed=true`、`finalAncestryAbsent=true`，closeoutErrors 为空。
- 选定的其他监听前后一致：5432／PID 10400；未启动、停止或重启 5432、8080、5173 及其他服务。此字段只证明工具选定的监听前后一致，不扩大为全系统所有状态未变。
- 临时明文密码文件已删除，内存凭据引用已清除；导出专用临时根为空。
- r10 数据目录、快照、报告及脱敏证据保留；r2～r9、s1／s2 及此前修复证据未改。

关闭成功与验收 FAILED 分别保留，不用关闭成功覆盖前置失败。失败后没有再次连接本库、重新执行阶段或追加新环境。

## 5. 输入／产物对应关系与修改账本

本轮只修改 7 个测试层文件，新增 6 个 r10 专用绑定脚本。4 个 Java 文件只绑定 r10 目录、库、角色、schema，并新增旧 r9 URL 拒绝参数；3 个旧契约适配历史冻结源或当前 44 项隔离清单。新目标额外 18 项绑定、12 项环境契约；共享参数化清单解析器、Maven 执行器、生命周期／父链引擎未重写。

63 个业务正文与已批准修复快照相同；累计仍为 8 个不同方法、11 条历史修订。产品逻辑、公共 POM、约束及 94 个迁移文件均未改。前端及锁文件保护指纹通过，未提交、合并、推送或发布。分支 `2-1G`、HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。

源码 993、依赖缓存 3337、Maven 发行文件 90；1204 项编译 class／SQL。固定 Node 24.12.0、Java 17.0.17、Maven 3.9.9、Surefire 3.1.2、PostgreSQL 18.6。当前源码全新离线编译，所有阶段共用同一份产物；未借用旧 classes 或历史成绩。

实际执行入口（项目根工作目录）：

```text
D:/JAVA/NODE/node.exe src/test/scripts/platform-r10-backend-snapshot.mjs seal
D:/JAVA/NODE/node.exe target/platform-db-acceptance-20260918/r10/snapshot/source/src/test/scripts/platform-r10-database-batch.mjs build
D:/JAVA/NODE/node.exe target/platform-db-acceptance-20260918/r10/snapshot/source/src/test/scripts/platform-r10-database-batch.mjs database --authorized-20260918-r10-arnold-elevated-platform-backend-only
```

最后一条使用获批沙箱外上下文。进程最终退出 1，与 FAILED 一致。预算保持已批准值：编译／Java 各 300 秒，Node 每套 120 秒，迁移每条 180 秒，Maven 每次 240 秒（fork 120／退出 20 秒），初始化 60 秒，启动命令 35／包装 37 秒，就绪 40 秒，独立收尾 60 秒，停止命令 32 秒，SQL 30／statement 15 秒，完整身份查询 15 秒、单 PID 5 秒、就绪 SQL 4 秒。没有运行中加时；本次完整身份查询实际超预算已如实判失败。

| 标识 | SHA-256 |
| --- | --- |
| 输入 ID | `3f07f3f50d0b125d2098a9c4318380ddf398c5e8435425e9828f0c839e80b817` |
| 输入清单 | `2ce69b177fe6406cb43f5a1087139866e867c980a6a19668902cc70e9f257259` |
| 源码组 | `0db60d3ac6bd35537dfab0d3489f33b569b67cb3642bb43282b911f9b55b2fa5` |
| 新 JAR | `0e377b61d043c00914e2f57cbaf8e635f35ac53a2dc79e4fac1ba880ff30adaa` |
| class／SQL 聚合 | `829e45764a8f606754c172b9dc060f72eda51c9be9fcedcffa752d6aef2ada51` |
| 编译及前置结果 | `abdccacbc12a775f59d20ce3dc04dc3795983b15ea2b662a0238838dbf8e0a4c` |
| 原始数据库结果 | `bc50631ceff9da530d662233a8c0f3d2ae89758e0dadab8afddb444ea00580ff` |

证据目录：`docs/releases/platform-db-acceptance-20260918/r10`。

- [输入及独立预期清单](releases/platform-db-acceptance-20260918/r10/backend-input-manifest.json)
- [编译与前置结果](releases/platform-db-acceptance-20260918/r10/backend-build.json)
- [原始实库结果及关闭证据](releases/platform-db-acceptance-20260918/r10/database-result.json)
- [方法及修改索引](releases/platform-db-acceptance-20260918/r10/METHOD_INDEX.md)
- [独立阶段](releases/platform-db-acceptance-20260918/r10/phases/independent.json)、[顺序阶段](releases/platform-db-acceptance-20260918/r10/phases/sequential.json)、[完整阶段未执行记录](releases/platform-db-acceptance-20260918/r10/phases/full.json)

## 6. 后续需要确认的事项，本批不继续执行

建议先限定为本次查询超时／计时异常的诊断及前置检查首因保留，不修改产品代码、不调整业务断言、不直接扩大超时预算，也不立即重启 r10 或发起新一轮数据库验收。需要用户另行确认诊断范围；若需系统事件或独立监督机制等额外检查，应先说明具体权限与修改范围。

应分别解决两个问题：一是实际查询和定时回调延迟的原因仍未知，二是阶段汇总吞掉原始前置异常，导致只能关联命令记录。无数据库测试通过仍不能保证所有异常执行环境下的硬性墙钟时限。本报告不预先批准修复或重跑，不指定、创建下一轮目录。

生产 Flyway／PostgreSQL 兼容风险保留：本次真实迁移仍提示 PostgreSQL 18.6 高于当前 Flyway 已验证版本范围，不能因隔离迁移通过而宣称生产兼容验收完成。编译的 javax.annotation.meta.When.MAYBE 警告及 Surefire Windows classpath 提示同样保留，未为消除提示关闭安全检查或升级依赖。

前端 TS2769／正式类型检查／整包构建继续范围外交接，本轮没有复核解决；邀请完整人工激活继续待验；恢复核对仍是缺单报告＋人工核验修改、取消和退款，最终边界待确认。r8 导出底层未知异常和 r5 未知异常码不倒填。

本轮到此停止。没有 ERP 开发、真实账号／租户／数据库操作、前端或 HTTP 服务启动、现有服务变更、提交合并或生产开放。
