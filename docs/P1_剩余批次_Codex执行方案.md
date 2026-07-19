# P1 剩余批次 Codex 执行方案

> 2026-07-19 决策修订版。本文替代此前包含 Shopify 库存回写、发货回写和自动补单的旧方案。

## 1. 已确认边界

1. 实际开发分支为 `2-1G`，当前不处理分支合并。
2. Shopify 只作为订单等数据的读取来源。本系统不修改 Shopify 库存，不回写履约结果，也不调用其他 Shopify 写接口。
3. 一张销售订单允许有多条内部发货记录，用于分批发货、不同承运商和不同运单号；这些记录只保存在 WMS 内部。
4. 对账默认只生成差异报告，不自动补单，不自动改库存。
5. 多店铺的租户与店铺归属模型暂缓，后续单独设计。

## 2. 已完成内容

### 2.1 库存预测空值语义

- 无出库历史或日均出库量为零时，预计缺货天数返回 `null`，不再返回极大浮点数。
- 前端显示为短横线，并提示“暂无出库记录，无法预测”。
- 中英文语言包增加键集合一致性测试。

### 2.2 内部多次发货

新增 `sales_order_shipments`，与销售订单建立一对多关系。每条发货记录独立保存承运商、运单号、状态和时间，并支持作废保留审计痕迹。

接口：

- `GET /api/sales-orders/{salesOrderId}/shipments`
- `POST /api/sales-orders/{salesOrderId}/shipments`
- `PUT /api/sales-orders/{salesOrderId}/shipments/{shipmentId}`
- `POST /api/sales-orders/{salesOrderId}/shipments/{shipmentId}/void`

权限：查询使用 `sales:view`，新增、修改和作废使用 `sales:edit`。

### 2.3 Shopify 只读拉单

- 订单读取支持 Shopify Link 游标分页，不再只读取第一页。
- API 版本可配置，默认使用 `2026-07`。
- 拉单只匹配现有启用客户和现有 SKU 映射，不创建客户，不创建商品。
- 无法匹配的订单或 SKU 保留在原始事件/待映射队列中，由人工处理。
- 正常同步成功后只创建 `PENDING_APPROVAL` 销售订单。

### 2.4 只读对账与受限补单

对账接口：

- `POST /api/integration/shopify/reconcile`：只读取远端数据、比较本地订单并生成报告。
- `POST /api/integration/shopify/reconcile/repair`：仅对人工选中的对账事件执行受限补单。

权限：

- 查看对账报告：`integration:reconcile:view`
- 执行补单：`integration:reconcile:repair`
- 默认仅 `SUPER_ADMIN` 拥有以上权限，后续可显式授权给专门岗位。

## 3. 受限补单的安全规则

补单操作者只能执行以下动作：

1. 从对账报告中选择待处理订单。
2. 匹配系统中已经存在且启用的客户。
3. 使用已经存在、状态正常的 SKU 映射和商品 SKU。
4. 创建一张 `PENDING_APPROVAL` 销售订单。

明确禁止：

- 新建或修改客户。
- 新建或修改产品、SKU、类别或渠道映射。
- 直接增加、减少或校正库存。
- 绕过销售订单审批。
- 自动执行批量补单。
- 向 Shopify 回写库存、履约、运单或其他业务数据。

## 4. 安全业务流程

```text
Shopify 只读拉取
  -> 保存原始事件
  -> 匹配现有客户
  -> 匹配现有 SKU
  -> 创建 PENDING_APPROVAL 销售订单
  -> 人工按正常权限审批
  -> 库存预留与 FEFO 分配
  -> 生成出库任务
  -> WMS 内部发货记录
```

在人工审批前：

- 不预留库存。
- 不生成出库任务。
- 不改变现有库存数量。

因此，补单权限本身不能绕过库存模型，也不能直接损坏库存账本。

## 5. 对账调度策略

- 定时对账开关为 `wms.integration.shopify.reconcile-enabled`，默认关闭。
- 即使开启，调度器也只生成对账报告，不自动调用补单接口。
- 对账报告保存为 `RECONCILE_REPORT` 原始事件，保留来源和审计记录。
- 仅处理已付款、未取消且尚未履约的候选订单；其他订单记录原因并跳过。

## 6. 数据库迁移

- `V4_21__Sales_Order_Shipments.sql`：建立内部多次发货记录表。
- `V4_22__Shopify_Reconciliation_Permissions.sql`：增加对账查看与受限补单权限。

本次不新增 Shopify 回写字段、回写任务或写接口。

## 7. 验证结果

- Java 全量测试：`1066/1066` 通过，失败 `0`，错误 `0`，跳过 `0`。
- 前端测试：`41/41` 通过。
- 前端 TypeScript 检查与生产构建：通过。
- Shopify 集成代码扫描：未发现 `POST`、`PUT`、`PATCH`、`DELETE` 远端调用，也未发现库存或履约回写端点。

## 8. 后续待定

多店铺环境下的店铺归属、客户匹配隔离、外部订单号唯一性和权限边界尚未定稿。在该模型明确前，不扩展多店自动补单能力。
