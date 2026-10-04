# 后端启动失败的脱敏定位与无数据库验证

日期：2026-09-20；完整验证结束于北京时间 21:21:57。

**本批工具验证通过并停止。** 16 项 Java 契约、231 项 Node 检查、36 项语法检查全部通过；其中新增 38 项 Node 检查包含 16 次真实 Java 合成子进程，经实际监督器的接收、解析、合并及最终报告落盘入口验证。没有数据库连接、PostgreSQL/Spring/前端启动或下一轮运行环境创建。

**b2 的原始 FAILED、目录、证据和未知原因保持不变。** 本批不证明 b2 的具体根因已定位或修复，更不代表邀请激活/上线验收通过。

## 1. 本批修改及实际接入

仅修改三个专用测试工具文件：

- `src/test/java/com/wms/browseracceptance/PlatformB1Harness.java`：在实际操作前记录阶段，替换原来只有 type/rootType 的失败输出。
- `src/test/scripts/platform-b1-batch.mjs`：实际子进程接入有界接收器，失败/收尾时合并诊断，最终落盘使用同一导出入口；收尾错误不再附带任意 exception message。
- `src/test/scripts/platform-b1-prepare.mjs`：明确封存新增 Java/Node 文件，接入新增 38 项门禁；未来启动器检查该门禁成绩。

新增五个专用文件：

- `PlatformBackendDiagnostic.java`：阶段、异常链/栈位置的安全协议输出，JDK-only。
- `PlatformBackendDiagnosticProbe.java`：JDK-only 合成失败进程，不调用实际 Harness.main、不加载 Spring 或 JDBC。
- `platform-backend-diagnostic.mjs`：协议校验、分流缓冲、受限落盘及证据合并。
- `platform-backend-diagnostic.test.mjs`：22 项协议/接入检查 + 16 项真实合成 Java 链路检查。
- `platform-backend-diagnostic-verification.mjs`：新封存、新输出、独立编译、完整受影响门禁及历史保护。

Java 文件位于上述 browseracceptance 包目录；Node 文件位于 src/test/scripts。未修改产品逻辑、迁移、公共默认配置、既有业务断言、隔离要求或 ERP 功能。

实际阶段按执行位置区分：

| 标记 | 含义 |
| --- | --- |
| ARGUMENTS | 参数、工作目录前置条件 |
| CREDENTIALS_READ | 读取专用私有配置，日志不含内容 |
| ISOLATION_CONFIG | 解析并验证精确隔离配置 |
| DATABASE_IDENTITY | 首次受保护的数据库身份与迁移准备状态检查 |
| SPRING_CONFIGURATION | 构造 SpringApplication 和专用配置 |
| SPRING_START | Spring 上下文启动 |
| SYNTHETIC_ACCOUNT_PREPARATION | 仅合成邀请人 A 的基线核验及准备 |
| READY / WAIT_STOP | 就绪后等待本批正常停止 |
| SPRING_CLOSE / COMPLETE | 关闭上下文及完成 |

这些字段是发生异常时的执行位置，不将其一律归因为产品失败，也不把“进入某阶段”冒充该阶段成功。

## 2. 安全边界和首因保留

- 协议前缀固定为 BROWSER_DIAGNOSTIC_V1；schema/version/event/stage/reason 字段严格校验，未知或额外字段拒绝，不保留原文。
- 只输出异常类型、最多 4 层 cause、每层最多 4 个类名/方法名/行号，以及最多 2 个 suppressed 异常。链/栈/抑制列表被截断时有明确标记，循环 cause 不会无限遍历。
- 类名只允许指定 Java/框架/本项目命名空间和安全字符，最长 160；方法名最长 100；源码行号范围 -2 至 1000000。未知/非法标识替换为 UNKNOWN，不输出文件路径。
- 原因码限 UNKNOWN、ARGUMENTS_REJECTED、ISOLATION_PRECONDITION、UNKNOWN_ACCOUNT_RESIDUE、REFERENCE_ROLE_MISSING、SEED_ROLE_FAILED、STOP_IDENTITY_MISMATCH。仅当异常类型、已知来源类及消息完全匹配专用工具已有常量时映射；消息本身永不输出，其他情况为 UNKNOWN。
- 单行最多 12288 字节、最多 32 个事件；stdout/stderr 分别缓冲，不能把两个流的半行拼起来。超长行持续丢弃到换行，不把尾部伪装成新协议；结束时残缺协议拒绝并标记。
- 非协议输出只计数，不落盘；旧 READY/CLOSED 标记仅接受四个精确常量，不接受任意 type/message 后缀。未启用全量调试日志。
- 实际监督器共享 `attachStartupCapture` → `mergeBackendDiagnostic` → `saveBrowserFinalReport`，真实 Java 合成验证也走这条接收/最终落盘链。不是独立的正确示例。
- `firstFailure` 保留监督器原始失败分类，并附上安全 backend 诊断；cleanupErrors 保持单列。诊断损坏/不完整也单列 diagnosticErrors，不能覆盖已有首因或算成功。退出后最多额外等待 1 秒排空诊断流，未确认会明确标记，不自动增加既定服务超时。

