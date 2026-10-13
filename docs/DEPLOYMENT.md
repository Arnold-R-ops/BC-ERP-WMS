# 2G WMS 生产部署清单

当前 Ledger 候选采用 Nginx 同域提供 React 静态前端并转发 Java API，PostgreSQL 保存业务数据；JAR 不包含前端。所有命令中的域名、路径和密码均为占位符，部署时按实际环境替换。

## 2026-10-13 第一步更新

正式域名、账户发现、邮箱登录和租户/平台隔离代码已补齐，详见 [Ledger 入口接入与验收记录](LEDGER_ENTRY_LAUNCH_STEP1.md)。下方 2026-10-07 表格保留当时的检查事实；“启用锁”和“缺少 account-finder”已由本次接入替代。生产启用仍要求安全数据库角色、关闭 localhost 回退及 Open EntityManager in View，并须在服务器阶段完成配置和真实数据检查。

本次没有执行目标服务器部署。Nginx 模板和 V4.95 迁移须在目标备份恢复副本检查后再使用；原验收副本及日常工作环境未切换。

## 2026-10-07 上线执行批次

用户已要求停止扩展竞品比较，将未比较的板块留待后续升级；本轮开始上线工作，目标为**已有服务器或域名**。这是上线工作的授权，不是重新批准下文历史 P2 遗留清单中的全部功能开发。当前不能把本机业务验收结论当作生产环境已就绪。

### 本轮范围与固定输入

- 已验收业务基线：分支 `codex/salesbinder-acceptance-20261004`，提交 `1df25c0ef34678b0ffc172c817151d1ec6228cdd`，包含折叠导航图标修复；后端产物对应 `dbbd2ff`。
- 业务证据：`docs/SALESBINDER_ACCEPTANCE_CANDIDATE_2026-10-04.md` 与 `target/acceptance-20261004/release-manifest.json`；本批本地文件核对结果为 `target/production-preflight-20261007/preflight.json`。
- 未深入比较的 CRM、税费/多币种、成本利润/对账、佣金、业务邮件通知、外部集成、活动/删除记录、条码标签/移动体验及账号套餐增强，留待后续升级。上线必需的域名接入、生产邮件配置、权限隔离、备份和运行配置按上线工作处理。
- 在目标服务器、数据来源及接入方式明确前，不把本机 55519 验收副本或测试账号带入生产，不替换原 5173/8080 工作环境。

### 2026-10-07 已查明的上线前置项

| 项目 | 已查明事实 | 下一步 |
| --- | --- | --- |
| 目标服务器 | 旧交接记载阿里云伦敦服务器、FONGVO 域名；这是历史记录。本轮已请求目标地址、已有 SSH/部署配置及业务数据情况 | 确认目标后只读核对系统、磁盘/内存、Nginx、TLS、数据库和现有服务 |
| 部署产物 | 已验收 JAR 不含 `BOOT-INF/classes/static/` 文件；当前前端由本机 Vite preview 提供 | 根据目标现有架构选择 Nginx 同域托管静态文件，或重新生成含前端的 JAR；验证最终文件及入口，不将 Vite preview 当作正式服务 |
| 正式多租户启用 | `TenancyProperties.isSafeToEnable()` 仍返回 `!enabled`；设置 `WMS_TENANCY_ENABLED=true` 会被校验拒绝，现有测试也明确验证此锁 | 完成目标域名路由、登录与隔离验证后再调整启用条件；不得删除锁后直接公网运行，也不得用关闭租户隔离代替验证 |
| Ledger 域名和登录 | 当前 Host 校验要求公共入口是基础域名的子域名，与既定 `ledger.fongvo.com` 公共入口不一致；前端仍为 HashRouter 的用户名登录，无 `account-finder` 实现 | 对照既有 FONGVO Ledger 交接核对本次上线入口，完成必要接入与回归，不引入未来产品功能 |
| 数据库迁移 | 已有原业务备份副本 V4.73→V4.94 演练；尚未核对目标服务器数据库 | 只读取得目标历史及校验值，先备份并恢复到独立环境演练。V4.73/V4.76 差异必须按该目标库核对，不一律 repair、baseline 或 out-of-order |
| 生产配置及恢复 | 本机验收使用独立数据库及开发邮件模式，不能复用为生产配置 | 核实生产密钥管理、低权限运行账号、独立迁移身份、SMTP、备份存放/恢复、TLS及回退步骤 |

