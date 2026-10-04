# 平台会话后端收尾：隔离交付记录

> **2026-09-10 23:38 最终补充（覆盖下方历史环境进度）：**同浏览器退出规则的核心人工会话用例已完成；用户随后确认剩余邀请衔接改为合成自动补测。后端 69/69、前端专项 58/58、故障选择器 7/7 通过，平台范围类型检查通过；已有范围外地址长度测试导致当前全工作区类型检查 TS2769，未代改。邀请完整人工激活仍未覆盖，不冒充通过。本次没有再改产品代码/新增或执行迁移。专用 15173/18091/55439 已正常收尾停止，原 5432/8080/5173 未动，数据与证据保留。实际结果和未覆盖清单见 `PLATFORM_SESSION_BROWSER_ACCEPTANCE_REVIEW_2026-09-10.md`；没有提交合并或生产开放。

> 2026-09-10 追加环境进度：已获确认并仅将合成浏览器验收库 `55439/p3_m1_platform_browser` 升至 V4_90，切换专用 18091/15173 服务，新版健康及无认证准备检查通过；日常服务与真实数据未动。下一步是人工重新登录和同浏览器双标签退出验收，不能视为浏览器验收已完成或生产授权。详情见 `PLATFORM_BROWSER_LOGOUT_SCOPE_CHANGE_2026-09-10.md`。

> 2026-09-10 09:52 浏览器验收中的调整：普通退出应撤销同一浏览器配置下同一平台账号的相关会话，并让其所有标签退出；其他浏览器/设备不受影响。“退出所有设备”仍沿用账号全局撤销与 MFA。新代码及 V4_90 已编写，获得继续授权后仅在新合成库 `55439/p3_m1_platform_browser_scope` 迁移并验证，后端 65 项（38 单元 + 27 PostgreSQL）全部通过；此前前端 246 项通过。当前浏览器库与运行服务仍为旧版本，尚未切换或完成新规则真实浏览器验收。详见 `PLATFORM_BROWSER_LOGOUT_SCOPE_CHANGE_2026-09-10.md`。本记录下文仍描述此前交付的“当前独立会话退出”行为，不能视为新规则浏览器验收已完成。租户 ERP 规则不变。

## 范围与授权

本批目标是独立平台基础上线能力中的会话后端收尾，不是完成第三阶段，也不启动第四、第五阶段。

- 沿用 Phase 1/2 与 P3-M1 A/B 的管理员目录、邀请、启停、MFA、授权、审计、安全暂停及双人恢复；不重复开发。
- 授权仅限代码、测试、增量迁移文件、专用隔离数据库验证和交付记录。
- 没有提交、推送、分支合并、真实数据库迁移、真实账号/租户操作、现有服务重启、生产配置修改或生产开放。
- 工作区起点：`2-1G@9c12f6f`，已有 P3-M1 未提交改动保留。实施前重新核对 ERP `codex/tenant-erp-a1@ea4697e` 及未提交改动；ERP 迁移为 V4_73–84，本工作区 P3 为 V4_85–88，因此本批使用 **V4_89**。正式发布前仍须再次核对同期迁移编号。
- JWT 与认证过滤器只增加平台分支逻辑。租户 ERP 登录、刷新、角色切换、登录交接、7 天绝对窗口、现有前端空闲规则及后台租户状态检查不变。

## 本批实际行为

