# 本地联调指南（Codex 环境准备）

## 后端启动

前置（本机已具备）：JDK 17、PostgreSQL（本地 5432，库 wms_db）、环境变量 `DB_PASSWORD`/`TENANT_JWT_SECRET`/`PLATFORM_JWT_SECRET`/`BATCH_SALT`（已在用户环境变量中配置）。

```powershell
# 仓库根目录（2G/）
.\start-app.bat
# 或
mvn spring-boot:run
```

启动成功后：
- 健康检查：`GET http://localhost:8080/health/check`
- **Swagger UI**：http://localhost:8080/swagger-ui.html （在线调试全部接口，右上 Authorize 填 JWT）
- **OpenAPI JSON**：http://localhost:8080/v3/api-docs （生成 TS 类型的源；本目录 openapi.json 是它的快照）

## 测试账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | password123 | SUPER_ADMIN（全权限） |

需要更多角色账号（经理/仓库/销售）时，用 admin 通过 `GET /api/roles` 查询角色 ID，再通过 `POST /api/users` 创建。角色与权限目录仅对 `SUPER_ADMIN` 开放。

## 前端 dev 代理（免 CORS）

后端未开 CORS（生产同域部署不需要）。开发期用 dev server 代理：

```ts
// Umi (Ant Design Pro): config/proxy.ts
proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
// Vite: vite.config.ts
server: { proxy: { '/api': 'http://localhost:8080' } }
```

前端代码里的请求一律用相对路径 `/api/...`（生产同域直接可用，开发走代理）。

## 生产构建约定

`npm run build` 产物将来复制到后端 `src/main/resources/static/`（或打包脚本处理），由 Spring Boot 同域托管：
- 路由用 hash 模式，或 history 模式 + 后端兜底转发（V1 先用 hash 最省事）
- 所有资源相对路径，不要写死域名

## 注意事项

1. **不要开启 Shopify 定时同步**（`wms.integration.shopify.scheduler-enabled` 保持未设置）——联调渠道模块时用手动同步按钮
2. 数据库里已有真实店铺配置（id=1）和真实的待映射 SKU 数据，渠道运营台可直接用真数据联调
3. 后端接口问题（缺字段/报错/需要新端点）不要绕过或 mock——记录下来反馈给后端维护方
