# b3 一次性隔离启动取证／邀请验收记录

日期：2026-09-20。专用启动于北京时间 22:10:32 开始，22:12:47 结束；随后只读补核于 22:15:01 左右完成。

**原始结果：FAILED_WITH_CLEANUP_ISSUES，保持不变。** 无数据库门禁、数据库创建及迁移通过；后端在包装器参数守卫失败，未启动代理、未进入人工邀请验收。数据库正常停止命令及第一次停止核验成功，但最终父链补核遇到 PID 复用，不能把整批收尾写成全部通过。后续只读检查确认当前无本批进程／监听残留，不改写原失败。

## 1. 本批范围、改动与固定输入

仅对六个专用测试文件作 b3 目标绑定：

- `src/test/scripts/platform-b1-binding.mjs`
- `src/test/scripts/platform-b1-prepare.mjs`
- `src/test/scripts/platform-b1-batch.mjs`
- `src/test/scripts/platform-b1.test.mjs`
- `src/test/java/com/wms/browseracceptance/PlatformB1Harness.java`
- `src/test/java/com/wms/browseracceptance/PlatformB1Contract.java`

更改目录、数据库、角色、控制 schema、授权标记、测试属性／环境变量名称、合成账号及对应文字断言；准备器额外登记 b2 和诊断修复包为受保护历史。逐行账本及新哈希见证据目录 `target-binding-ledger.json`。沿用 B1 类名／工具文件名和协议常量，不代表复用 b1 环境。

诊断 Java／Node 实现及其 38 项测试与已通过的诊断修复包指纹相同。未修改产品逻辑、迁移、业务断言、公共配置、依赖版本或租户 ERP。当前分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；保留工作区其他任务改动，没有提交、合并或推送。

| 项目 | 本批固定值 |
| --- | --- |
| 准备根 | D:/ERP_WMS/BC warehouse/2G/target/platform-b3-preparation-20260920/a1 |
| 冻结入口／新包装器 | 上述根/snapshot、上述根/classes |
| 运行根 | D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b3 |
| 数据／私有目录 | 上述根/pgdata、上述根/private |
| 证据根 | docs/releases/platform-browser-invitation-20260920/b3 |
| 库／控制 schema | p3_platform_browser_b3／p3_b3_control；维护库 postgres |
| 三角色 | p3_b3_initializer、p3_b3_migrator、p3_b3_app |
| 监听 | 127.0.0.1:55459／18092／15174 |
| 实测执行身份 | ARNOLD\Arnold，管理员组启用、elevated=true、restrictedToken=false |
| 工具 | Node 24.12.0、Java 17.0.17、PostgreSQL 18.6、Windows PowerShell 5.1.26100.9444 |

SID 为 `S-1-5-21-3770167052-703495069-1631994977-1001`。没有 RunAs、UAC／系统策略修改、临时唤醒请求或原有服务操作。