1. 完成平台 MFA（含初次绑定、恢复码登录、邀请激活的完成入口）后，在同一事务内登记新会话，再签发带 `platform_session_id` 的 JWT。密码验证阶段不创建会话。会话登记失败时挑战消费及登录成功审计一起回滚。
2. 所有携带平台 JWT 的请求，在进入接口前检查数据库会话、账号启用及安全版本、JWT 身份和实时角色。JWT 尚未到期不代表空闲期限仍有效。
3. 连续空闲 **30 分钟**、登录最长 **12 小时**；等于期限即无效。平台有效期配置不得超过 12 小时，数据库也约束绝对期限。没有平台刷新接口，不续绝对期限。
4. GET `/api/platform/auth/me` 返回 `expiresAt`、`idleExpiresAt`、`serverNow`；刷新、页面挂载、聚焦和后台轮询只检查，不延长空闲期限。
5. POST `/api/platform/auth/session/activity` 只使用服务端时间，不接受客户端时间或自定义续期长度。先锁账号，再锁会话，取得锁后读取 PostgreSQL `clock_timestamp()`，检查未过期且未撤销，之后才更新。没有先续期再检查、过期会话重建或旧 JWT 自动登记的兼容路径。
6. 点击、键盘输入、滚轮、触摸操作属于活动；单纯鼠标移动、程序触发的滚动通知、后台请求和页面保持打开不算。滚动意图以真实滚轮/触摸/键盘交互识别。前端检查事件可信性与页面可见性，常规最多约每 15 秒合并上报一次，接近期限时及时上报。不记录点击位置、按键内容或操作正文。后端空闲起点是成功接受活动的服务端时间，合并/网络传输会与最后一次物理操作产生时间差，并非证明真实人员在场。
7. 客户端不乐观延长期限；只有后端成功响应才能更新本地调度窗口。页面重载必须完成后端检查才展示受保护页面。不同登录各自计时；复制标签页若共用同一 JWT，则属于同一后端会话。
8. POST `/api/platform/auth/logout` 撤销当前会话并在同一事务记 `SESSIONS_REVOKED / CURRENT_PLATFORM_SESSION` 审计，成功返回 204；其他独立登录不受影响。原有“退出所有设备”的 MFA 及安全版本规则继续沿用。
9. 普通退出先捕获当前令牌，清除浏览器登录信息，再提交撤销；等待中、确认撤销、已经失效、网络/服务端结果未确认分别提示。失败不声称服务端撤销成功；旧令牌可能在后端空闲/绝对期限或账号撤销生效前仍可使用。必要时重新登录后使用“退出所有设备”。
10. 旧请求晚到的 401 不会清除已经建立的新登录。MFA 验证失败的特定 401 仍仅提示 MFA 错误，不误清除当前有效登录。

### 明确边界

- 活动上报只是空闲管理，不是令牌防盗。持有被盗 JWT 的人仍可能主动模拟活动；12 小时硬期限、账号启停/安全版本、实时角色及既有权限检查继续约束访问。
- 后端检查是请求准入，不是对已准入请求中途强制取消。已经成功提交的审批、导出或后台任务不会因关闭页面、普通退出、空闲到期而被撤销；后台继续按原有账号、授权、租户状态及暂停原因规则执行。新的查询和文件下载仍须有效登录。
- 数据库不可用时不放行平台请求。认证阶段会话数据库异常返回 503，而不是误报已撤销；客户端本地清除登录不等于服务端状态改变。
- 上线本批后，未登记后端会话的旧平台 JWT 会失效，平台管理员需重新登录。不是租户 ERP 全体重新登录。实际上线仍未授权。

## 数据结构、迁移与清理

新增 `platform_sessions`：随机 UUID 会话编号、平台账号 ID、安全版本快照、开始时间、最后活动时间、空闲期限、绝对期限、撤销时间。没有设备目录、设备指纹、JWT 明文或租户业务数据。

迁移文件：`src/main/resources/db/migration/V4_89__Platform_Backend_Sessions.sql`。只新增表、外键、约束、索引和注释，不修改 V4_85–88，不转换租户数据，不删除审计，不批量修改账号安全版本。

清理规则：

- 以已记录的空闲到期、绝对到期、当前会话撤销时间的最早值为终止时间；终止满 **7 天**后才具备清理资格。
- 每小时执行一批、每批最多 1,000 行，使用 `FOR UPDATE SKIP LOCKED`；跳过被占用行，下轮继续。不保证积压时恰在第七天删除。
- 账号停用、角色变化、退出所有设备等仍以账号安全版本立即使旧会话失效，不逐行改写历史会话；这些行不能继续报告活动，按原空闲/绝对期限进入上述清理资格（通常最迟再等原来的 30 分钟空闲窗口）。
- 只删除 `platform_sessions`。不连带删除平台审计、MFA 历史、审批、导出任务或租户数据。清理失败记录错误日志，下一小时重试；积压属于运维需关注事项，不影响过期/撤销令牌的拒绝。
- 缺失会话行一律拒绝。清理、应用重启或刷新页面不会重建旧会话。

