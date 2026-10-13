# Ledger 上线第一步：正式入口与登录接入

工作范围：已确认的 Ledger 域名、账户查找、邮箱预填与登录、公共/租户/平台入口隔离。业务功能仍沿用 SalesBinder 对比后的验收范围。未执行正式服务器、DNS、TLS 或业务数据切换。

## 入口与兼容

- 公共入口：`https://ledger.fongvo.com/account-finder`。
- 租户入口：`https://<slug>.ledger.fongvo.com/login?email=...`；邮箱仅用于预填，仍必须验证密码。
- 登录后进入同一租户根路径，退出后回到该租户 `/login`。原 `#/...` 书签保留兼容。
- 平台入口保持独立，部署前填写 `WMS_PLATFORM_HOST`，本轮没有替用户确定公开平台域名。
- 使用已有 V1 标识、登录页面布局和产品名称；没有调整左侧导航样式或业务页面布局。
- Nginx 提供静态前端及 SPA 刷新回退，Java 后端负责 API。JAR 本身仍不包含前端。

## 账号与数据边界

邮箱以已验证身份及有效 Ledger 成员关系映射到原租户用户，密码校验、改密和重置仍使用原租户用户的密码记录。没有启用第二套密码校验，也没有把现有用户改造成新账号。

生产登录及已有令牌访问会核对身份/成员状态与订阅资格。旧用户没有身份关联时，保留租户内用户名登录；账户发现只能识别已验证且已关联的邮箱。正式数据切换前须核对旧账号关联情况，不能把未验证的资料邮箱直接认定为账号所有权。

V4.95 增加 Ledger 内一个身份只能有一个当前租户的数据库约束，当前状态包括 INVITED、ACTIVE、SUSPENDED，保留历史 REVOKED 记录。遇到旧数据冲突应停止并人工核对，迁移不会自动合并用户、删除成员或改写密码。

公共入口、平台入口及未知域名不能访问租户业务 API；令牌中的租户必须与 Host 一致。生产 ORM/RLS 查询没有明确租户上下文时不再回退到公司 1。

生产多租户启动要求：

- 非 SUPERUSER、非 BYPASSRLS 的应用数据库账号；迁移账号独立配置。
- `spring.jpa.open-in-view=false`，避免公共注册与租户初始化共用错误的 ORM 会话。
- 禁用 localhost 租户回退；有效且不同的公共/平台 Host 配置。
- 生产模式使用 SMTP、独立密钥及既有安全配置要求。

账户发现仅返回登记有效的 Ledger HTTPS 登录地址，邮箱经过 URL 编码；对不存在、停用、缺少资格及归属不唯一的邮箱使用相同不可用提示，接口禁止缓存并限流。Nginx 模板按客户端 IP 限制账户查询与密码登录，并覆盖 X-Real-IP。应用仅信任来自回环代理的该地址头，其他来源使用直接连接地址；后端必须绑定回环地址。部署为多进程或不同代理拓扑时须在服务器检查中核对配置。

## 部署交接

`deployment/ledger.nginx.conf.template` 是待按目标机器填写和检查的模板，不是已安装配置。仅替换列出的 LEDGER_* 变量，保留 Nginx 自身的 `$host`、`$uri` 等变量。静态目录指向本次构建的前端目录；后端只监听回环地址。保留已有 fongvo.com 官网配置。

生产配置使用：

- `SPRING_PROFILES_ACTIVE=prod`
- `WMS_BASE_DOMAIN=ledger.fongvo.com`
- `WMS_APP_HOST=ledger.fongvo.com`
- `WMS_PLATFORM_HOST=<最终选定的平台域名>`
- `SERVER_ADDRESS=127.0.0.1`，Nginx 转发到同机 8080
- 按现有部署文档注入数据库、JWT、SMTP、加密密钥和导出目录配置，禁止使用验收密钥。

模板需要 `LEDGER_PUBLIC_HOST`、`LEDGER_BASE_DOMAIN`、`LEDGER_PLATFORM_HOST`、`LEDGER_FRONTEND_ROOT`、`LEDGER_TLS_FULLCHAIN`、`LEDGER_TLS_KEY`。TLS 须实际覆盖公共、租户和平台域名。平台域名若不在一层通配符覆盖内，需要另行匹配证书。

先在恢复副本运行 `deployment/ledger-preflight.sql`；有重复成员、缺少订阅、失效试用或缺少新域名登记的租户必须处理后才能切换。旧域名不会被迁移自动重写，旧账号与真实订阅也不会被测试数据覆盖。

## 验证记录

2026-10-13：第一步代码与本机候选验收通过。独立验证库固定为本机 55519 的 `ledger_launch_test_20261010`，从既有验收库只读备份恢复；未连接日常 5432 或正式服务器数据库。

| 检查 | 结果与证据 |
| --- | --- |
| 后端编译、打包与定向回归 | 72 项通过，0 失败、0 跳过；`target/ledger-launch-final-verification.log` |
| 真实数据库集成 | 上述测试中包含 9 项：公共注册、账户发现、邮箱/用户名登录、改密后旧令牌失效、成员撤销/身份停用/订阅失效、跨租户与跨入口拒绝、一次性交接、RLS 无默认公司、单租户约束及平台域名保留；`target/ledger-launch-final-build/surefire-reports/` |
| 前端 | TypeScript 校验和生产构建通过；13 项路由/重定向/交接码测试通过；`target/ledger-launch-front-tests.log`、`target/ledger-launch-vite.log` |
| 最终 JAR 真实 HTTP | 28 项通过，覆盖三类 Host、受控登录地址、登录与刷新、8 个业务读取入口、跨租户及伪造转发 Host 拒绝、静态页面回退；`target/ledger-launch/http-verification.json` |
| 浏览器 | 账户不可用提示、邮箱参数登录、工作台、库存页刷新、退出回同租户 /login、旧 #/login 书签转到新地址均已操作；`target/ledger-launch/login-verified.jpg` |

最终后端产物：`target/ledger-launch-final-build/wms-system-0.0.1-SNAPSHOT.jar`；前端产物：`frontend/dist-launch/`。本机预览的 55174 是公共入口，55175 是专用测试租户，55177 是平台入口；本机代理使用固定 Host 模拟域名，未将生产域名改指向本机。最终包及源码哈希记录见 `target/ledger-launch/release-manifest.json`。

实际检查中修复了公共注册请求沿用错误 ORM 租户会话的问题，并保留启动检查；验证不以关闭隔离或使用超级用户运行应用作为替代。

范围说明：没有在真实公网域名上执行 HTTPS 跳转、没有验证服务器上的 Nginx 配置或 SMTP 发信，也没有实施统一身份中心。数据库资格检查和 HTTP 阻断已在隔离副本执行；真实业务数据中的旧域名、成员关联和订阅仍须在服务器接入后核对。退出登录保持既有本机清除会话行为；撤销全部设备及改密的令牌失效另有测试，不能混称每次本机退出都会撤销所有设备。

下一步：取得可用 SSH 接入信息与最终平台域名后，只读检查已有服务器，准备真实数据备份/恢复、迁移演练、DNS/TLS、SMTP 和正式切换方案。未验收前不直接把本机候选称为已上线。

统一身份中心 accounts.fongvo.com、跨产品 SSO/IdP 及跨产品开通流程尚未实施。本轮完成的是 Ledger 本地生产入口接入；未对比的业务板块继续留待后续升级。
