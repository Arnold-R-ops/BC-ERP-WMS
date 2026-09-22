# R7 平台后端隔离数据库验收：启动环节 FAILED

本批在 PostgreSQL 启动环节停止，未执行迁移或数据库业务测试。新源码编译、412 项无数据库回归和三次真实 Maven 探针通过，但不能作为实库验收或发布候选完成证明。

原始启动状态为 STARTUP_UNVERIFIED，原始收尾状态为 CLEANUP_FAILED。随后通过单独获准的沙箱外只读核对，未发现 r7 目录匹配进程、55449 监听或 PID 文件。**保留原始 FAILED，不改写为“正常关闭成功”。**

## 1. 实际执行与三阶段成绩

| 项目 | 实际结果 |
| --- | --- |
| 新源码编译 | 通过；580 个主源码、198 个测试源码；新输出目录 |
| Java 无数据库门禁 | 99 通过；0 失败／错误／跳过 |
| Node 无数据库门禁 | 313 通过；0 失败／跳过／取消 |
| 真实 Maven 探针 | 同一个无数据库方法分三阶段各执行一次，3 次均通过 |
| 脚本语法 | 14 项通过 |
| initdb | 完成，创建全新 r7 初始化目录；SCRAM |
| PostgreSQL 服务启动 | 启动器退出码 1；没有取得运行身份／SQL 就绪证明 |
| 主库空库迁移、增量迁移 | 均未执行 |
| 数据库业务三阶段 | 全部未执行 |
| 原始停止动作 | 未调用 pg_ctl stop；原收尾检查失败 |
| 后续只读残留核对 | 未发现 r7 目录匹配进程、专用监听或 PID 文件 |

实际数据库业务成绩如下；启动器失败不记作业务用例失败：

| 阶段 | 计划执行次数 | 通过 | 失败／错误／跳过 | 未执行 | 无法核实 | 结论 |
| --- | ---: | ---: | --- | ---: | ---: | --- |
| independent | 1 | 0 | 0／0／0 | 1 | 0 | NOT_EXECUTED |
| sequential | 42 | 0 | 0／0／0 | 42 | 0 | NOT_EXECUTED |
| full | 63 | 0 | 0／0／0 | 63 | 0 | NOT_EXECUTED |

106 是计划累计执行次数，63 是完整回归的不同方法数。本批数据库业务实际执行为 0；完整回归单独结果为 **63 未执行**。没有拼接 r6 的同次目标通过补充、r4／r5 或无数据库成绩。

412 项无数据库回归由以下实际执行组成：

| 门禁 | 通过 |
| --- | ---: |
| Java 隔离目标／连接保护 | 41 |
| Java 基线角色夹具 | 7 |
| Java 夹具归属及收尾 | 29 |
| Java MFA 前置条件／准确异常 | 22 |
| Node 源码夹具隔离 | 24 |
| Node 生命周期 | 32 |
| Node 升级哨兵 | 65 |
| Node XML 解析及入口 | 67 |
| Node 三阶段编排 | 57 |
| Node 报告绑定／异常首因 | 60 |
| Node r7 目标及报告隔离 | 8 |

相对上批 403 项，增加一项拒绝旧 r6 正确 URL 的 Java 参数用例，以及 8 项 r7 专用绑定检查；没有减少原覆盖。本批正式成绩来自冻结输入，不累计封存前开发预检。

封存前曾发生一次新脚本导出遗漏导致的模块导入错误；未启动 Maven 或数据库，补齐导出后 8 项开发预检通过。该预检只有工具输出，不冒充正式封存 TAP，也不计入上述 412 项之外的额外成绩。

## 2. 探针与业务报告分离

冻结的同一共享执行器 SHA-256 仍为 `393f91001eb42d50c3f173d674f6a35e009a4d7f90bbdfe7b35532fc5eead2dc`。探针和业务回调均使用它及同一专用 POM／参数构造，只按模式选择固定的报告根目录：

- 探针：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/build/a1/landing-reports/{independent,sequential,full}`
- 数据库业务：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/build/a1/reports/{independent,sequential,full}`

三次探针实际 XML、stdout、调用 UUID、夹具作用域、Maven 进程和有效配置均核验通过；后续阶段未覆盖先前文件，默认报告目录也未变化。业务报告根目录始终未产生，不可能将探针误记为业务结果。

每次探针由真实 Maven 3.9.9 先执行 help-plugin 3.4.1 effective-pom，再执行 Surefire 3.1.2:test。六次 Maven 调用的实际参数、cwd、有效 POM、进程结束证据及文件哈希保存在 `landing/*.json`；没有使用另一套冒烟专用正确调用。仍保留 Windows 绝对类路径 dumpstream 提示，未采用禁用 URLClassPath 检查的建议参数。