实际命令各执行一次（**历史记录，不是重跑授权**）：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-prepare.mjs'
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b3-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b3-invitation-only-20260920
```

完整编译、迁移、后端和代理命令在启动前已封存于 `input.json.commands`；具体执行回执在逐命令文件和 `environment-final.json.commands`。后端／迁移只使用新 b3 包装器 + 原 r11 classes/resources/136 项冻结依赖。没有复制旧包装器 classes 作为本轮编译证明。

预算启动前固定：编译 300 秒；Node 每套 120 秒；Java 契约 60 秒；语法每项 15 秒；合成 Java 10 秒；诊断排空 1 秒。初始化／迁移各 180 秒；ACL／SQL 30 秒、statement 15 秒；启动器 37 秒（命令 35）；PG 就绪 40 秒、独立收尾 60 秒、停止命令 32 秒；全量身份查询 15 秒、PID 查询 5 秒、就绪 SQL 4 秒；后端就绪／停止 90／30 秒、代理就绪／停止 10／10 秒。未在运行中增加预算。

## 2. 本批实际成绩

| 检查 | 实际结果 |
| --- | --- |
| 冻结输入／新包装器 | 45 文件／17 class |
| 语法检查 | 35/35 通过 |
| Java 无数据库契约 | 16/16 通过 |
| 原有 Node 门禁 | 193/193 通过（155 + 完整性 13 + 启动安全 25） |
| 诊断 Node 门禁 | 38/38 通过，含 16 次真实 JDK-only 合成失败进程 |
| Node 合计 | 231/231；失败、取消、跳过、待办均 0 |
| 现场前置核验 | 三端口空闲、目标目录不存在、执行身份匹配 |
| ACL | 实际收紧并读回通过；仅当前 SID 和 SYSTEM，完整权限、无继承；之后才生成随机凭据 |
| 初始化／PG 启动 | 一次初始化、一次启动；SCRAM；目录、监听、所有者、创建时间、父链和 SQL 身份通过 |
| 业务库／迁移 | 创建新库，校验 94 个迁移资源；空库实际应用 8 条，至 V4_90；再次执行 0 变更 |
| 应用角色 | 无 SUPERUSER、无 BYPASSRLS、不拥有 public/control 表；无迁移历史写入及 ready 表更新权限 |
| 后端 | 仅启动一次，退出 1，未就绪 |
| 代理／人工主线 | 未启动／全部未执行 |

实际迁移顺序：B4_71 → V4_72 → V4_85 → V4_86 → V4_87 → V4_88 → V4_89 → V4_90。不是把 94 个资源校验数写成 94 次迁移执行。校验和和 success 均记录于 `044-migration-history.log`，与冻结迁移核验一致；未运行 repair/clean，未修改迁移。

集群号 `7687619747811186604`；SQL 核验连接维护库 postgres、用户 p3_b3_initializer、地址 127.0.0.1、端口 55459 和确切 b3 pgdata。迁移执行 2286 毫秒／180 秒预算。

迁移日志保留 Flyway 9.22.3 对 PostgreSQL 18.6 的兼容警告：其声明测试支持上限为 PostgreSQL 15。本次迁移实际通过不消除该生产兼容性待核事项；未升级依赖。

上述无数据库成绩全部来自本批新冻结内容，未拼接历史结果；这不是 r11 完整业务套件重跑。本批未创建 B、没有通过 API 或改库替代邀请激活。

## 3. 真实后端失败：已确认事实与未知项

监督器首因为 `BACKEND_START / B1_BACKEND_EARLY_EXIT`。新的真实诊断流有 8 个完整、有效事件，无协议损坏、分片截断或非协议输出：

```text
ARGUMENTS → CREDENTIALS_READ → ISOLATION_CONFIG → DATABASE_IDENTITY
→ SPRING_CONFIGURATION → SPRING_START → ARGUMENTS → FAILURE
```

失败字段：`stage=ARGUMENTS`、`reason=ARGUMENTS_REJECTED`、异常 `java.lang.IllegalStateException`。

保留的安全栈：

```text
com.wms.browseracceptance.PlatformB1Harness.main:43
jdk.internal.reflect.NativeMethodAccessorImpl.invoke0:-2
jdk.internal.reflect.NativeMethodAccessorImpl.invoke:77
jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke:43
framesTruncated=true
```

第 43 行现有守卫拒绝“参数非空或工作目录不等于 ROOT”。初次调用已通过该守卫，并通过专用数据库身份检查；进入 Spring 启动后有反射重入，再次进入守卫并失败。**这定位了本次失败位置，但没有记录第二次 args.length／cwd 分支，不能据此断言是哪一个条件，更不能把原 b2 未知异常补写为本次原因。**

未到达 SYNTHETIC_ACCOUNT_PREPARATION／READY，A 的准备和后端产品上下文完整启动均未验证。不将“进入 SPRING_START”写成 Spring 启动成功，也不把包装器拒绝归为已确认的产品缺陷。

### 静态证据与推断（无新启动）

只读检查冻结的运行参数与依赖，并用固定 JDK 的 javap 查看现有 `spring-boot-devtools-3.2.11.jar` 字节码，没有运行 Spring：

- 实际 classpath 包含该 DevTools；其冻结 SHA-256 为 `75c73430682f72409787e59b05cd76b6b633c37aa1b9e79a363dddbfe1521721`。
- 当前测试配置只在 Spring 配置文件／属性源设置 `spring.devtools.restart.enabled=false`，本次实际 JVM 命令没有对应 `-D` 系统属性。
- `RestartApplicationListener.onApplicationStartingEvent` 读取 `System.getProperty("spring.devtools.restart.enabled")`，重启路径把 ApplicationStartingEvent 的参数交给 Restarter；RestartLauncher 通过 Method.invoke 调用 main。
- 本包装器在 `app.run` 中传入两个固定的配置／profile 参数。

这些静态事实与本次反射重入现象吻合，DevTools 早期重启是明确的下一步核对方向；由于本次安全栈仅保留前四帧，未保留实际调用者和线程身份，**不将其宣称为唯一已证实根因或已修复问题**。没有改变 JVM 参数、产品或依赖并重试。

## 4. 收尾事实：停止成功与最终核验异常分开

- 后端 PID 53968 退出 1，实际核验其消失；代理 NOT_STARTED。
- 经完整目标身份核验后，唯一一次 `pg_ctl stop -D <b3/pgdata> -m fast -w -t 30` 返回 0，458 毫秒，输出 server stopped。没有强杀。
- 独立停止流程记录 completed=true、status=STOPPED，25847 毫秒／60 秒预算；第一次停止后检查中，PG／relay／launcher 均不存在。
- 最终第二次父链核验中，原 PG PID 5636 已被新建的 PowerShell 查询进程复用：旧创建 ticks `639255102606258730`，新 ticks `639255103659896820`，新可执行文件为固定 Windows PowerShell，父 PID 22316。原始证据 `069-process-proof-31.log`；收尾错误 `PG_STOP / IdentityError / ANCESTRY_ABSENCE_PID_REUSED`。
- 该收尾错误独立保留，未覆盖后端首因。原 `environment-final.json` 是 FAILED_WITH_CLEANUP_ISSUES，没有改写为正常完成。
- init-password.ephemeral 在初始化后清除；后端退出确认后清除确切归属的 runtime.properties 与 login-credentials.txt。专用数据／证据目录保留，临时明文认证材料不归档。

### 后续只读补核（不是重跑／补写历史成功）

使用同一冻结只读查询函数，经权限审批检查当前时点，结果写入新 `closeout-readonly.json`：

- 22:14:44—22:14:54，55459／18092／15174 均无监听；确切 b3 目录的 PostgreSQL 进程数 0，postmaster.pid 不存在，查询完整。
- PG PID 5636、relay PID 48428、后端 PID 53968、监督器 PID 22316 当前均不存在；监督器查询使用原创建时间、路径、所有者及命令行哈希身份作为对照。
- b3 private 目录为空；未连接数据库、未再停止／杀死任何进程、未重启。
- 原有 5432 的 PID 9768 和 5173 的 PID 14716 在前后记录一致；原有服务未被本批操作。

**当前无本批残留，不等于原最终收尾核验通过。** 只读补核的 historicalClosureReclassified=false。本批没有使用 MANUAL_COMPLETED；人工验收仍为 false／未执行。

## 5. 指纹、历史与未覆盖项

```text
b3 输入聚合（45 文件）
8360497c422294d418b7667254b80634724124cf819f4583e8896732428eef14
b3 新 class 聚合（17 文件）
736bc4746ea03983e64497e22ef2ed64d3c78c8e89b0039c9d37ffb9e8371f85
input.json
dbf232af0ff74f19bbfe4327939c3d21663d0e4fba36979ce208396312ab2422
compiled.json
5c31dd94329a37b01f4082ed54ca9f9e9ccea73728db7988b6af44deb75bb6f3
preparation-result.json
42c1846e0809d5d041e937cb2f45291f3e3834ae1a2b914cb1956220e41ba508
environment-final.json（原失败）
4ff025df4a5f9ff729d5d6faacbcba3c81ecd9c134397f069432a5c4a111cbfc
backend-diagnostic.jsonl（本次真实诊断）
b40a6836eba7ccda217457bd277b163586f4bb6e34304831c907e8514f7dff58
closeout-readonly.json（后来只读补核）
ef4fadb2622df593f49bca429f745dd9cb0401b9e69d927ca3c5eb1fe95c07fe
```

产品复核：F3 源码 236 文件、dist 262 文件；r11 产品源码 679 文件、产物 951 文件，差异均为空。F3 源码 ID `ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d`，dist 聚合 `7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c`；r11 输入 ID `f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62`，class 聚合 `36a46c4ac127806483861b4032133c7074c9a10dcb4791c4bf1177d82658dfc7`，jar `7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6`。没有依赖安装、产品重建或后端全套重验。

准备前后及收尾补核逐文件／清单保护 b1、b2 和前批诊断证据：b2 运行目录 1910 文件、准备 54 文件、证据 175 文件均不变；旧 FAILED、原快照和未知原因不动。新准备文件数比诊断修复批少一个，是不再封存只用于前批验证的 platform-backend-diagnostic-verification.mjs；实际诊断实现、38 项测试和监督入口均封存并验证，没有删减用例。

未覆盖：后端完整就绪、代理页面／边界实测、A 首次 MFA、创建／人工转交邀请、B 设置密码／首次 MFA／恢复码确认／激活、刷新、退出后旧链接拒绝、正常再次登录。没有邮件送达验收，不新增自动业务成绩。r11 后端通过记录与 F3 构建收尾记录继续独立有效；完整发布候选版本、最终恢复核对业务边界及生产上线条件仍未完成／未批准。

## 6. 建议下一批范围——待单独批准，未执行

先限定为测试启动链及收尾身份判定的无数据库修正／验证，不直接开启下一轮环境：

1. 在专用后端 JVM 启动命令层明确禁用 DevTools 重启，核对实际入口和封存命令的一致性；不移除产品依赖、不修改公共配置，也不放宽零参数／目录守卫。补充无需数据库的启动参数契约及必要合成进程验证。
2. 核对最终收尾对“旧 PID 已结束、当前同 PID 属于不同创建时间的其他进程”的处理。保留所有者、路径、创建时间、目录、监听及查询完整性检查，不能将任意 PID 不匹配直接视为成功，不能终止新进程。未知和查询失败继续停止；以无数据库合成／契约验证为先。
3. 更新专用工具后重新封存、编译并完整执行受影响门禁；b3 原证据不改写，不倒填 b2 原因。通过后另行提出新的单次隔离启动／邀请验收目标，等待批准。

本批已停止。没有创建下一轮环境，没有现场修补后重启，不提交合并、不开放生产。