被抑制异常只标为 suppressed，不擅自断定每个 suppressed 都来自收尾。若 JVM 在进入 main/诊断器之前失败，仍可能没有阶段记录；本次没有宣称能覆盖所有宿主、类加载或输出环境异常。

## 3. 实际无数据库结果

新准备根：`D:/ERP_WMS/BC warehouse/2G/target/platform-backend-diagnostic-repair-20260920/a1`。

新证据根：`docs/releases/platform-backend-diagnostic-repair-20260920/a1`。

固定 Node 24.12.0、Java 17.0.17；使用经工具审批的专用执行通道读取受 ACL 保护的历史目录。没有 OS 身份补查、数据库启动命令、网络依赖安装、临时唤醒或服务操作。

唯一验证入口执行一次：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-backend-diagnostic-verification.mjs' no-database-diagnostic-only
```

| 实际检查 | 结果 |
| --- | --- |
| 冻结输入 | 46 个文件 |
| 语法 | 36/36，通过 |
| 新独立编译 | 17 个 class，不复制 b2 包装器 |
| 既有 Java 契约 | 16/16，通过，原断言和指纹不变 |
| 既有 Node 门禁 | 193/193，通过，原测试文件指纹不变 |
| 新诊断门禁 | 38/38，通过，含 16 项真实 Java 子进程 |
| Node 合计 | 231/231，失败/取消/跳过/待办均 0 |
| 真实 Java 合成进程 | 16 次均按预期退出 1、完成输出排空；最长 137 毫秒 / 10 秒预算 |
| 合成落盘证据 | 42 文件；敏感值排除扫描通过 |

16 个 Java 进程分别覆盖 11 个阶段，以及白名单原因码、深链、循环 cause、非法栈字段、混合非协议噪声。**退出 1 是合成失败的预期结果，验证的是诊断和关闭，不是服务启动成功。** 合成进程只使用新的包装器 classes 作为 classpath，不包含 Spring/JDBC 依赖，也不调用实际服务入口；DATABASE_IDENTITY/SPRING_START 等仅为模拟阶段标签。

其余检查覆盖额外/缺失/嵌套敏感字段、非法类型/行号/布尔值、超限链和帧、逐字节分片、双流不拼接、CRLF、超长行尾部注入、截断（含半个协议前缀）、重复失败、阶段不一致、事件上限、历史文件拒绝覆盖、首因/收尾区分以及实际 Java/监督器/准备器调用点。

合成定位里的类名、seed 方法及行号 127 是显式合成测试夹具，不是本次 b2 实际异常栈，绝不能倒填历史。

所有成绩来自同一新冻结输入，本批没有拼接 b2 或其他历史成绩。预算固定为语法每项 15 秒、编译 300 秒、Java 契约 60 秒、Node 每套 120 秒、合成 Java 每个 10 秒、诊断排空 1 秒；未增加等待时间。

## 4. 指纹与历史保护

```text
输入聚合（46 文件）
9e341c1f36ebebf10d2f4ba713a95f4eb2854a49f9d00dbe045c604f494d6683
新编译 class 聚合（17 文件）
21eb0b43f7ec0ce717d660080ad4191f804b33f9030dd811161d9c47d87c6c97
input.json
629ba49a3116da6e1cdc8b18ec889698b0d6af24f7a985cab9e3bd3c022af173
compiled.json
050e4f5677a9d10de686c42a710b3d91d1f1efa2ba0d7c4cf672209e0facc843
result.json
24a450e1deceb0ebce4d442111075ae9b87bb6ae9e302b627681267c39716476
synthetic-files.json
587193d640fffed3a926afcbed1ab9c968bd13af65e91a6cd90ff97f0fa44ee2
合成证据聚合（42 文件）
db3e351ff0a2e84a0503a7f8c456dd2e3b9cd5b898f7dcc85522735861e12993
```

input.json 保存来源、依赖及旧目录清单；compiled/result、逐命令回执、门禁 TAP/assessment、synthetic-files.json 和 delivery-summary.json 保存实际成绩及对应关系。

前后核对 b2 准备 54 文件、证据 175 文件、停止后的运行目录 1910 文件，逐文件及清单均未变。b2 私有目录仍为空；既有 b1 历史由 b2 冻结校验继续匹配。F3 和 r11 产品指纹一致，仅只读复核，没有重建或重跑。

本诊断包仍保留 b2 的旧绑定作为测试输入，**不是可直接用于重新启动 b2 的授权包**。没有调用其 supervisor；未来获批才对新目标绑定并重新封存。

## 5. 已验证能力、静态疑点与尚待实库确认

### 已验证的诊断能力

实际 Java 发出有界安全元数据；真实监督器接收函数处理分片/双流；解析拒绝非法字段；最终报告保存阶段和安全异常位置，首因与收尾错误分开。上述能力有本批合成证据，不依赖 b2 旧结果。

### 静态发现，不能当作 b2 根因

旧包装器把全部初始化包在一个 catch 中，只留下 type/rootType，且旧监督器合并 stdout/stderr 的未结束片段；这些是已确认的取证不足，本批已针对性改正。旧证据不包含新阶段/栈信息，不能追溯生成。

只读源码可见专用隔离拒绝、合成账号基线拒绝以及产品 JWT 初始化等不同路径均可能产生 IllegalStateException；**同一异常类型不能决定是哪条路径**。静态核对 URL、角色、授权属性和环境变量名称未发现新的绑定不一致，但没有实库启动证据证明配置解析、受保护 JDBC 或 Spring 初始化均成功。

没有找到足以确认 b2 根因、或授权修改产品的静态证据。未将密钥、RLS、Spring 配置等候选方向写成已确认故障。

### 仍需新环境真实启动才能确认

实际失败阶段、异常链/安全栈位置、是否能映射白名单原因码；问题属于测试包装器、隔离配置、数据库准备还是产品；修复范围；邀请人工主线是否能够继续。这些均未在本批验证。b2 原 FAILED 和通用错误码不能被本批合成结果替换。

## 6. 一次完整的新环境取证/验收方案——待批准

建议将后续必要目标绑定、封存编译、门禁、全新隔离启动及邀请主线合为 **b3 一次性批次**；本节仅提出方案，本批没有创建任何 b3 目录或启动环境。

| 项目 | 拟定固定值 |
| --- | --- |
| 运行根 | D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b3 |
| 数据/私有目录 | 上述根/pgdata、上述根/private |
| 准备根 | D:/ERP_WMS/BC warehouse/2G/target/platform-b3-preparation-20260920/a1 |
| 证据根 | D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b3 |
| 维护库/验收库 | postgres / p3_platform_browser_b3 |
| 三角色 | p3_b3_initializer、p3_b3_migrator、p3_b3_app |
| 控制 schema | p3_b3_control |
| 端口 | 127.0.0.1:55459 / 18092 / 15174（PG / 后端 / 代理） |
| 工具及身份 | Node 24.12.0、Java 17.0.17、PostgreSQL 18.6、固定系统 PowerShell；工具审批后现场核验 ARNOLD\Arnold 提升上下文 |
| 产品/数据 | 原指纹一致的 F3 / r11；仅空库、源码公共参考数据、合成账号；无真实数据 |
| 唤醒与系统设置 | 不申请唤醒，不修改电源/UAC/执行策略 |

拟执行顺序及停止点：

1. 只做新 b3 测试目标绑定及相应文字断言，接入本批诊断。重新封存、独立编译必要包装器，完整执行 16 项 Java、既有 193 项 Node、新增 38 项诊断及最终清单中的语法检查。不得直接复用本批 classes 充当新目标产物。
2. 冻结最终命令、预算、指纹和连接目标；复核 F3/r11 不变。任何来源、产品、依赖、目录或端口冲突则停止，不修改产品、回退目标或重试。
3. 现场 Windows 身份核验；ACL 收紧/完整读回通过才生成随机临时凭据。一次初始化和启动新 SCRAM 集群，核验目录/进程/父链/监听/集群号/SQL 身份。创建指定新库和角色，空库迁移至 V4_90并验证重复迁移零变更；运行角色无超级用户、无 BYPASSRLS且不拥有业务表。
4. 启动一次新后端，使用诊断协议记录真实阶段。失败立即停止后续并安全关闭，不自行修复重启。如果通过，启动代理并核验 F3 页面及隔离边界，不改产品、不重跑 r11 全套验收。
5. 用户方便时逐步完成合成 A（inviter-b3@platform.invalid）发起邀请，B（invitee-b3@platform.invalid）经真实邀请取得安全审计员岗位；B 不预建、无租户读取/导出权限。验证密码设置、首次 MFA、恢复码确认、激活登录/刷新、退出后旧链接拒绝及再次正常登录。人工转交不算邮件送达，不以 API/改库替代。
6. 任何阶段失败保留首因和独立收尾结果，停止后续；完成后关闭仅属于本批的代理、后端和数据库，核验残留并清除确切归属的临时认证材料。主线完成且证据核对后才能使用 MANUAL_COMPLETED；否则 ABORT/FAILED。不自动重试、复用 b2 或创建下一轮。

预算拟保持：编译 300 秒、Node 每套 120 秒、Java 契约 60 秒、语法 15 秒、合成进程 10 秒；初始化/迁移各 180 秒；ACL/SQL 30 秒、statement 15 秒；启动器 37 秒（内部 35）、PG 就绪 40 秒、独立收尾 60 秒、停止命令 32 秒；全量查询 15 秒、PID 查询 5 秒、就绪 SQL 4 秒；后端就绪/停止 90/30 秒、代理就绪/停止 10/10 秒；诊断流排空单独最多 1 秒。不得运行中加时。

本方案获批后可以从前置验证连续执行至人工步骤及收尾，工具权限审批仍正常进行。**本批当前停止在工具交付，b3 方案尚未获批，不进入真实启动。** 发布候选版本、正式上线准备和恢复核对最终业务边界继续独立保留。
