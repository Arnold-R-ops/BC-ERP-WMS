# TENANT-ERP-P0-01 库存写入口清单与调用图

> **历史快照（截至 V4.73）**：本文记录 P0-01 当时的现状与后续接线边界，不再是当前接口说明。采购/其他入库统一收货已由 P0-02 完成，销售出库和盘点已由 A1 接入统一记账，公共预留释放接口也已由 A1 删除。当前真相见 `TENANT_ERP_A1_IMPLEMENTATION_REPORT_2026-09-01.md` 和实时 OpenAPI。

日期：2026-08-25

基线：`2-1G@5a719e6`
范围：首个 Epic 的现状盘点和接线边界；采购、入库、销售、出库、盘点的业务行为本 Epic 暂不重构。

## 1. 真相源与统一规则

- `inventory_batch.quantity` 是实存 `OnHand` 真相源；`reserved_quantity` 是已预留数量，`Available = OnHand - Reserved`。
- 新的 `InventoryPostingService` 是后续实存变动的统一事务边界，强制接收公司、仓库、SKU、批次、来源、来源单、原因、操作者、`postingId` 和幂等键。
- 首个端到端接线切片只有“紧急库存校正最终落账”。其他业务域保持现有行为，后续 Epic 逐域迁入统一记账服务。
- 库存流水 `stock_transactions` 从 V4.73 起可保存仓库、库存批次、批次码、`postingId` 和幂等键；历史流水允许这些新增字段为空。

## 2. 已退役的公共直写入口

| 原入口 | 原风险 | V4.73 后结果 |
|---|---|---|
| `POST /api/inventory/adjust` | 客户端可填来源和操作者并直接覆盖库存 | Controller、DTO、独立落账逻辑和契约暴露删除 |
| `PUT /api/inventory/batches/{batchCode}/adjust` | 绕过来源单、审批、预留保护和统一流水 | Controller、专属服务逻辑和契约暴露删除 |
| `POST /api/inventory/batches/outbound` | 任意 FIFO 扣减，可能侵占已预留库存 | Controller、无调用旧扣减方法和契约暴露删除 |

旧权限 `inventory:adjust` 随迁移停用且不再绑定路径。库存校正改用 `inventory:correction:execute`；欠货重分配改用独立的 `backorder:wake`，避免复用一个高风险通用权限。

## 3. 截至 V4.73 当时仍存在的库存或预留写入口

| 业务入口 | 调用链 | 写入事实 | 本 Epic 状态 |
|---|---|---|---|
| `PUT /api/purchase-orders/{id}/confirm` | `PurchaseOrderController → PurchaseOrderService.confirmAndGenerateBatchCodes` | 创建尚未分配库位但已带订购数量的批次 | 保持现状；采购/统一收货 Epic 将改为只登记预计到货，不提前形成真实库存数量 |
| `PUT /api/purchase-orders/{id}/receive` | `PurchaseOrderController → PurchaseOrderService.receiveGoods` | 分配库位、写采购入库流水、发出可用库存事件 | 保持现状；统一收货 Epic 后下线采购页直接收货 |
| `POST /api/inbound-orders/{id}/receive-goods` | `InboundOrderController → InboundOrderService.receiveGoods` | 新建或增加批次数量并写入库流水 | 保持现状；统一收货 Epic 合并为唯一收货任务链路 |
| 销售单创建/批准 | `SalesOrderController → SalesSubmissionService → AllocationService` | 增加批次预留、创建 Reservation 和 OutboundTask；不改变 OnHand | 保持现状；后续销售状态/分配 Epic 接线并发锁与统一预留语义 |
| 销售取消/作废 | `SalesOrderController → SalesSubmissionService → InventoryReservationService` | 释放开放预留；不改变 OnHand | 保持现状 |
| `POST /api/inventory/reservations/{id}/release` | `InventoryReservationController → InventoryReservationService` | 直接释放指定预留；不改变 OnHand | 现状记录；需在销售/预留 Epic 决定是否保留公共入口、补最终权限与来源审计 |
| `POST /api/backorders/wake/product/{skuId}` | `BackorderController → BackorderService.wakeProduct` | 对开放欠货重新分配并增加预留 | 保持功能，改用专用 `backorder:wake` 权限 |
| `POST /api/outbound-tasks/{id}/confirm`、`/batch-confirm` | `OutboundTaskController → OutboundService` | 消耗 Reservation、扣减 OnHand、写出库流水 | 保持现状；后续出库域迁入统一记账服务 |
| `POST /api/stocktake/tasks/{id}/review` | `StocktakeController → StocktakeService.reviewStocktake` | 审批盘点差异后改 OnHand 并写盈亏流水 | 保持现状；后续盘点域迁入统一记账服务 |
| `POST /api/emergency-stock-corrections/{id}/apply` | `EmergencyStockCorrectionController → EmergencyStockCorrectionService → EmergencyStockCorrectionApplicationExecutor → InventoryPostingService` | 按冻结差额叠加当前数量，原子写批次、流水和 Outbox | 本 Epic 已接入统一记账、预览凭证、审批快照、预留保护和租约式幂等 |
| 租户数据清理/历史测试数据归档 | 平台治理服务 | 在受保护前置条件下删除租户或测试数据 | 非业务库存入口；由 `app.tenant_purge` 和平台审计单独治理 |

## 4. 首个切片调用图

```text
制单
  └─ 冻结 systemQty / countedQty / adjustmentQty
       ├─ NONE：制单人获取 impact → 二次确认时冻结 approvalModeSnapshot
       ├─ TWO_PERSON：制单人提交时冻结 approvalModeSnapshot → 不同审批人批准 → 获取 impact
       └─ THREE_PERSON：制单人提交时冻结 approvalModeSnapshot → 不同复核人 → 不同审批人 → 获取 impact

impact
  └─ 读取修正版本 + 当前批次版本/OnHand/Reserved
       └─ 返回 projectedQty、受影响销售单、5 分钟签名 previewToken

apply(Idempotency-Key, previewToken)
  ├─ 公司范围幂等 claim（请求指纹 / PROCESSING 租约 / 成功或确定失败重放）
  └─ 原子应用事务
       ├─ 锁修正单 → 锁库位 → 锁批次
       ├─ 校验工作流、操作者分离和签名预览快照
       ├─ 校验 projectedQty >= Reserved
       ├─ delta = 0：只保存校正审计结果
       └─ delta != 0：InventoryPostingService
            ├─ 校验公司/仓库/SKU/库位/批次
            ├─ 更新 OnHand
            ├─ 追加 stock_transactions
            └─ 追加 INVENTORY_POSTED Outbox
```

## 5. 截至 V4.73 当时仍需逐域接线的边界

1. 采购确认当前提前创建带数量批次，采购收货与独立入库仍是两条增加库存链路；由采购/统一收货 Epic 解决。
2. 出库、盘点、采购和独立入库仍直接修改批次并自行写流水；本 Epic 不改变其线上行为，后续按业务域迁入 `InventoryPostingService`。
3. Reservation 的公共释放入口缺少明确业务来源与方法级最终权限；在销售/预留 Epic 中决定删除或收紧，不能把它误当作人工库存校正。
4. Shopify 仍为远端只读接单，不直接改商品、库存或履约；Webhook 幂等兼容仅保证瞬态失败可重试。
