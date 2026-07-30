# 2G WMS 生产部署清单

本文用于当前单体部署形态：React 管理端构建后由 Spring Boot 同域托管，PostgreSQL 保存业务数据，反向代理负责 HTTPS。所有命令中的域名、路径和密码均为占位符，部署时按实际环境替换。

## 0. P2 发布门槛

2026-07-27 固化前置核对结论为**不通过**，本清单当前仅供准备，不代表 P2 已具备发布条件。正式构建、推送和标签前必须同时满足：

1. 业务负责人明确接受物理手机断网恢复、盲盘重试和采购真机未完成项为发布遗留，或完成对应实机复验。
2. 实时 OpenAPI、`docs/frontend/openapi.json` 与 `frontend/src/api/schema.d.ts` 完全一致。
3. 独立 API `.http` 验收集具备可复现执行方式，并在执行前记录测试数据、库存影响和保留/清理方案。
4. Java、独立 API、前端测试、类型检查和生产构建全部通过。
5. `.claude/settings.local.json` 等本机文件不进入提交，工作区修改已按模块核对。
6. 推送远程和创建 P2 标签已获得用户明确批准。

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
| `JWT_SECRET` | 是 | Base64 编码、至少 256 位的 JWT 签名密钥 |
| `BATCH_SALT` | 是 | 批次短码盐；每个环境只设置一次，之后不得随意更换 |
| `ADMIN_INITIAL_PASSWORD` | 否 | 仅在系统首次创建 `admin` 账号时使用 |
| `SPRING_PROFILES_ACTIVE` | 是 | 生产固定设置为 `prod`，关闭 SQL 明文调试日志和 OpenAPI 页面 |

不要把真实密钥写入 Git、批处理文件、镜像或部署文档。Windows 可使用系统环境变量；Linux 建议使用权限为 `600` 的服务环境文件或云端密钥服务。

数据库低权限账号初始化脚本位于 `docs/security/setup-db-user.sql`。部署前应先按 `docs/security/SECURITY_SETUP.md` 完成账号和密钥配置。

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
