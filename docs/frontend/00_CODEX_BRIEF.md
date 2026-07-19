# 2G WMS 管理端前端 · Codex 开发指令书（V1）

> 你（Codex）的任务：为一套已完成的 Spring Boot WMS 后端（134 个 REST 端点）构建办公 Web 管理端第一版。
> 本目录四份文档就是你需要的全部输入：本文（总纲）→ 01 认证契约 → 02 页面规格 → 03 本地联调。
> 接口的机器可读契约在 `openapi.json`（本目录），也可从运行中的后端 `GET /v3/api-docs` 实时获取。

## 技术栈（已定稿，不要更换）

| 项 | 要求 |
|---|---|
| 框架 | React 18 + TypeScript（严格模式） |
| 组件库 | Ant Design 5.x，重型场景用 ProComponents（ProTable / ProForm / ProLayout） |
| 脚手架 | Ant Design Pro（Umi Max）或 Vite + 手工集成 ProComponents——二选一，优先前者 |
| 样式 | Antd 设计体系为主；布局微调可引入 Tailwind（须关闭 preflight 防止与 Antd 样式冲突） |
| 国际化 | **中英双语从第一天启用**：所有文案走 i18n key（默认中文 zh-CN，英文 en-US 骨架同步建立），Antd 组件经 ConfigProvider 切换 locale |
| 状态/请求 | 请求层由 openapi.json 生成 TypeScript 类型；数据获取用 ahooks useRequest 或 TanStack Query，二选一并全局统一 |
| 代码位置 | 本仓库 `frontend/` 子目录（新建） |

## 项目结构要求

```
frontend/
  src/
    api/          # 由 openapi.json 生成的类型与请求函数（生成物，勿手改）
    pages/        # 按 02_PAGE_SPECS.md 的模块划分
    components/   # 跨页复用组件
    locales/      # zh-CN.ts / en-US.ts
    access.ts     # 权限（见认证契约：角色 + 403 处理）
    app.ts(x)     # 全局配置：请求拦截器（JWT 注入 / 401 / 403 / mustChangePassword）
```

## 硬性规则

1. **认证与拦截器**：严格按 `01_AUTH_CONTRACT.md` 实现，特别是 `mustChangePassword=true` 的强制改密流程和 401/403 区分
2. **不要 mock 后端**：本地联调直连真实后端（见 `03_LOCAL_DEV.md`），dev 代理把 `/api` 转发到 `http://localhost:8080`
3. **分页约定**：后端 Spring Data 分页（`page` 从 0 起，返回 `content/totalElements`），ProTable 的 request 适配层统一封装一次，全站复用
4. **错误处理**：后端错误响应带 `errorKey`（如 `STOCK_INSUFFICIENT`）与 `message`；全局拦截器统一 toast，`errorKey` 进 i18n 映射表（缺 key 时回退显示 message）
5. **金额与数量**：金额 GBP 两位小数；数量为整数；时间显示用户本地时区（后端存 UTC）
6. **权限显隐**：菜单/按钮按登录响应的 `currentRole` + availableRoles 控制粗粒度显隐；细粒度以后端 403 为准（前端拦不住的以后端为权威）

## 交付顺序（按依赖递进，每步可独立验收）

1. **骨架**：脚手架初始化 + ProLayout 布局 + 双语框架 + 请求层生成 + 登录页/强制改密/角色切换（跑通认证闭环）
2. **稳定模块**：商品管理运营台（类别 → 产品/SPU → SKU）→ 库存三层查询（只读，接口最稳定）
3. **业务流**：销售订单（列表/详情/审批）→ 采购单三阶段 → 入库单四步
4. **渠道运营台**：店铺配置 / SKU 映射 / 待映射队列（一键放行交互）/ 人工复核列表
5. **工作台**：待办角标聚合（依赖前面所有模块的接口）

## 验收标准

- 全部页面中英文切换无硬编码文案残留
- 登录→改密→角色切换→审批一单→处理一条待映射 SKU 的完整操作链可走通
- `npm run build` 产物可直接置入 Spring Boot 静态目录运行（相对路径，无绝对 URL）
