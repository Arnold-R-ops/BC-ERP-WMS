# 历史测试数据归档 API 契约

## 目标与边界

该能力用于保留历史测试订单和待拣任务的审计证据，同时把它们从正常业务队列排除。它不是通用删除接口，也不会释放预留、修改批次、调整库存或创建库存流水。

能力开发、V4.46 登记与 V4.47 数据库状态约束已完成。订单 13/14、任务 1/2/3 已在用户逐单授权后完成真实归档；执行证据见 `docs/HISTORICAL_TEST_DATA_ARCHIVE_EXECUTION_2026-08-09.md`。其他订单仍必须重新预览并单独取得写入授权。

## 权限

- 两个端点均仅允许当前权限为 `SUPER_ADMIN` 的认证用户。
- `sales:cancel`、`SECURITY_ADMIN` 或普通业务角色不能访问。
- 提交时操作人 ID 和用户名从认证上下文解析，不接受客户端传入。

## 第一步：只读预览

`GET /api/admin/historical-test-data/sales-orders/{id}/archive-preview`

响应关键字段：

- `eligible`：是否满足全部归档条件。
- `blockers`：机器可读的阻断原因。
- `taskIds`：将被保留并标记作废的任务。
- `reservationCount`、`stockTransactionCount`、`activeShipmentCount`：必须为 0。
- `shipmentCount`：全部运单历史数量；`voidedShipmentCount`：其中已作废数量。已 `VOIDED` 的运单保留并进入快照，但不单独阻断归档。
- `snapshotFingerprint`：当前快照 SHA-256；提交时必须原样带回。

预览不修改订单、任务、预留、批次、库存或审计。

## 第二步：受控提交

`POST /api/admin/historical-test-data/sales-orders/{id}/archive`

请求示例：

```json
{
  "reason": "经批准归档 V4.4 历史测试数据",
  "confirmationOrderNo": "SO20260610001",
  "expectedFingerprint": "64位预览指纹"
}
```

服务端在同一事务中锁定订单、任务和预留记录，重新生成快照并校验：

1. 订单仍为 `APPROVED_AWAITING_SHIPMENT`。
2. 订单的公司、ID、订单号与迁移管理的不可变 `historical_test_data_registry` 登记完全一致；客户以及每条商品/备注仍含明确测试标识。
3. 至少存在一条待拣任务，且所有任务均为 `PENDING`、`actualQty=0`、没有 `reservationId`。
4. 不存在预留记录、库存流水、非 `VOIDED` 运单或 Backorder；历史已作废运单允许保留。
5. 每条订单明细 `shippedQty=0`。
6. 手工输入的订单号与目标一致，预览指纹没有变化。

校验全部通过后：

- 订单与订单明细履约状态变为 `VOIDED`。
- 任务记录保留，状态变为 `VOIDED`，写入归档人、归档时间和原因。
- 订单原审计日志追加 `ARCHIVE_HISTORICAL_TEST_DATA`。
- `historical_test_data_archive_audit` 追加不可修改、不可删除的处置前后快照。
- 不调用预留释放，不修改批次和库存，不创建库存流水。
- 原归档事务成功提交后发布销售事实变更事件，在 `AFTER_COMMIT` 阶段以独立新事务定向刷新订单创建日期的 `sales_daily_summary`；归档回滚时不刷新，且不会在归档事务中执行全量 `refreshAll`。
- 提交后定向刷新若遇到瞬时异常会记录错误，已经提交的归档不会伪装成回滚；每日 03:00 的完整事实刷新负责兜底修复。

## 主要阻断与错误

- `HISTORICAL_ARCHIVE_CONFIRMATION_MISMATCH`：确认订单号不一致，HTTP 400。
- `HISTORICAL_ARCHIVE_NOT_ELIGIBLE`：存在状态、登记身份、客户/商品测试标识、任务、预留、流水、活动运单或 Backorder 阻断，HTTP 409。
- `ORDER_NOT_REGISTERED_AS_HISTORICAL_TEST_DATA`：目标公司、订单 ID、订单号没有完全匹配的不可变登记记录。
- `ACTIVE_SHIPMENTS_PRESENT:n`：存在 `n` 条非 `VOIDED` 运单；已作废运单不计入该阻断项。
- `HISTORICAL_ARCHIVE_SNAPSHOT_STALE`：预览后数据已变化，HTTP 409；必须重新预览。
- `SALES_ORDER_NOT_FOUND`：订单不存在，HTTP 404。

## 真实执行门禁

真实处置前仍必须：重新读取订单 13/14 及任务 1/2/3 的实时预览、人工核对阻断项和指纹、明确逐单写入授权，并在执行前后记录库存/预留/流水基线。本次开发授权不等于真实归档授权。
