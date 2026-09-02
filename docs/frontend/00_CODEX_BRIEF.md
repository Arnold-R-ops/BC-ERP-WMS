# 2G WMS 租户 ERP 管理端 · 前端维护与验收总纲

> 历史说明：本文件最初用于 V1 前端建设，现已更新为当前 P0 租户 ERP 的维护入口，不再使用“134 个端点、采购三阶段、旧四步入库”等历史假设。
> 当前业务真相依次取自：已确认的 `TENANT_ERP_P0_*_IMPLEMENTATION_REPORT`、本目录 `openapi.json`、生成的 `frontend/src/api/schema.d.ts` 和当前代码。`02_PAGE_SPECS.md` 只解释页面意图，不能覆盖实时接口契约。

## 技术栈（已定稿，不要更换）

| 项 | 要求 |
|---|---|
| 框架 | React 18 + TypeScript（严格模式） |
| 组件库 | Ant Design 5.x，重型场景用 ProComponents（ProTable / ProForm / ProLayout） |
| 脚手架 | 当前已采用 Vite，不再重新选择或切换脚手架 |
| 样式 | Antd 设计体系为主；布局微调可引入 Tailwind（须关闭 preflight 防止与 Antd 样式冲突） |
| 国际化 | **中英双语从第一天启用**：所有文案走 i18n key（默认中文 zh-CN，英文 en-US 骨架同步建立），Antd 组件经 ConfigProvider 切换 locale |
| 状态/请求 | `schema.d.ts` 由 openapi.json 生成；请求封装和业务 API 适配层按现有结构维护，页面状态沿用当前 React/TanStack Query 方案 |
| 代码位置 | 本仓库既有 `frontend/` 子目录 |

## 项目结构要求

```
frontend/
  src/
    api/          # schema.d.ts 是生成物；其余请求封装与业务适配文件按现有测试维护
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
6. **权限显隐**：菜单和按钮按当前角色的服务端权限码控制；`availableRoles` 只用于可切换角色列表，不能替代权限。后端方法级权限、租户和仓库范围始终是最终权威
7. **业务真相分层**：销售业务、分配、仓库、物流状态不得重新合并；采购页面不得直接收货；质量状态不得由通用客户端字段任意改写
8. **幂等与操作者**：所有受治理写操作按 OpenAPI 发送幂等键、版本和确认字段；操作人只来自登录会话，前端不得提供可伪造的操作人输入

## 当前维护与验收顺序

1. **基础安全**：登录、强制改密、当前角色权限、角色切换、租户 Host、仓库范围、401/403 和会话失效。
2. **主数据与库存查询**：类别 → 产品/SPU → SKU → 仓库/库位 → 库存汇总、批次和质量状态。
3. **采购与收货**：采购草稿和分仓 → 提交/可选审批 → 快捷确认发货或 ASN 管理 → 独立仓库统一收货 → 可选收货质检；`/inbound` 只承载其他入库申请。
4. **销售与仓库履约**：销售草稿/提交 → 可选审批 → 分配/欠货 → 仓库确认发货 → 物流；列表始终分列显示业务主状态、库存分配和仓库进度。
5. **库存治理、售后与多仓**：冻结/恢复、质检、处置、盘点和校正 → 关联原出库证据的 RMA → 调出、在途和调入闭环。
6. **渠道与经营页面**：Shopify 只读店铺配置、映射、异常复核和人工对账；统一渠道运营工作台仍属于后续范围。

## 验收标准

- 全部页面中英文切换无硬编码文案残留
- 登录 → 改密 → 角色切换后，菜单、按钮和仓库数据范围与当前权限一致；旧 JWT 按安全规则失效
- 采购分仓与确认发货 → 仓库统一收货 → 可选质检，以及销售草稿/提交 → 分配 → 确认发货两条核心链可走通
- RMA 和调拨只能通过各自受治理页面改变库存，不出现采购直接收货、旧入库直接收货或直接库存调拨入口
- `pnpm build` 产物可直接置入 Spring Boot 静态目录运行（相对路径，无绝对 URL）
- OpenAPI 重新导出后，`pnpm generate:api` 结果无漂移，TypeScript、Vitest 和生产构建按发布门禁通过