默认报告和所有探针文件在尝试数据库启动前后均再次核对，未变化。业务运行入口已接入逐套件产物／探针保护，但这次由于启动失败，**未实际执行数据库阶段中的报告／夹具 SQL 收尾**。

## 3. 固定目标与启动失败证据

| 项目 | 固定目标／实际状态 |
| --- | --- |
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/pgdata` |
| 唯一授权监听 | `127.0.0.1:55449` |
| 计划主库／增量库 | `p3_m1_platform_release_r7`／`p3_m1_platform_upgrade_r7`；未创建 |
| 初始化角色 | `p3_r7_initializer`；由 initdb 创建 |
| 计划迁移／运行角色 | `p3_r7_migrator`／`p3_r7_app`；未创建 |
| PostgreSQL | 固定 18.6，二进制与已有批准指纹匹配 |
| 物理集群号 | `7685179345729095408`；来自启动前离线 pg_controldata |
| 数据来源 | 新 initdb 空集群；未复制旧账号、真实库或租户数据 |
| 批次开始／原记录完成 | 2026-09-14 08:20:53／08:21:06（北京时间） |

启动前再次检查了目录不存在和端口空闲。初始化成功后，仅在本批新目录配置 SCRAM，限制回环地址和指定角色／库；其他 IPv4／IPv6 连接拒绝。没有使用 trust，也没有改变公共默认数据源。

实际启动命令为固定 PostgreSQL 的：

`pg_ctl.exe start -D "<r7/pgdata>" -l "<r7/postgres-private.log>" -w -t 30`

启动器在约 186 毫秒后退出，exitCode=1，未超时，输出完整：

```text
pg_ctl: could not create restricted token: error code 87
pg_ctl: could not start server: error code 3
```

这是首先发生的具体错误，来自 `pg-start.log` 及原始结果中的 launcher 输出。之后有时限的就绪观察没有发现本批 PID／进程，记录 STARTUP_UNVERIFIED、NOT_OBSERVED 和 DeadlineError；不能把后面的就绪期限当作最初原因。没有 PostgreSQL 服务日志生成，没有 live SQL 身份证明，主库／增量库创建和迁移步骤为 0。

这些是 Windows 启动执行环境／权限方面的线索，**尚未通过另一执行上下文的受控启动对照确认最终根因**。本批没有通过修改产品逻辑、扩大权限、关闭检查或重复启动来试错，也不能归因于 MFA、会话事务或产品约束失败。

### 原始收尾与补充核对必须区分

原始 `stop` 为 `attempted=false, completed=false, status=CLEANUP_FAILED, error=Error`。工具未发现可绑定的 r7 PID；用于确认不存在残留的进程查询失败，因此不能宣称正常关闭已验证。原记录只保留泛化 Error，不倒填为它已记录具体访问拒绝码。

批次结束后，在同一受限环境复现只读 `Get-CimInstance Win32_Process`，得到“拒绝访问”。随后通过工具单独申请并获准 **仅进行沙箱外只读进程／端口核对**，没有重新启动、连接或停止数据库。2026-09-14 08:23:20（北京时间）结果为：

- PostgreSQL 进程信息可读取，10 个进程的命令行均可读取；与 r7 数据目录匹配的进程为 0。
- r7 的 `postmaster.pid` 不存在，55449 无监听。
- 既有监听仍为 `0.0.0.0:5432` PID 10452；检查范围内没有新增 8080、5173、55439、15173、18091 服务。
- 没有对既有 PostgreSQL 进程执行停止或修改。

因此实际收尾结论是：**启动失败后，补充只读核对未发现 r7 运行残留；不是一次成功的 pg_ctl stop。** 原 FAILED／CLEANUP_FAILED 原样保留。r7 初始化目录保留，不复用、覆盖或删除；临时初始化口令文件已清除，未归档明文凭据。

## 4. 迁移及业务未覆盖边界

封存包含 94 份迁移资源，计划使用既定空库基线与增量链：
`B4_71 → V4_72 → V4_85 → V4_86 → V4_87 → V4_88 → V4_89 → V4_90`。

计划主库形成 8 条版本历史；升级库按 6＋1＋1 演练，并核对重复执行零变更、哨兵、约束、校验和及角色权限。本批这些真实步骤 **全部未执行**。无数据库哨兵契约 65 项通过，不等于迁移或哨兵保留在实库中已经通过。

同样未覆盖：角色权限实库、事务与并发、平台会话／邀请／审计、紧急暂停／双人恢复、导出与连接联动、目标 MFA 事务独立及前序套件后一致性、逐阶段夹具收尾。现行 63 个方法及权限／业务断言保持不变。

## 5. 源码、依赖与新产物

| 项目 | SHA-256／实际值 |
| --- | --- |
| 分支／HEAD | `2-1G`／`9c12f6f893105632c4de24ad8bb4f6f43092f84e` |
| r7 输入 ID | `0eb8b0f444609c0a1ff20e9da6bba42159f21925a8868555cd14fc37bcbfd7e9` |
| 输入清单 | `f98f024828b6b132a1b031e20d51e01ee897c2ee6ca3491cc816991165d0d892` |
| 构建清单 | `2f559f7682149f5244a1955d11d55bae7604c30ccbe91259ec9d4d6eb80a765c` |
| 原始 FAILED 结果 | `4c5fc190dcc340db835d2e34293c8a07d5b120f7cef0f3a257046dca6a76df98` |
| 新 JAR | `d1e94909ed58431106a59f24a70e24235dcd2d22b41123fbe5b185ed13c90546` |
| 1,190 项 class／SQL 集合 | `245e4cd4d57e91832ac9bccb0dea54713055e819af6f4d8c3f8ac710afb7e594` |
| 专用测试 POM | `7bee6ca8caa125c02307327b1bbe8f9a522b0c3dbaa5563ce4e2ad96a5d378d8` |

源码：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/snapshot/source`；新输出：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r7/build/a1`。封存 942 个后端文件、3,337 个 Maven 缓存文件、90 个 Maven 发行文件和 136 个运行类路径 JAR 指纹。工具实际版本核对为 Node 24.12.0、Java 17.0.17、Maven 3.9.9、PostgreSQL 18.6；Surefire 固定 3.1.2。未升级依赖、变更锁文件或全局 PATH。

仅四个已有测试辅助文件改为 r7 目标：

- `PlatformR2Isolation.java`
- `PlatformR2IsolationTest.java`（另加拒绝旧 r6 的输入）
- `PlatformR2SpringInitializer.java`
- `PlatformR2DatabasePreparer.java`

新增三个专用工具：`platform-r7-backend-snapshot.mjs`、`platform-r7-database-batch.mjs`、`platform-r7-binding.test.mjs`。旧 r6 工具／报告、通用报告解析器及生命周期实现没有修改。公共 POM 只被读取，专用 POM 位于新冻结源码根目录，派生配置保留原检查范围。

相对报告修复 a2，11 个测试 class 指纹不同，包括引用 r7 编译期常量的测试；清单保存于构建结果。**产品 class／SQL 变化为 0**。业务测试源码和 63 个现行方法正文没有再修改；已批准的 7 处历史正文变化仍保留，不能声称相对最初 r5 的全部正文均未变。

结束后源码、工作区对应输入、产物、默认和探针报告均无漂移；61 个历史保护组、95,074 个历史文件实例仍按哈希校验。r6 原始 FAILED 结果 SHA-256 仍为 `886e1d588055f5335b6736ee5c16427bb1c981f5d612794aad900d4b77ec6f7e`。r6 同次目标 1 项通过补充与未完成收尾继续分开，r5 未留存的实际异常码仍未知。

## 6. 交付记录与下一步建议

本批记录目录：`D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7`。

- [backend-input-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7/backend-input-manifest.json>)：全部输入、依赖、迁移、目标和历史指纹。
- [backend-build.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7/backend-build.json>)：实际编译命令、412 项无数据库结果及三次探针证据。
- [database-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7/database-result.json>)：原始 FAILED、启动器首个错误、未执行成绩及原始收尾失败，不改写。
- `landing/*.json` 与 `phases/*.json`：探针成功和数据库三阶段未执行分别保存。
- [closeout-readonly-review.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7/closeout-readonly-review.json>)：后续只读残留核对，与原始记录分离。
- [delivery-ledger.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/r7/delivery-ledger.json>)：上述记录、本报告、日志及真实报告文件的路径与哈希索引；不复制原始认证材料。

建议下一批先单独确认**专用启动和收尾所用的 Windows 执行上下文，以及进程身份查询前置门禁**。应在初始化新目录前验证身份查询权限，避免启动失败后才发现无法核实残留；同时准确记录启动器错误、身份检查错误及无残留观察。若需要在沙箱外启动，必须明确批准精确的专用命令及新目标，不能把本次只读授权当作数据库启动授权。

权限／执行环境问题解决并获准后，再讨论全新批次的封存编译、412 项及真实落盘门禁、迁移和同产物 1／42／63 重跑。**本轮未修改工具以绕过本次失败、未复用 r7、未创建 r8、未自动开启下一轮。** 不建议通过改 trust、降低断言、扩大产品权限、仅延长超时或改动现有服务处理本问题。

前端 TS2769 与整包构建阻塞继续交接，地址测试和 pnpm 锁文件哈希未变；本批未执行前端正式检查。邀请完整人工激活流程继续待验，已完成 MFA／恢复码等人工项保留。恢复核对仍是“缺单报告＋人工核验修改、取消和退款”，最终业务边界未确认。本任务不接手租户 ERP 开发。

**最终结论：r7 FAILED；启动失败后经补充只读核对无匹配运行残留；迁移和全部数据库业务测试未执行。仍非发布候选版本，不可上线。**