## 文件清单

后端新增：

- `platform/service/PlatformSessionService.java`、`PlatformSessionException.java`、`PlatformSessionCleanupScheduler.java`。
- `platform/controller/PlatformSessionController.java`、`PlatformSessionExceptionHandler.java`。
- V4_89 迁移文件。

后端增量修改（Java 路径均位于 `src/main/java/com/wms/system/`）：

- `security/JwtUtil.java`、`PlatformJwtClaims.java`、`JwtProperties.java`、`JwtAuthenticationFilter.java`；`SecurityConfig.java` 仅补充认证说明注释。
- `platform/service/PlatformMfaService.java`、`platform/repository/PlatformUserRepository.java`、`platform/dto/PlatformSessionResponse.java`。
- MFA 写入使用账号 `FOR NO KEY UPDATE` 锁，与账号撤销串行，允许审计外键读取；不改变密码/MFA/恢复码业务规则。

前端：`frontend/src/platform/PlatformAuthProvider.tsx`、`PlatformApp.tsx`、`api.ts`；新增 `sessionActivity.ts`。租户 `frontend/src/auth/` 无本批业务修改。

测试：新增 `PlatformSessionServiceTest`、`PlatformSessionPostgresTest`、`sessionActivity.test.ts`、`PlatformAuthProvider.test.tsx`；更新 `PlatformMfaServiceTest`、`JwtAuthenticationFilterTest` 及 P3 `TenantSecuritySpringPostgresTest` 的真实平台令牌夹具与退出后任务断言。

## 隔离验证约束

- 复用并显式启动此前已停止的专用 PostgreSQL 数据目录 `target/p3-m1-pg-20260909`，只监听 `127.0.0.1:55439`。未重启任何真实/既有业务服务。
- 新测试库 `p3_m1_platform_session` 从此前隔离基础库复制；新组合库 `p3_m1_platform_session_erp` 从此前隔离 ERP 库复制。两库在本批执行 V4_89；不变更以前交付记录对应的基础库。
- 使用 `p3_m1_app`，已核验 `NOSUPERUSER / NOBYPASSRLS`。测试地址必须符合固定 55439 / `p3_m1_` 库名前缀白名单，否则不启动测试。
- 使用 `p3-m1-smoke.properties`，禁用全部调度，模拟邮件与第三方 API；MockMvc 不监听实际 HTTP 端口。禁止改用普通 `application-test.yml` 或不筛选的 Maven 测试命令。
- 当前构建输出 `target/platform-session-build`；ERP 组合输出 `target/platform-session-erp-build`；不写现有 `target/classes`。
- ERP 组合只在已有 `target/p3-m1-erp-compat` 专用副本叠加本批平台文件；保留此前组合中 ERP 分支及已有 Java 修复，不修改原 ERP 工作区，不代表正式合并，也不代表 ERP 全量业务 UAT。
- 前端构建输出 `target/platform-session-frontend-dist`；前端机器报告 `target/platform-session-frontend-tests.json`。
- 最终验证后再次通过数据库查询核验确切数据目录及 55439 端口，再停止本批启动的专用 PostgreSQL 实例；`postmaster.pid` 已消失。测试库文件、报告和兼容副本保留，不涉及真实数据库或业务服务。

## 实际结果

| 验证 | 实际结果 |
|---|---|
| 当前工作区最终后端回归 | **260 项通过，0 失败/错误/跳过**；包括此前 236 项及本批新增 24 项 |
| 本批专用会话测试 | 精确时间边界纯单元 4 项、真实 PostgreSQL/Spring/MockMvc 18 项、认证过滤器新增 2 项；已包含在 260 项内，不重复加总 |
| ERP 组合最终回归 | **142 项通过，0 失败/错误/跳过**；879 个主源码编译通过；包含 ERP + 既有 Java 修复 + P3-M1 + 本批会话改动，不是 ERP 全量业务 UAT |
| 迁移 | 两个新隔离库 V4_89 均成功；ERP 组合保留 B4_71、V4_72–89 顺序；两个 Spring 上下文 ORM validate 通过 |
| 前端全量 | **62 个文件、235 项通过**；机器报告 `target/platform-session-frontend-tests.json` |
| 前端静态/构建 | `tsc --noEmit` 与独立目录 Vite 构建通过 |