### 执行顺序

1. 核对目标与服务器现状，记录现有网站、服务、数据库版本和数据来源。
2. 固定本轮发布输入，完成生产域名/登录/隔离接入与对应回归。
3. 落实生产配置、密钥与数据库身份；准备最终部署产物。
4. 完成目标数据备份、独立恢复和迁移演练，明确应用与数据库配套回退。
5. 在受控入口验证最终包的 HTTPS、登录、租户/平台路由、权限、必要业务页面及输出；不对真实业务数据执行无标记的试验写入。
6. 满足上述条件后执行正式服务切换，核对服务恢复、日志、备份及正式入口，记录实际版本与结果。

本批尚未连接或修改目标服务器，未执行生产迁移、DNS/证书修改、部署或服务切换。下面保留的 P2 文档是历史操作参考，其中默认测试数据库、直接启动迁移与旧回滚说明不能代替本轮针对实际目标的执行方案。尤其不要直接运行未指定隔离库的全量 Maven 测试。

## 0. P2 发布门槛

2026-07-27 固化前置核对结论为**不通过**，本清单当前仅供准备，不代表 P2 已具备发布条件。正式构建、推送和标签前必须同时满足：

1. 业务负责人明确接受物理手机断网恢复、盲盘重试和采购真机未完成项为发布遗留，或完成对应实机复验。
2. 实时 OpenAPI、`docs/frontend/openapi.json` 与 `frontend/src/api/schema.d.ts` 完全一致。
3. 独立 API `.http` 验收集具备可复现执行方式，并在执行前记录测试数据、库存影响和保留/清理方案。
4. Java、独立 API、前端测试、类型检查和生产构建全部通过。
5. `.claude/settings.local.json` 等本机文件不进入提交，工作区修改已按模块核对。
6. 推送远程和创建 P2 标签已获得用户明确批准。

> **2026-08-11 本机凭证门禁更新：通过。** 历史数据库密码已在数据库端轮换并验证失效，应用已切换到非超级用户 `wms_app`。该结论只覆盖当前本机环境；其他部署环境仍必须分别执行凭证轮换和最小权限验证。详见 `docs/P2_DATABASE_CREDENTIAL_ROTATION_2026-08-11.md`。

## 1. 服务器与网络

- Java：Amazon Corretto 17 或其他兼容的 JDK 17。
- 数据库：PostgreSQL 15 或更高版本。
- 反向代理：Nginx、Caddy 或等价产品，必须启用 HTTPS。
- 应用端口：Spring Boot 默认监听 `8080`，仅允许本机或内网反向代理访问。
- 公网入口：只开放 HTTPS；不要直接暴露 PostgreSQL、`8080`、Swagger 或 OpenAPI 文档。
- 服务器时区可按运维习惯设置，应用 JVM 会统一使用 UTC；前端按用户区域展示时间。

## 2. 必需环境变量

