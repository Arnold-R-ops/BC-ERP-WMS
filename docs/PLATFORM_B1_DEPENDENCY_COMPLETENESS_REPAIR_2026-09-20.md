# b1 封存依赖完整性修复与无数据库门禁交付

日期：2026-09-20。门禁完成：北京时间 01:40:57。

**结论：本批准备门禁通过，已到授权停止点。** 在同一份新封存输入上，重新编译测试包装器，完整执行原有 **16 项 Java 契约、155 项 Node 检查**，另执行 **13 项新增完整性契约**及 **30 项语法检查**。没有拼接原 b1 或 r11 历史成绩。

没有初始化、连接数据库，没有启动后端、前端或代理，没有进入浏览器操作，也没有临时唤醒、依赖安装、F3 重建、r11 重跑、提交合并或生产操作。门禁通过不等于 b1 浏览器验收通过，也不等于可以上线。

## 1. 修复范围与完整性检查

仅修改专用测试层工具，产品、迁移、约束、业务断言、依赖声明、锁文件及公共配置未改。

| 文件 | 本批变化 |
| --- | --- |
| src/test/scripts/platform-b1-catalog.mjs | 新增固定 20 文件清单及明确依赖关系；核验批准来源和 SHA-256，缺失/重复/漂移即失败，无回退 |
| src/test/scripts/platform-b1-catalog.test.mjs | 新增 13 项完整性契约，独立列账，不替代原 155 项 |
| src/test/scripts/platform-b1-prepare.mjs | 实际封存入口先预检，再创建新目录；保留相对路径、逐文件来源与哈希；从新快照执行门禁并独立编译 |
| src/test/scripts/platform-b1-binding.mjs | 准备/快照/classes/证据改绑新 a1，后续 b1 运行目标不变 |
| src/test/scripts/platform-b1-batch.mjs | 后续启动入口必须核验新增完整性门禁及包装器编译 ID；本批未执行启动 |
| src/test/java/com/wms/browseracceptance/PlatformB1Harness.java | 测试包装器的隔离配置位置指向新 a1 快照，避免后续读取旧 b1 快照；业务/安全行为未改 |

共享文件固定取自 r11 测试层快照：
target/platform-db-acceptance-20260919/r11/snapshot/source/src/test/scripts。

r11 清单自身 SHA-256 固定为：
6faefc5ab260931cbc1d378fbef826f82a4c3b1ef391dd62220b7c427aa9c228。

补齐的三个已知缺口：

- fixtures/PlatformPipeInheritance.cs，保留 fixtures 相对目录；
- platform-r7-database-batch.mjs；
- platform-s1-start-stop.mjs。

一并显式封存 R7 入口需要的 backend-snapshot、upgrade-sentinels、r6-stages、test-results、report-runner、junit-xml.ps1、report-pom.ps1 等传递依赖。完整 20 文件及关系在 input.json 的 completeness.sharedFiles/edges；全部 38 个封存文件均记录来源或明确生成方式及封存哈希。未建设通用依赖扫描/解析框架。

完整性预检在新准备目录、新证据目录创建之前完成；缺文件直接失败，不能从工作区或其他历史目录兜底。历史入口只在测试中导入/读取，不调用其初始化/启动分支。新增契约实际验证导入 R7/S1/S2 时不启动子进程、不执行所拦截的文件写入，同时检查历史 CLI 入口保护。

原六个 Node 测试文件及 PlatformB1Contract.java 与旧 b1 已封存哈希逐项相同，见 input.json 的 unchangedContracts。未删除、跳过用例或修改原断言。新增共享副本和配置绑定不代表重新实施历史数据库批次。

## 2. 实际执行与结果

本次唯一准备入口，已执行一次，不可在原目录重复执行：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-prepare.mjs'
~~~

cwd 为 D:\ERP_WMS\BC warehouse\2G。固定 Node 24.12.0、Java/javac 17.0.17；使用 r11 已冻结且逐项匹配的 136 个 classpath 依赖，没有 Maven/pnpm 安装或产品编译。

| 本次实际步骤 | 结果 | 命令耗时 |
| --- | --- | --- |
| 新清单语法检查 | 26 个 mjs 的 node --check、4 个 ps1 的只解析检查，30/30 退出 0 | 每项均在 15 秒预算内 |
| 新增完整性契约 | 13/13，失败/取消/跳过/待办均 0 | 0.153 秒 |
| 包装器独立 javac | 退出 0，生成新 13 个 class | 1.873 秒 |
| 原 Java 无数据库契约 | 16/16 | 0.185 秒 |
| process-lifecycle | 35/35 | 3.262 秒 |
| launch-preflight | 40/40 | 0.186 秒 |
| process-ancestry | 33/33 | 0.162 秒 |
| process-query | 16/16 | 1.534 秒 |
| b1-environment | 12/12 | 0.154 秒 |
| b1 专项 | 19/19 | 0.235 秒 |