后端 XML 证据分别位于 `target/platform-session-build/surefire-reports`、`target/platform-session-erp-build/surefire-reports`。较早的 36、252 项为中途验证，不累加为新增测试。预期注入的异常（例如 MFA 会话创建失败、Webhook 失败）会产生错误日志，但相关断言均通过。前端遗留 Ant Design 弃用提示等不作为生产浏览器验收结果。

关键断言：30 分钟/12 小时相等边界拒绝、有效期检查先于活动更新、锁等待后重新取数据库时间、活动与退出并发后不能复活、当前退出与审计原子回滚、MFA 成功记录与会话创建原子回滚、旧 JWT 不能直接调用业务/下载/活动接口、分开登录分别计时、七天清理不删除审计、失败退出不误报成功、旧响应不清除新登录。P3 真实用例同时验证发起人退出不撤销已提交恢复申请、另一人仍可审批，以及已提交导出继续执行但旧会话不能下载。

复验必须先另行明确启用专用隔离实例；以下示例不能改成真实库，也不能省略测试筛选：

```powershell
& 'target/codex-tools/apache-maven-3.9.9/bin/mvn.cmd' -B -ntp `
  '-Dwms.build.directory=D:/ERP_WMS/BC warehouse/2G/target/platform-session-build' `
  '-Dtest=PlatformSessionServiceTest,PlatformSessionPostgresTest,PlatformMfaServiceTest,JwtUtilTest,JwtAuthenticationFilterTest,TenantSessionSecurityControllerTest,TenantSecurity*Test' `
  '-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_session' test
```

上例为核心复验子集。260 项完整名单可由本批 XML 报告的 testsuite.name 读取后拼为 `-Dtest`；禁止直接使用普通 test profile 跑全部测试。

## 恢复核对边界（必须保留为待确认）

P3-M1 当前实现是 **系统生成缺单报告＋人工核验订单修改、取消和退款**，不是完整自动恢复核对。实施和隔离测试证明当前机制可运行，不代表用户已经接受人工核验作为最终方案。最终业务边界仍待单独确认；本批没有扩展自动核对，也没有把它作为平台会话收尾的前置条件。

## 剩余风险与下一步人工验收

以下列入后续，不因本批隔离通过而默认获准执行：

1. 在另外批准的浏览器验收环境，使用测试平台管理员完成 MFA 登录；实际放置 30 分钟不操作，刷新、切换菜单、直接重用旧 JWT 均应拒绝。后台导出轮询不能延长空闲期限。
2. 正常点击/输入/滚动能维持有效空闲窗口；接近 12 小时时再活跃也不能延长。验证页面休眠、网络慢/断开、电脑时间偏差、多标签页同会话与两个独立登录。
3. 普通退出确认 204，旧 JWT 失效、另一独立登录仍有效；模拟退出断网时，确认显示“服务端撤销未确认”，不能显示全部退出成功。
4. 回归管理员停用/再启用、角色变更、退出所有设备、MFA 失败/重试、恢复码和邀请激活。已提交的审批/导出任务不被会话清理取消；下载仍检查当前会话、授权、租户状态和暂停原因。
5. 目标 PostgreSQL 版本、备份恢复和生产迁移演练、统一迁移编号/发布版本另行确认。本机为 PostgreSQL **18.6**，Flyway **9.22.3**提示高于已测试版本；本机成功不等于生产目标版本验收。
6. 尚未做生产负载、真实网络浏览器、真实邮件/告警送达测试。每个平台请求新增短数据库会话检查，需上线前关注请求延迟、锁等待、连接池和清理积压。
7. 正式管理员邮箱、MFA/恢复码保管、应急撤销流程、域名/HTTPS、生产密钥、邮件和安全配置，统一列入上线前队列；服务器与数据库备份沿用统一运维方案。

停止点：代码、迁移文件、隔离测试及记录可交付。所有延期功能保持关闭；不开放生产。
