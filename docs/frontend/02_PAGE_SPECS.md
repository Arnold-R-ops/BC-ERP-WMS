# V1 页面规格书

> 字段级细节以 openapi.json 为准；本文讲清每页的业务意图、状态流转和关键交互。
> 通用约定：列表页 = ProTable（查询表单+分页+列设置）；详情用抽屉或详情页均可但全站统一。

## 0. 全局布局

- ProLayout 侧边菜单，顶栏右侧：语言切换（中/EN）、当前角色徽标（点击可切换角色，调 switch-role）、用户菜单（改密码、登出）
- 菜单结构：工作台 / 商品 / 库存查询 / 销售订单 / 采购管理 / 入库管理 / 渠道集成（店铺配置、SKU 映射、待映射队列、人工复核）

## 1. 工作台（Dashboard）

四个待办卡片（数字角标 + 点击跳转对应列表的预筛选视图）：

| 卡片 | 数据来源 |
|---|---|
| 待审批销售订单 | `GET /api/sales-orders?status=PENDING_APPROVAL` 取条数 |
| 待映射 SKU | `GET /api/integration/sku-mappings/pending` 取数组长度 |
| 待人工复核渠道事件 | `GET /api/integration/raw-events?status=MANUAL_REVIEW` 取 totalElements |
| 紧急补货建议 | `GET /api/predictions/reorder/urgent` 取条数 |

下方两个快捷列表：最近待审批订单（前 5 条，行内审批入口）、紧急补货商品清单。

## 2. 商品管理

- 列表：`GET /api/products`（搜索名称/条码，enabled 筛选）
- 新建/编辑：`POST /api/products` / `PUT /api/products/{id}`——字段较多（SPU、条码、箱规换算率、临期阈值、最低限价、安全库存），用分组表单；**条码字段加提示**：“与渠道 SKU 一致可自动匹配订单”
- 无删除（主数据逻辑删除靠 enabled 停用）

## 3. 库存三层穿透查询（只读）

三层钻取，是仓库日常最高频页面：

1. **SKU 汇总层** `GET /api/inventory/summary?page=&search=`：每行显示 在库/预留/可用（可用=在库-预留，用颜色区分）、近效期标识 → 点击行进入第 2 层
2. **批次明细层** `GET /api/inventory/details/{skuId}`：该商品全部活跃批次（批次码/效期/库位/数量），效期临近的行黄色预警、已过期红色
3. **库位反查层** `GET /api/inventory/location/{locationCode}`：输入/扫描库位码，显示该库位上所有批次——做成第 1 层页面顶部的快捷搜索框（按库位查）

## 4. 销售订单

- 列表：`GET /api/sales-orders?status=&customerId=`，状态用 Tab 分组（待审批/待发货/已发货/已取消），渠道列显示徽标（SHOPIFY/MANUAL/WECHAT）
- 详情：订单头 + 明细行（商品/数量/单价/小计）+ 状态时间线（创建→审批→发货）
- **审批**：`POST /{id}/approve`（成功即触发库存预留+智能分配，提示文案要说明）；拒绝 `POST /{id}/reject`（必填原因）
- 取消：`POST /{id}/cancel`（必填原因；提示会释放预留库存）；作废 `POST /{id}/void` 放在更深的菜单里（属系统清理操作，标注“作废用于清理错误/测试数据”）
- 新建：`POST /api/sales-orders`（客户下拉 `GET /api/customers` + 明细行动态增减 + **可选渠道标记 channel，默认 MANUAL，选项含 WECHAT**）；Excel 导入入口（template 下载 + upload）
- 状态流转参考：DRAFT → PENDING_APPROVAL → APPROVED_AWAITING_SHIPMENT → SHIPPED；旁路 REJECTED / CANCELLED / VOIDED

## 5. 采购管理（三阶段）

阶段即状态列 Tab：ORDERING（下单中）→ IN_TRANSIT（在途）→ PARTIALLY_RECEIVED/COMPLETED

- 创建 `POST /api/purchase-orders`（供应商 + 明细行）+ Excel 导入
- **确认 ASN** `PUT /{id}/confirm`：录入每行效期 → 系统生成批次码（界面展示生成的批次码）
- **收货** `PUT /{id}/receive`：按行录入实收数量 + 选择库位（库位下拉 `GET /api/locations/warehouse/{id}`），支持多次收货直到收满
- 回滚 `PUT /{id}/rollback`（IN_TRANSIT→ORDERING，需确认弹窗）

## 6. 入库管理（四步流程）

按状态 Tab：PENDING_APPROVAL → APPROVED_PLAN → AWAITING_RECEIVAL → COMPLETED（+ REJECTED）

- 创建入库计划 `POST /api/inbound-orders`
- 审批 `POST /{id}/approve-plan`（角色权限：经理）；拒绝 `POST /{id}/reject`
- 确认订单 `POST /{id}/confirm-order`：录入确认数量
- 实物收货 `POST /{id}/receive-goods`：录入实收数量/效期/库位 → 生成批次库存
- 三段数量（计划/确认/实收）在详情页并排展示，差异高亮

## 7. 渠道集成运营台（V1 的灵魂模块，交互要求最高）

### 7.1 店铺配置
- 列表/新建/编辑：`/api/integration/configs`（密钥字段显示为掩码，编辑时留空=不修改——界面加说明文字）
- **测试连接**按钮：`POST /{id}/test-connection`，成功显示店铺名/币种/时区
- 手动同步按钮（页面顶部）：`POST /api/integration/shopify/sync`，返回 成功/跳过/失败 计数 toast

### 7.2 待映射队列（核心页面，按此交互实现）
- 列表：`GET /api/integration/sku-mappings/pending`（默认 status=PENDING，按卡单次数倒序）
- 每行展开卡片：外部 SKU、商品标题、样例单价、卡单次数、样例订单号
- 右侧操作区：
  - 候选推荐 `GET /pending/{id}/suggestions`（单选列表，展示 条码+名称）
  - 或手动搜索商品（复用商品列表接口）
  - 数量换算比输入框（默认 1，帮助文案：“1 个渠道单位 = N 个内部单位”，>1 时显示预览：“顾客买 1 件 → 仓库发 N 件”）
  - 三个动作按钮：**确认映射**（action=MAP+productId+ratio）/ 标记虚拟行（VIRTUAL，说明“运费/服务费等不占库存的行”）/ 忽略（IGNORE）
- resolve 成功后 toast：“已映射。被卡订单将在下次同步自动放行”，并提供“立即同步”快捷按钮（调 sync）
- `NOSKU::` 前缀的行加特殊徽标“无 SKU 行”

### 7.3 SKU 映射管理
- 列表 `GET /api/integration/sku-mappings?channel=`：外部SKU → 内部商品、换算比、类型（PRODUCT/VIRTUAL）、来源（AUTO 自学/MANUAL）、状态
- 编辑（改商品/换算比/停用启用）`PUT /{id}`；手动新建 `POST`

### 7.4 人工复核列表
- `GET /api/integration/raw-events?status=MANUAL_REVIEW`（分页）：事件类型（渠道取消/渠道改单）、外部单号、原因说明、时间
- 行点击 → 详情抽屉 `GET /{id}` 展示原始 JSON（格式化+可复制）
- V1 只做“查看+知晓”，处理动作（改单/退货）由人工在对应订单页操作——抽屉里放“跳转本地订单”链接（按 externalId 查销售订单）
- FAILED Tab 一并提供（失败原因排查用）

## 8. 用户侧小页面

- 改密码页（见认证契约）
- 角色切换（顶栏下拉，调 switch-role 后刷新整站权限）