| 变量 | 必需 | 说明 |
|---|---:|---|
| `DB_PASSWORD` | 是 | PostgreSQL 应用账号密码，无默认值 |
| `DB_USERNAME` | 建议 | 生产应使用低权限账号 `wms_app`，未设置时默认为 `postgres` |
| `DB_URL` | 建议 | 例如 `jdbc:postgresql://127.0.0.1:5432/wms_db?serverTimezone=UTC` |
| `TENANT_JWT_SECRET` | 是 | Base64 编码、至少 256 位的公司 JWT 签名密钥 |
| `PLATFORM_JWT_SECRET` | 是 | Base64 编码、至少 256 位的平台 JWT 签名密钥；必须与公司密钥不同 |
| `BATCH_SALT` | 是 | 批次短码盐；每个环境只设置一次，之后不得随意更换 |
| `ADMIN_INITIAL_PASSWORD` | 否 | 仅在系统首次创建 `admin` 账号时使用 |
| `SPRING_PROFILES_ACTIVE` | 是 | 生产固定设置为 `prod`，关闭 SQL 明文调试日志和 OpenAPI 页面 |
| `WMS_SIGNUP_TOKEN_PEPPER` | 是 | 注册验证码和一次性交接码的服务器 Pepper，随机生成并长期保管 |
| `SMTP_HOST` / `SMTP_USERNAME` / `SMTP_PASSWORD` | 是 | 生产邮箱验证码发送账号；生产模式缺失时启动或发送失败 |
| `WMS_MAIL_FROM` | 建议 | 验证邮件发件地址，默认 `no-reply@bcwms.com` |
| `WMS_PLATFORM_EXPORT_DIR` | 是 | 平台导出文件的专用绝对目录；只允许应用服务账号访问，不得置于静态网站目录 |

不要把真实密钥写入 Git、批处理文件、镜像或部署文档。Windows 可使用系统环境变量；Linux 建议使用权限为 `600` 的服务环境文件或云端密钥服务。

本地默认使用内存开发邮件箱，不会在日志中打印验证码。生产 profile 强制使用 SMTP，并拒绝默认
注册 Pepper。验证码为 6 位数字、10 分钟有效、最多尝试 5 次；交接码为 60 秒一次性 opaque code，
不得写入访问日志或查询参数。

平台导出文件保存 24 小时后由后台任务删除。导出目录可能包含公司业务数据，必须使用磁盘加密、
最小文件权限，并排除在 Git、通用静态文件服务、普通应用备份和公司用户可访问目录之外。

数据库低权限账号初始化脚本位于 `docs/security/setup-db-user.sql`。部署前应先按 `docs/security/SECURITY_SETUP.md` 完成账号和密钥配置。

生产配置会强制检查数据库运行账号。`wms_app` 必须同时为 `NOSUPERUSER` 和
`NOBYPASSRLS`，否则应用拒绝启动。V4.52 会对租户业务表启用并强制执行 PostgreSQL RLS；
不要为了绕过策略而使用 `postgres` 启动应用。平台读取和导出也必须通过应用服务显式进入目标
公司的事务上下文，不能使用数据库特权账号直读租户数据。

## 3. 构建管理端

在仓库根目录执行：

```powershell
Set-Location frontend
& 'D:\JAVA\NODE\npx.cmd' pnpm install --frozen-lockfile
& 'D:\JAVA\NODE\npx.cmd' pnpm test
& 'D:\JAVA\NODE\npx.cmd' pnpm build
```

构建成功后，`frontend/dist/` 是静态管理端。将其中的全部内容复制到后端的 `src/main/resources/static/`，不要再包一层 `dist` 目录：

```powershell
Copy-Item -Path 'frontend\dist\*' -Destination 'src\main\resources\static' -Recurse -Force
```

前端使用 Hash Router，因此静态托管无需为每条前端路由配置服务端回退规则。浏览器访问同一域名，API 继续使用 `/api/...`，不需要开放跨域访问。

## 4. 构建与启动后端

先完成前端静态文件复制，再构建可执行 JAR：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk17.0.17_10'
& 'D:\ITsoftware\Toolbox\JetBrains社区\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd' clean test package
```

生产启动前确认环境变量已经注入，然后运行：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
java -jar target\wms-system-0.0.1-SNAPSHOT.jar
```

应用启动时 Flyway 自动校验并执行尚未应用的迁移。任何迁移失败都应先停止发布、备份数据库并定位原因，不得手工修改 Flyway 历史表来强行跳过。

发布后检查：

