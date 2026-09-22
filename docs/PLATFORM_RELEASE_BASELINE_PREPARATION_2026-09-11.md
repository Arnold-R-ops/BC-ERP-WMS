# 平台交付整理与源码重建验证

日期：2026-09-11。批次：`platform-release-baseline-20260911/r1`。

**结论：本批无需启动服务的整理、源码重编译和专项验证已完成；正式前端类型检查及整包构建仍被范围外地址测试阻塞。不是整工作区全部通过、发布候选版本验收通过或上线授权。**

本批没有修改产品代码、公共安全逻辑、正式检查配置或迁移 SQL。没有连接/启动数据库、启动/重启服务、执行迁移、操作账号/租户、提交/合并/推送或开放生产。没有自动恢复已停止的浏览器隔离环境。本记录只保留脱敏证据，不读取、复制或归档历史 private 目录中的原始认证材料。

## 1. 源码、依赖和产物对应关系

| 项目 | 本批固定值或实际结果 |
|---|---|
| 工作分支 | `2-1G` |
| 基础提交 | `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；未提交工作快照，不是新 Git 提交 |
| 源码快照 SHA-256 | `1f21d07756d4376eb950ca28ed10817b3415e887a7d767f3e4f716429fbf9671` |
| 输入范围 | 1,127 个文件：当前后端主源码/资源、测试源码/工具，前端 src/public、入口、正式检查/构建配置、POM 和前端锁文件 |
| 独立源码目录 | `target/platform-release-baseline-20260911/r1/source` |
| 全新后端输出目录 | `target/platform-release-baseline-20260911/r1/backend`；未复制旧 classes/test-classes |
| 后端重编译 | 580 个主源码文件、188 个测试源码文件；编译测试不等于执行所有测试 |
| JAR | `wms-system-0.0.1-SNAPSHOT.jar`，79,940,476 字节 |
| JAR SHA-256 | `84340fdbbc7d0d48e1954684ddc0c1545975247179e05b7b07bdcbb9152a0c4c` |
| 包内核验 | 855 个包内业务 class 与本批新 classes 逐个一致；94 个迁移 SQL 与快照逐个一致；另记录全部 1,070 个主/测试 class 哈希 |
| 前端锁文件 SHA-256 | `7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e` |
| 前端依赖 | 快照仅链接现有 node_modules；安装锁与源码锁一致，不重新安装依赖、不复用旧业务构建产物 |
| 构建后源码复核 | 快照与工作区的上述 1,127 个输入文件均无变化；新增整理文档/证据工具单独列账 |

后端是共用工程，重新编译共用源码是核验平台依赖所必需，**不代表已整合其他 ERP 分支，也不意味着该 JAR 是可独立部署的平台专用包**。它只用于本次验证，不应启动或作为生产发布包直接使用。没有进行分支切换、远端 fetch 或跨任务整合。

源码快照不是脱离依赖的完整离线发行包。前端依赖通过 junction 引用原 node_modules；后端复用现有 Maven 依赖缓存，而非旧业务类。快照内容不包括 target、node_modules、private、`.env*`、本地认证文件或历史日志；源码内合成测试向量不等于真实账号材料。

工具链实际为 Java 17.0.17、Maven 3.9.9；直接运行 Vitest/Node 测试使用 Node 24.12.0。现有 pnpm 包装器使用 Node 24.19.0 / pnpm 11.19.0，而 package.json 声明 pnpm 11.16.0。**此差异已记录，未修改声明或安装新版本；固定发布候选工具链时须统一/确认并重跑正式检查。** TypeScript 5.9.3、Vite 8.1.4、Vitest 4.1.10 等实际依赖版本列于依赖清单。

后端 POM/父 POM不是严格依赖锁文件；本批记录了 POM 哈希、JAR 中 95 个运行依赖及测试 classpath 中 136 个依赖 JAR 的版本路径和字节哈希。这是实际解析结果的追溯清单，不冒充 Maven 的严格锁定机制或依赖漏洞审计。

## 2. 四组交付与修改片段边界

完整逐文件清单和 `git diff --unified=0` 修改片段位置见 [delivery-index.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/delivery-index.json>)。该清单基于实际差异修正初始 `change-ownership.json` 的文件名粗分类，以 delivery-index 为归属索引。清单含 132 个已存在的相关改动/参考文件（含最先建立的快照工具），另列 113 个范围外或未归属项予以保留。组间文件重叠不应相加当成独立文件总数。

| 组 | 产品与迁移 | 测试/说明及依赖 |
|---|---|---|
| P3-M1 | `tenant/security`、`tenant/web`、`TenantSecurityProperties`、`TenantSecurityPanel`；暂停/双人恢复、版本检查、受控读取导出、必要连接与 Webhook 处理；V4_85–V4_88 | 保留已实现的租户鉴权/交接、后台任务、RLS、Shopify 等公共安全接入片段；不借此新增 ERP 功能 |
| 平台会话 | `PlatformSessionService/Controller/ExceptionHandler/CleanupScheduler`、`PlatformMfaService`、`PlatformSessionResponse`、`PlatformUserRepository`、平台 JWT、`sessionActivity`、V4_89 | 后端 12 小时/30 分钟/撤销检查，MFA 与会话事务衔接、清理、前端检查与提示 |
| 浏览器退出 | `PlatformBrowserBinding`、`browserLogout`、`storage`、会话/API/邀请衔接的退出屏障片段、V4_90 | 同浏览器同账号组内撤销；独立登录的计时仍独立；故障提示与延迟消息保护 |
| 验收工具与记录 | 专用 browseracceptance 辅助类、已有 scripts、历史 P3-M1/会话/浏览器验收记录；本批 3 个整理脚本、本文及 releases 脱敏清单 | 工具不会作为生产入口开放。Phase 3 完整设计及 Phase 4/5 文件仅作为保留的历史参考，不扩展本批功能范围 |

四组只是交付归属和未来提交边界，**不能分别部署**。第一、二阶段已有管理员目录、邀请、启停、MFA、授权、审计继续沿用，不重复开发。

公共/交叠片段的具体核准建议：

| 文件与现有片段 | 归属判断及后续约束 |
|---|---|
| `JwtUtil.java` 租户版本 claim、租户 token 兼容重载；平台 session id、绝对时间重载 | 前者 P3-M1，后者平台会话；不能整文件替换或移除其中一支 |
| `JwtAuthenticationFilter.java` 已验证租户 claims 复用；平台后端会话检查/不可用 503 | 分别 P3-M1、平台会话；避免合并时绕过其中任一状态边界 |
| `DynamicAuthorizationManager.java` browser-session 公共入口与 company-access 状态入口 | 分别浏览器退出、P3-M1；不能覆盖其他任务的资源路径匹配改动 |
| `SecurityConfig.java` 租户过滤器顺序及 bearer/session 注释 | P3-M1 的顺序约束与平台会话语义共存，不重构公共安全配置 |
| `PlatformUserRepository.java` 新增 `findByIdForSessionUpdate` | 平台会话/MFA 锁顺序，不归为新增 P3 租户业务逻辑 |
| `PlatformAuthProvider`、`PlatformApp` | 后端校验/活动上报属于会话；跨标签退出、结果提示和登录屏障属于浏览器退出 |
| `api.ts` | 受控数据访问原因属 P3-M1；会话窗口/活动接口属会话；浏览器绑定及退出屏障属浏览器退出 |
| `PlatformInvitationActivationPage.tsx` 第 72 行附近的中断登录捕获 | 会话/浏览器退出衔接，不是重做邀请激活产品功能 |
| `pom.xml` 的 `wms.build.directory` 属性与 build.directory | 已有隔离构建支持；本批仅使用，不修改插件/依赖或正式测试范围 |
| ERP app/AuthProvider、租户 JWT/刷新/交接、Provisioning、RLS、任务和 Shopify 文件 | 只保留既有 P3 安全接入片段；跨任务修改/整合另批核准，不按文件路径取得 ERP 开发权限 |

本批没有执行上述提交/合并建议。共同文件的未来暂存应逐片段核准，不能 `git add .` 或以四组归类代替实质审查。

## 3. 迁移清单与依赖

快照共 **94 个 SQL：93 个 V 迁移＋1 个 B 基线**；没有重复 V 版本，V4_89、V4_90 均已纳入新 JAR。`B4_71` 和 `V4_71` 类型不同，不算重复版本。完整数值排序及逐文件 SHA-256 见 delivery-index 与 [migration-manifest.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/migration-manifest.json>)；SHA-256 不能直接替代目标库 Flyway checksum 核验。

| 版本 | 作用与前置依赖 |
|---|---|
| V4_85 | 租户安全状态、会话版本和 handoff 版本；依赖已有 tenants / session_handoff_codes |
| V4_86 | 安全请求/命令、连接状态、Webhook 暂存与预算、告警及受控导出原因；依赖 V4_85 和既有平台导出/集成模型 |
| V4_87 | 风险凭据指纹、恢复时间；依赖 V4_86 |
| V4_88 | Webhook 副本保留、核对报告证据、告警处理字段；依赖上述模型及 channel_raw_events |
| **V4_89** | `platform_sessions`，12 小时/30 分钟约束与清理索引；依赖已有 platform_users；不删除平台审计 |
| **V4_90** | `browser_binding_hash` 和组内撤销索引；严格依赖 V4_89。旧行不猜测浏览器归属，缺少绑定的旧会话要求重新 MFA 登录 |

历史基础依赖包括 V4_50 控制面（tenants/platform_users/roles）、V4_53 交接、V4_54 受控导出以及第一、二阶段后续增量；空库 B4_71 已包含相应历史结构，之后还有 V4_72。

当前平台源码仍没有 ERP 分支的 V4_73–V4_84 及三项权限收敛回调：`beforeMigrate__assignable_permission_convergence.sql`、`afterMigrate__assignable_permission_convergence.sql`、`afterMigrateError__assignable_permission_constraint_recovery.sql`。这是跨任务迁移协调事项，不由本批复制/合并。

既有隔离记录的空库路径 `B4_71 → V4_72 → V4_85…90` 不能直接当作生产升级方案。生产应先确定统一候选源码与目标库历史，决定 ERP 增量/回调的纳入顺序；不允许先套平台路径、再靠 out-of-order/repair 补救。现有库不得重复施加空库基线。本批仅验证源码/包内 SQL 一致，**没有执行 Flyway validate、baseline、repair 或 migrate**。

## 4. 本批实际验证结果

所有下面的“本批通过”均对应上述新源码快照/新 classes 或快照中的前端源码；没有把历史结果累加。

| 检查 | 实际结果 | 明确边界 |
|---|---|---|
| 后端源码编译、测试源码编译、JAR 打包 | **通过，exit 0** | 离线 `package -DskipTests`；编译 580＋188 个源码文件；没有启动 Spring 应用 |
| 后端无服务单元测试 | **186/186，40 套件；0 失败/错误/跳过，exit 0** | 平台管理员/授权/邀请/MFA/会话和必要公共安全兼容子集，仓储/外部接口模拟；不是全工程或真实 PostgreSQL 回归 |
| 前端平台专项 | **86/86，13 文件，exit 0** | Vitest/jsdom，未启动网页服务；不能证明完整 TypeScript 或生产 bundle 通过 |
| 前端安全接入兼容 | **6/6，2 文件，exit 0** | 仅 TenantAccessBoundary 与 sessionCacheScope；不是接手 ERP 功能验收 |
| 退出故障选择器工具 | **7/7，exit 0** | Node 纯函数测试，不启动测试代理、不注入浏览器故障 |
| **正式全工作区类型检查** | **失败，进程 exit 1、脚本 exit 2** | `pnpm run typecheck`，实际出现 addressLength.audit.test.tsx(23,68) TS2769 |
| **正式前端整包构建** | **失败，进程 exit 1、脚本 exit 2** | 原样执行 `tsc --noEmit && vite build`，被同一类型错误阻断；Vite 未执行，没有本批前端生产 bundle |

完整套件名称、计数、脱敏用例名、结果文件哈希见 [validation-results.json](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/validation-results.json>)。Maven Surefire XML 中的报告属性可能包含运行环境/模拟输出，原始日志/XML仅保留在忽略的本批 target 中，不作为交付附件复制；公开清单不包含原始 JWT、密码、MFA 秘钥或恢复码。

执行选择明确固定为 40 个无服务后端套件；没有更改 POM 的正式 includes 或删除任何测试。数据库依赖套件未运行，不记为“跳过后通过”。现有安全并发/审批/导出/邀请原子性 PostgreSQL 套件虽已从源码编译，本批并未执行；须另批授权隔离数据库。

使用的核心命令（均从 snapshot 执行/指向 snapshot）：

```text
mvn -o -B -ntp -f <snapshot>/pom.xml -Dwms.build.directory=<r1>/backend -DskipTests package
mvn -o -B -ntp -f <snapshot>/pom.xml -Dwms.build.directory=<r1>/backend -Dtest=<no-service-test-selection.json 的 40 个精确类名> -Dmaven.test.redirectTestOutputToFile=true surefire:test
pnpm run typecheck
pnpm run build
node node_modules/vitest/vitest.mjs run src/platform --no-cache --configLoader runner --maxWorkers=2 --reporter=json --outputFile=<r1>/frontend-platform-tests.json
node node_modules/vitest/vitest.mjs run src/auth/TenantAccessBoundary.test.tsx src/auth/sessionCacheScope.test.ts --no-cache --configLoader runner --maxWorkers=2 --reporter=json --outputFile=<r1>/frontend-safety-compat-tests.json
node --test --test-reporter=tap --test-reporter-destination=<r1>/logout-fault-selector.tap src/test/scripts/platform-session-browser-logout-fault.test.mjs
```

PowerShell 里含点号的 Maven `-D...` 参数必须整体加引号。本批首个 Maven 编译命令因沙箱 Java 用户目录指向空缓存，在解析父 POM 时失败；获准读取现有本地缓存后离线重编译成功。首个单元测试命令因 PowerShell 拆分参数失败，测试未开始；加引号后运行通过。两次失败不算测试失败/通过覆盖，保留诊断记录。证据整理脚本另修正了 classpath 尾分隔符处理及 PowerShell 有序字典计数汇总，已与 40 份原始测试报告的 186/0/0/0 校验一致；未重写测试结果或修改产品代码。

编译仍有 `javax.annotation.meta.When.MAYBE` 注解元数据、JwtUtil 弃用 API、测试 unchecked 提示。它们本次未导致失败，不在本批顺手修复，也不宣称构建零警告。

## 5. 最新规则和历史验收边界

普通退出最新版是：**同浏览器配置/同源、同平台账号的后端会话一起撤销，并通知相关标签清除本地登录信息**；不同浏览器或不同账号不受普通退出影响。“退出所有设备”仍是账号级撤销。函数名中的 Current 或历史旧说明不能用来解释为“只撤销当前一条会话”。

各次登录的 30 分钟空闲计时仍独立，后端最长 12 小时。跨标签退出通知不等于自动共享登录/token/计时。刷新/切页/后台检查不复活超时会话；活动上报不宣称能阻止持有被盗 JWT 的人模拟操作。网络退出失败只能明确本地已清除、后端撤销未确认，不能误报成功。已成功提交的审批/任务不因退出自动取消。会话终态记录七天后分批清理，平台审计独立保留。

历史验收仍以 [2026-09-10 最终验收记录](</D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_SESSION_BROWSER_ACCEPTANCE_REVIEW_2026-09-10.md>) 为准，原文不删除、不改写为本批新测：

- 人工已通过：30 分钟实际静置与刷新、双标签同浏览器普通退出、不同浏览器普通退出/全设备退出、退出请求未送达的真实提示、账号停用/启用/改岗、MFA 错码后重试、恢复码再生成/10→9 单次消费/已用码拒绝。
- 历史末轮自动：后端 69、前端 58、故障选择器 7；22:55 另有审批/导出延续 2 项。后端使用旧 V4_90 classes，仅新邀请测试重编译，不能作为当时最新全部源码已重建的证据。本批 186 项重新编译后运行，仍没有替代这些数据库集成测试。
- 未覆盖保留：完整人工浏览器邀请激活与登录交接；所有浏览器/角色组合；服务端已撤销但响应丢失等其他网络形态；人工 MFA 锁定阈值/重生前旧恢复码重放；12 小时/七天真实等待；生产配置、邮件、调度、迁移与备份恢复。
- P3 暂停/双人恢复、受控读取导出的完整发布候选人工流程，不能由“平台会话浏览器子项通过”推定全部通过。
- 不无故重复已完成 MFA/恢复码等人工步骤；只有后续变更实际影响时才加对应回归。

恢复核对当前实现仍为 **“系统缺单报告＋人工核验修改、取消和退款”**；不是完整自动核对，用户没有确认人工方案为最终业务边界。本批不扩展此能力，也不启动完整第三阶段、第四或第五阶段。未开放功能入口继续关闭。

## 6. 阻塞、范围外交接与后续停止点

| 项目 | 责任边界与下一步 |
|---|---|
| 地址测试 TS2769 | `frontend/src/test/addressLength.audit.test.tsx:23`，传给 getByRole 的 `exact` 不是当前 ByRoleOptions 字段。交由该测试所属任务处理；本任务未修改/删除/排除。修复后必须建立新快照重跑正式类型检查/构建，不能把本次失败改成通过 |
| pnpm 版本差异 | 平台候选工具链固定时统一声明与实际运行版本，保留现有 lock；不需为了此事扩大身份/业务开发 |
| 公共鉴权/前端/Provisioning/Shopify 重叠 | 保留本批已存在安全片段；其他任务有 ERP 资源路径多值匹配、权限初始化、采购销售库存等变更。逐片段协调由另行批准的整合任务负责；本次共用快照单元测试通过不等于 ERP 最新分支已兼容合并 |
| ERP V4_73–84 与迁移回调 | 由整合/统一运维任务核准完整候选序列与目标数据库历史，本任务不复制/合并或运行 |
| 新 classes 的数据库集成/并发验证 | 待另行提出固定库身份、端口、数据来源、拟执行迁移、停止方案并获批；当前保持不启动 |
| 平台人工候选验收/上线配置 | 邀请完整人工流程留在发布候选验收；域名、HTTPS、管理员/MFA 应急、正式邮件密钥、权限、备份恢复与迁移演练按统一运维方案另批确认 |

建议后续顺序：范围外地址测试由所属任务解决 → 平台候选工具链/源码重新固定并复跑正式检查 → 另批批准新源码的隔离数据库/必要人工验证 → 另批平台提交与跨任务整合 → 上线准备与最终审批。不得跳过阻塞直接生产开放；本批不替其他任务修改地址测试或合并 ERP。

**本批停止于清单完整、来源可追溯、实际结果与阻塞明确。当前无需批准生产操作；下一批如要启动隔离数据库/服务，我会先单独提交环境方案，不能沿用旧环境授权自动启动。**

## 7. 交付文件索引

- [源码文件指纹](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/source-manifest.json>)：每个输入文件的原始字节 SHA-256、基准提交、锁文件指纹。
- [四组逐文件/片段索引](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/delivery-index.json>)：分类修订、修改片段位置/差异哈希、范围外保留列表、迁移排序、未运行数据库套件。
- [依赖指纹](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/dependency-manifest.json>)、[后端产物指纹](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/backend-artifact-manifest.json>)、[实际测试结果](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/validation-results.json>)。
- [历史证据脱敏摘要](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/historical-evidence-summary.json>)：仅引用历史结果，不并入本轮计数。
- [交付封存指纹](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-baseline-20260911/delivery-seal.json>)：本批文档、清单和整理工具的哈希；不是 Git 提交或生产签名。
- 整理工具：[快照准备/复核](</D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-release-baseline.mjs>)、[脱敏结果采集](</D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-release-evidence.ps1>)、[归属索引/封存](</D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-release-index.mjs>)。固定路径且拒绝覆盖已有输出，不能直接复跑来覆盖 r1；后续新批次需新目录/清单并重新确认输入。

新增的结果采集/索引工具及本文在源码快照之后产生，作为整理交付物独立封存，不声称它们已进入本次业务编译输入。历史验收文件与其他任务的未提交成果原位保留；临时 JAR、classes、原始报告只留在 target，不纳入后续平台源码提交。未将 private 或原始认证材料纳入清单附件。