原 Node 合计 **155/155**，六套失败/取消/跳过/待办均 0。13 项新增检查单列，不把总数变成“原有 168 项”。原先失败的生命周期第 31 项，本轮从新快照取得 C# 文件，实际编译并执行短生命周期合成管道子进程，通过；不是仅校验文件存在。

新增 13 项依次覆盖：完整相对路径/来源、缺 C#、缺 R7、缺 S1、传递依赖缺失、批准来源缺失、哈希漂移、重复清单/来源、越界/额外路径、禁止回退、内容/关系缺口、历史导入无执行副作用、历史 CLI 保护及实际封存入口预检顺序。详见新 TAP 原文，不从本次结果反推预期数量。

预算未调整：语法每项 15 秒；Node 每套 120 秒；javac 300 秒；Java 契约 60 秒。39 个正式命令回执均退出 0、输出完整、进程结束确认且未超预算；外层准备命令退出 0。PowerShell 仅解析源码，不执行所封存的身份/端口查询脚本。

这些是无数据库工具回归。模拟身份、端口、父链和收尾断言通过，不等于 b1 的真实 Windows 身份、数据库或服务启动已经验证。

## 3. 输入、产物与历史证据对应关系

| 项目 | 本批位置或核验 |
| --- | --- |
| 新准备根 | target/platform-b1-preparation-20260920/a1 |
| 新快照 | 上述根/snapshot，共 38 文件 |
| 新包装器产物 | 上述根/classes，共 13 class，不复用旧包装器 classes |
| 新证据根 | docs/releases/platform-b1-preparation-20260920/a1 |
| 旧 b1 快照/产物 | target/platform-browser-invitation-20260919/b1，39 文件前后清单及哈希一致 |
| 旧 b1 证据 | docs/releases/platform-browser-invitation-20260919/b1，43 文件前后清单及哈希一致；FAILED 原样保留 |
| F3 输入/dist | 236 前端输入、262 产物匹配；没有重建 |
| r11 产品 | 679 产品文件、951 产品编译文件及 JAR 匹配；沿用其验收，不重跑 |
| 依赖及迁移 | 136 项冻结 classpath 依赖、94 个迁移 SQL 的对应关系保持；本批不迁移 |

关键 SHA-256：

~~~text
新封存输入 ID（38 文件）
e194ee481ff27f1f5c26e8f26240ea8f1eac1f2534cbdf2a04100453224080a9
新包装器编译聚合（13 class）
9cba2424aab4c881dca6e27895c2e610905f11edf1e9b28f786401b24af904ad
input.json
463653e59218560345401fb6797262bf4160c05fa680f91b295fd41506fe55c5
compiled.json
5b6e81c9271d7c7036b396186dc52957313a3eb0a4c4fbdef7de8dd165dcab38
preparation-result.json
2318762660157d84ddd384e94ec3cc1f8e53bb92711a5ebd588dffacb6c03fa7

F3 前端源码
ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d
F3 dist
7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c
r11 输入
f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62
r11 产品编译聚合
36a46c4ac127806483861b4032133c7074c9a10dcb4791c4bf1177d82658dfc7
r11 JAR
7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6
~~~

六个本批工具的完整指纹见 delivery-review.json 的 modifiedToolFingerprints，包含原始来源与新快照 SHA。全部外部工具、依赖、生成参数、输入/产物逐文件哈希在 input.json、compiled.json。各命令的绝对 argv、cwd、预算、实际 PID/退出/计时及日志哈希在对应 json 回执，原 TAP/编译输出在 log。

## 4. 保留情况、观察及边界

1. 旧 b1 原 FAILED、快照、部分产物及失败复核没有覆盖或改写。本批保存的是新 a1 成绩。
2. b1 的 pgdata、private、旧/新 start-attempt 和 environment-ready 均未创建。没有数据库/服务启动，因此“正常关闭”不适用，不能记为关闭验证通过。
3. 未生成运行时密码、MFA 密钥、JWT、邀请链接或恢复码；无需清理这些运行认证材料。无数据库契约中的固定合成占位文本不是真实凭据。
4. 没有查询真实 OS 身份、监听端口或现有服务；不能由文件不存在推断整台机器不存在其他进程/监听。后续启动仍须实际前置核验。
5. 产品清单枚举有既有 Git 全局 ignore 读取权限警告，命令退出 0，所需清单及产品指纹核验通过；未改权限。
6. 准备编辑期间，一条只读历史文档读取工具回执报告约 439.376 秒后返回，最终退出 0。原因未确认，不计入正式门禁成绩；本轮 39 个正式门禁命令均在原预算内。这不能证明宿主延迟问题已经解决，也没有申请/取得临时唤醒。
7. 未操作其他任务成果、共享存储、前端依赖或历史数据库目录。未提交、合并、推送或开放生产。