1. HTTPS 登录页可以打开。
2. 使用非默认管理员密码登录成功。
3. 生产环境的 `/v3/api-docs` 和 `/swagger-ui` 不可访问。
4. 日志中没有 SQL 参数、Token、数据库密码或渠道密钥。
5. 用只读查询确认 Flyway 迁移版本与代码一致。
6. 确认日志显示数据库账号兼容 RLS，并抽查租户业务表的
   `relrowsecurity=true`、`relforcerowsecurity=true`。

## 5. 反向代理

以下是 Nginx 最小示例，证书路径按实际环境修改：

```nginx
server {
    listen 443 ssl http2;
    server_name wms.example.com;

    ssl_certificate     /etc/letsencrypt/live/wms.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/wms.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 80;
    server_name wms.example.com;
    return 301 https://$host$request_uri;
}
```

## 6. Shopify 只读上线步骤

当前系统对 Shopify 的边界是“读取订单、接收 Webhook、对账和受控补单”，不会回写 Shopify 商品、订单或库存。因此应用只申请实际使用的读取权限，不申请任何 `write_*` scope。

1. 在 Shopify Dev Dashboard 配置应用凭据和必要的只读权限。
2. 将订单 Webhook 回调地址设置为 `https://wms.example.com/api/webhooks/shopify`。
3. 发送测试 Webhook，确认 HMAC 签名校验成功；签名失败必须返回拒绝响应。
4. 先保持轮询和定时对账关闭，通过管理端“手动同步”和“订单对账”验证真实店只读链路。
5. 只有获得业务负责人明确批准后，才设置 `wms.integration.shopify.scheduler-enabled=true`。
6. Webhook 稳定后，轮询只作为漏单兜底，建议设置 `wms.integration.shopify.poll-cron=0 0/30 * * * ?`，即每 30 分钟一次。
7. `wms.integration.shopify.reconcile-enabled` 默认保持关闭；确需自动对账时再按店铺流量和运维窗口开启。

首次上线和本轮验收期间，不得开启上述两个调度开关。系统也不需要 Shopify 的库存写权限。

## 7. 数据库备份纪律

### 每日备份

- 每天至少执行一次 `pg_dump` 自定义格式备份。
- 备份文件必须加密并复制到与数据库服务器不同的存储位置。
- 建议保留 7 份日备、4 份周备和 12 份月备；实际周期按业务合规要求调整。
- 备份任务失败必须告警，不能只依赖“任务已启动”的日志。

示例：

```powershell
$env:PGPASSWORD = $env:DB_PASSWORD
& 'D:\ITsoftware\PostgreSQL\bin\pg_dump.exe' `
  --host 127.0.0.1 --port 5432 --username wms_app `
  --format custom --file 'D:\backup\wms_db_20260721.dump' wms_db
```

### 恢复演练

恢复演练必须使用独立数据库，绝不能覆盖生产库：

```powershell
$env:PGPASSWORD = $env:DB_PASSWORD
& 'D:\ITsoftware\PostgreSQL\bin\createdb.exe' `
  --host 127.0.0.1 --port 5432 --username postgres wms_restore_test
& 'D:\ITsoftware\PostgreSQL\bin\pg_restore.exe' `
  --host 127.0.0.1 --port 5432 --username postgres `
  --dbname wms_restore_test --clean --if-exists 'D:\backup\wms_db_20260721.dump'
```

恢复后至少核对：核心表数量、最近销售单、库存批次余额、预留余额、用户和权限关系、Flyway 版本。演练结束后由授权人员删除恢复测试库，并保存演练时间、耗时和检查结果。至少每季度完成一次恢复演练。

## 8. 回滚原则

- 应用发布失败：停止新版本，恢复上一版 JAR 和静态资源。
- 数据库迁移已成功后，不要直接回滚迁移文件；优先以前向修复迁移处理。
- 涉及不可逆数据变更时，发布前必须取得可验证备份，并预先演练恢复步骤。
- Shopify 调度异常：立即关闭 `scheduler-enabled` 和 `reconcile-enabled`，保留 Webhook 原始事件用于人工核对。
