# 平台 R2 工具链与隔离验证前置交付

日期：2026-09-11。结论：本批已完成固定工具链、测试层隔离适配、无数据库编译及防护测试。**尚非发布候选版本，不可上线；没有执行数据库批次。**

本批未连接或启动 PostgreSQL、创建数据库、执行迁移或数据库集成测试；未操作真实账号/租户/数据库或既有服务；未提交、合并、推送。产品代码、前端代码、POM、公共默认数据源、项目版本声明与锁文件均未修改。上一批封存目录与其他任务成果保留。

## 1. 固定工具链实际结果

| 工具 | 实际核验 | 本批执行方式 |
|---|---|---|
| Node | **24.12.0** | `D:/JAVA/NODE/node.exe` 显式调用 |
| pnpm | **11.16.0**，包要求 Node >=22.13，所选 Node 24.12.0 满足 | 专用目录内的 `package/bin/pnpm.cjs`，由上述 Node 执行 |
| Java | **17.0.17**，Corretto 17.0.17.10.1 | `C:/Program Files/Amazon Corretto/jdk17.0.17_10` |
| Maven | **3.9.9** | 既有专用 Maven，离线读取现有依赖；JAVA_HOME 仅设置在验证子进程 |

pnpm 官方来源：[固定版本元数据](https://registry.npmjs.org/pnpm/11.16.0)、[固定版本包](https://registry.npmjs.org/pnpm/-/pnpm-11.16.0.tgz)。校验名称、版本、官方 tarball 地址、SHA-512 integrity；解包前检查路径与文件类型，拒绝链接和越界路径；实际 CLI `--version` 为 11.16.0。

- 工具目录：`target/platform-validation-tools/pnpm-11.16.0`。
- 包 SHA-256：`3f2a848545f82fce6966f3ecaa8b7d87b5563f901a170e2bd573b02296b382fa`。
- CLI SHA-256：`67b035e322203961795e8e34ca63a08c37a4386eda94107fb3d28f3246d882ad`。
- Node 可执行文件 SHA-256：`2ffe3acc0458fdde999f50d11809bbe7c9b7ef204dcf17094e325d26ace101d8`。
- 首次下载受沙箱网络权限拒绝，随后经工具授权从同一官方地址成功获取；未改用未知镜像。
- 没有安装/升级项目依赖，没有执行全局安装、Corepack enable、修改全局 PATH 或用户配置。
- 项目锁与现有安装锁仍同为 `7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e`。

后续正式前端运行将显式使用 Node＋该 CLI，并仅在相应验证进程中把 Node 24.12.0 的目录置于子进程 PATH 首位，保证 TypeScript/Vite 子命令不选到另一套 Node。冻结安装只进入新的正式快照目录；不会修改现有 node_modules 或公共锁文件。安装要求改锁时停止，不自动修复/升级。

来源及校验记录：[toolchain.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-r2-preflight-20260911/p2/toolchain.json>)。

## 2. 本批修改范围

新增 4 个 Java 测试辅助文件，均在 `src/test/java/com/wms/platformverification`，不会进入产品 JAR：

- `PlatformR2Isolation`：固定 URL/角色/许可校验；运行及迁移连接身份核验；迁移源文件与历史指纹；不允许连接参数覆盖。
- `PlatformR2SpringInitializer`：测试环境最高优先级配置、专用 DataSource、关闭自动迁移/DDL/调度、禁止真实第三方 HTTP、模拟邮件。
- `PlatformR2DatabasePreparer`：独立迁移准备器，仅允许明确的四个迁移步骤；`plan` 模式不连接数据库。
- `PlatformR2IsolationTest`：纯 JUnit/Mockito 防护测试，无 Spring 应用启动、真实 JDBC 或网络连接。

新增 `src/test/resources/platform-r2-isolation.properties`，替代这些专用测试的 application 配置；不修改默认 application*.yml。固定合成测试向量仅用于隔离测试，不取用旧 A/B/C 或正式认证材料。

适配以下 8 个既有数据库测试套件的入口、配置、身份前置检查和迁移职责：

1. `PlatformSessionPostgresTest`。
2. `PlatformInvitationSessionPostgresTest`。
3. `PlatformAccessGrantRoleRaceIntegrationTest`。
4. `PlatformAdminInvitationMfaTransactionIntegrationTest`。
5. `PlatformCompanyDataServiceIntegrationTest`。
6. `TenantSecurityPostgresTest`。
7. `TenantSecurityCommandsPostgresTest`。
8. `TenantSecuritySpringPostgresTest`。

原本测试内的 Flyway migrate 和探针表 DDL移至专用准备器；运行角色只检查准备完成的结构与 RLS，不执行迁移。原邀请套件的硬编码旧地址改为同样严格的新身份检查。共同前置条件需显式 `p3.r2.database-authorized=true`；没有该许可不能进行数据库验收。未来专用执行器必须核对实际执行套件/用例数量及零跳过，不能把条件未满足导致的跳过当成通过。

与上一批 r1 源文件按测试方法正文比对：**63 个既有 `@Test` 方法正文全部一致**，仅测试入口/夹具基础设施适配；没有删除业务测试、放宽业务断言或改 POM includes。新增防护断言单独计数。

新增工具：`platform-r2-toolchain.mjs`、`platform-r2-preflight.mjs`、`platform-r2-evidence.ps1`。均为本批专用工具，不是通用任务中心，不修改/调用原 CI 的 Backend/All 模式。原 CI 指向 `5432/wms_db_test`，仍禁止在本批使用。

## 3. 实际编译与无数据库测试

| 检查 | 实际结果 |
|---|---|
| 首轮 p1 源码编译与打包 | 通过；主源码 580、测试源码 192，未复用旧 classes |
| 首轮 p1 防护测试 | 35 项：33 通过、2 错误、0 跳过；Mockito 嵌套 stubbing 夹具写法错误，不是数据库失败 |
| 第二轮 p2 全新目录重编译与打包 | **通过，exit 0**；主源码 580、测试源码 192；未复制 p1/r1 或浏览器旧 classes |
| 第二轮 p2 防护测试 | **36/36，0 失败/错误/跳过，exit 0**；修复夹具并新增迁移历史篡改拒绝验证 |
| Node 工具语法检查／证据脚本解析 | 通过 |
| 迁移准备器 `plan` | exit 0，明确输出 PLAN_ONLY，无连接 |
| 编译输入复核 | 无漂移；产品/前端/POM 相对 r1 受保护文件无变化 |
| 数据库集成/迁移/真实 Spring 接线 | **未执行** |
| 冻结依赖安装、正式类型检查、整包前端构建 | **未执行，地址测试仍阻塞** |

36 项防护覆盖：无许可/缺配置、旧端口/旧库/localhost 别名/远程地址/URL附加参数、公共数据源冲突、运行角色不得代用迁移角色或增量库、实际库名/地址/端口/角色/超级权限/RLS绕过/物理目录/集群号不符、迁移历史缺失或错序、源文件或历史指纹不符、拒绝任意迁移命令、禁止连接凭据覆盖，以及全部模拟条件匹配的正向路径。

密码只允许从专用进程环境读取，不通过命令行或 Java 系统属性输入；避免出现在 Surefire 系统属性报告中。连接设置的 toString 脱敏。MockMvc 认证响应不打印，交付只包含摘要与哈希，不归档原始 JWT、密码、MFA 密钥或恢复码。

首轮结果没有删除；第二轮新增用例不与首轮重复累加。上一批的 186 项单元测试和历史浏览器结果均不计入本轮 36 项，也不能替代数据库验收。

## 4. 编译输入标识与正式快照边界

本次前置编译输入指纹（p2）：

`97b1bdbe5e37025c3fbc60da02904d1502419b6e7c263c245210fe7601edfbc1`

它覆盖 903 个后端/测试/工具及必要版本和阻塞检查输入，带有逐文件哈希和 63 个原业务测试正文哈希。编译前后复核一致。基础分支仍为 `2-1G`，提交仍为 `9c12f6f893105632c4de24ad8bb4f6f43092f84e`，没有产生提交。

这是**前置编译输入指纹，不是正式 R2 全源码快照或候选版本**。按照用户约定，地址错误解决后才固定正式快照并进行冻结安装及完整前端重建；本批没有提前创建正式 `r2/source`。

- 新输出目录：`target/platform-r2-preflight-20260911/p2/backend`。
- 新 JAR：79,940,476 字节，SHA-256 `262f6a98e14c12b205a63fd90b847b8ad993dc7aceaf29c39958d2a2c5fcb3dd`。
- 测试工具 class 指纹、编译日志指纹、p1/p2 实际结果：[preflight-results.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-r2-preflight-20260911/p2/preflight-results.json>)。
- 输入清单：[input-manifest.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-r2-preflight-20260911/p2/input-manifest.json>)。
- 结果采集脚本在编译后添加，仅采集脱敏证据，不属于上述 Java 编译输入；其源码哈希为 `14a2b84b1e1832e1c3e4e67642e9dea83623c89b2c9550404a98d284907e894c`。

## 5. 数据库批次准确执行范围——尚未授权

| 资源 | 固定设计 |
|---|---|
| PostgreSQL | 现有 18.6 可执行程序另建专用集群，只监听 `127.0.0.1:55449` |
| 物理目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r2/pgdata`；本批尚未创建 |
| 主验收库 | `p3_m1_platform_release_r2` |
| 增量演练库 | `p3_m1_platform_upgrade_r2` |
| 迁移角色 | `p3_r2_migrator`：非超级用户、无绕过 RLS，拥有这两个测试库的迁移结构权限 |
| 运行角色 | `p3_r2_app`：非超级用户、无绕过 RLS、不是表拥有者；授予本批测试所需 DML/序列/函数权限，不得改 Flyway 历史或就绪凭证 |
| 数据 | 空库＋源码 B 基线的公共参考目录＋纯合成夹具；不用任何真实/旧验收库副本、旧账号或旧认证材料 |
| 执行 | 独立测试 JVM + Spring/MockMvc；不启动前端或 HTTP 服务，不恢复 15173/18091/55439 |

启动批次获批后，才可创建集群/两个数据库与角色。新集群专用初始化管理员仅管理该集群；迁移角色需由它明确授予读取 `data_directory` 和执行 `pg_catalog.pg_control_system()` 的权限，用于迁移前物理身份核验。运行角色通过迁移角色拥有的固定只读函数核验相同信息，不授予运行角色超级权限或广泛系统读取权限。

每次运行连接同时检查主库、127.0.0.1、55449、`p3_r2_app`、非超级用户/非 BYPASSRLS、固定物理目录、预先记录的集群 system_identifier、八项迁移历史、源码与历史指纹。迁移连接也须校验物理身份后才交给 Flyway。身份错误、密码缺失、历史/校验和不符立即拒绝；不尝试其他库或默认 URL。

准备器仅有下列步骤；**本批只运行过 `plan`**：

1. `prepare-release`：主验收库须为空，执行 B4_71、V4_72、V4_85–V4_90；最终建测试探针/只读就绪凭证并授予测试角色权限。
2. `upgrade-to-88`：增量库须为空，执行 B4_71、V4_72、V4_85–V4_88。
3. `upgrade-to-89`：严格匹配前六项历史后，只推进 V4_89。
4. `upgrade-to-90`：严格匹配前七项历史后，只推进 V4_90；最终准备相同测试辅助结构。

每步实际迁移数必须等于预期增量；验证已应用迁移；随后的重复 migrate 必须为零。允许本步尚待执行的 pending 状态，不忽略 checksum 错误、缺失已应用文件或额外历史。不提供 clean、repair、任意 baseline、任意库名、启动或创建数据库命令。准备器不会创建数据库，也不会操作旧环境。若迁移完成但夹具准备失败，保留现场报告，不自动覆盖/清库重试。

两个库最终历史均应为：`4.71(B), 4.72, 4.85, 4.86, 4.87, 4.88, 4.89, 4.90`。现版应用测试只在 V4_90 且就绪核验通过后运行。增量步骤间的合成哨兵投放、结构/记录保留核验，要在获准数据库批次实际执行并记录，不能用静态源码检查冒充迁移成功。

上述路径仅验证当前平台源码，**不是生产迁移方案**。ERP 的 V4_73–84 及回调仍待跨任务整合；不在此复制或修改。PostgreSQL 18.6 高于当前 Flyway 已测试版本范围的既有提示继续保留，真实兼容性要在数据库批次及后续目标生产版本演练中分别核验。

数据库套件选择为第 2 节 8 个套件，邀请子类只选择 `#invitation*`，避免再次累加继承的会话测试；目标为现存 63 个方法，执行前按正式新快照再核对。零执行、意外跳过、遗漏套件、错误或超时均不能验收通过。范围覆盖会话/MFA/邀请事务、权限竞争、组内退出并发、七天清理、P3 暂停/恢复原子性、RLS/在途任务、受控读取导出及必要 Webhook/连接安全。不扩展租户采购销售库存功能。

结束时先关闭本批测试 JVM 与连接池，再核对专用 PostgreSQL PID、启动时间、可执行路径、55449 与物理目录，正常 `pg_ctl stop -D <固定目录> -m fast -w -t 30`；只处理本批拥有的进程。已有 5432/8080/5173 和旧隔离环境均不操作。停止异常先报告，不按模糊进程名批量结束。保留脱敏结果及专用数据目录，不把明文临时凭据并入交付证据。

## 6. 剩余阻塞与停止点

1. [地址测试](</D:/ERP_WMS/BC warehouse/2G/frontend/src/test/addressLength.audit.test.tsx:23>)仍包含 `getByRole(..., { exact: true })`，文件与 r1 一致，TS2769 继续交由所属任务处理。未执行安装、正式类型检查或整包前端构建，未宣称其状态转为通过。
2. 地址错误解决后，先核对其他任务交付与本任务测试适配，再固定正式新源码快照，使用确认工具链和冻结锁文件安装，重新编译、类型检查、构建及运行受影响的平台测试。需要改锁/公共配置时停止说明，不能自行绕过。
3. 数据库迁移、真实权限、Spring 数据库接线、事务、并发、DDL/Flyway 的真实行为均未验证；本批 36 项模拟防护测试只证明前置防误连逻辑，不能代替这些验收。
4. 邀请完整人工浏览器激活仍待发布候选验收；已完成 MFA/恢复码人工子项不无故重复。
5. 恢复核对仍是“缺单报告＋人工核验修改、取消和退款”，最终业务边界未确认；本批不扩展自动核对或第四、第五阶段。

**停止点：前置工具与适配可交付、无需数据库的实际结果明确、正式重建阻塞保留。数据库启动/创建/迁移/集成测试仍待用户单独批准。不得据本报告直接上线。**