## 5. 本轮停止点与未覆盖项

完整无数据库门禁、工具/包装器指纹及交付记录已具备，**本轮到此停止**。

仍未覆盖：b1 实际 Windows 执行上下文及端口检查、空集群创建/迁移/角色核验、后端/代理就绪与关闭、真实邀请浏览器主线。正式域名/HTTPS/密钥/真实邮件、生产数据库权限、备份恢复、发布整合及上线审批也不属于本轮。

F3 正式前端构建收尾此前已通过，本批仅核对未漂移；不重新写成前端仍受 TS2769 阻塞，也不扩大为所有 ERP 功能已验收。恢复核对仍为“缺单报告 + 人工核验修改、取消及退款”，最终业务边界待确认。

## 6. 后续 b1 启动对应关系与命令——仅交付，待重新批准

下一步沿用原 b1 运行目标，**不是启动历史 r7/s1/s2，也不是创建下一轮后端验收**：

| 对象 | 固定值 |
| --- | --- |
| 执行工具 | target/platform-b1-preparation-20260920/a1/snapshot/platform-b1-batch.mjs |
| 包装器及隔离配置 | 新 a1/classes 与新 a1/snapshot/platform-b1-isolation.properties |
| 后端/前端产品 | 未变的 r11 classes/资源/冻结依赖 + F3 dist |
| 运行根 | D:\ERP_WMS\BC warehouse\2G\target\platform-browser-invitation-20260919\b1 |
| 待创建数据目录 | 上述运行根\pgdata |
| 数据库 | p3_platform_browser_b1 |
| 专用角色 | p3_b1_initializer / p3_b1_migrator / p3_b1_app |
| 控制 schema | p3_b1_control |
| 监听 | PG 127.0.0.1:55459；后端 127.0.0.1:18092；代理 127.0.0.1:15174 |
| 后续运行证据 | 新 docs/releases/platform-b1-preparation-20260920/a1；旧 b1 43 个证据文件不作为写入目标 |
| 执行上下文 | 工具审批后重新核验 ARNOLD\Arnold 及已约定令牌上下文；本轮未做实际身份检查 |
| 临时唤醒 | 不申请、不继承 r11 授权 |

运行根虽已包含旧快照/classes，本方案不读取其工具或包装器作为新执行输入；只保留它们，并在尚未存在的 pgdata/private 等确切 b1 运行目标创建本批数据。旧文件只做保护性哈希检查，不承担依赖兜底。启动前如运行目标/启动标记已存在、端口冲突或指纹不符即停止，不覆盖、不换目标；目标状态变化先报告。

**待另行批准后的精确启动命令**（cwd 必须是仓库根，本批未执行）：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b1-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b1-invitation-only-20260920
~~~

完整准备结果、输入/产物校验通过后，仍必须现场通过实际身份/端口前置检查才可初始化；保持 SCRAM、新随机凭据、运行角色无 SUPERUSER/BYPASSRLS 且不拥有业务表、HTTP 不运行 Flyway、无默认库回退。只准备 A，B 不预建，通过真实邀请取得安全审计员岗位，不授予租户数据读取/导出委派。

后续固定预算不变：初始化/迁移各 180 秒，SQL 30 秒（statement 15 秒），启动器 37 秒（内部命令 35 秒），PG 就绪 40 秒，独立收尾 60 秒（停止命令 32 秒），全量查询 15 秒、PID 查询 5 秒、就绪 SQL 4 秒；后端就绪 90 秒，前端就绪 10 秒，后端正常停止 30 秒，前端停止 10 秒。实际命令及完整预算已封存在 input.json，不在运行中加时。

环境就绪后逐步引导：A 创建并人工转交一次性邀请 → B 设置密码 → 首次 MFA → 保存恢复码并确认 → 激活登录/刷新 → 退出后旧链接拒绝 → 正常再次登录。人工转交不算邮件送达；密码、令牌、MFA 密钥、恢复码及 JWT 不进入聊天或证据。

完成主线后，使用同一冻结入口发送关闭请求（本批未执行）：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b1-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' stop MANUAL_COMPLETED
~~~

中止人工流程时使用 stop ABORT，而非将未完成记为完成。自动启动/运行错误由监督入口保留首因并进入独立收尾；先停代理、再后端及 worker、再按核实身份正常停 PG，核验监听/进程残留并清除确切归属临时认证材料。收尾错误不能覆盖首因，也不自动修补重试。

**恢复 b1 数据库/服务启动及浏览器验收须用户另行确认；本次只交付上述命令，不执行。**

