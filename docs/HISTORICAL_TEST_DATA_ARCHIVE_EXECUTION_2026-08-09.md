# 历史测试数据归档执行记录（2026-08-09）

## 授权与范围

- 用户明确授权归档订单 13、14及关联任务 1、2、3。
- 执行顺序固定为订单 13完成并核验后，才处理订单 14。
- 禁止释放或重算预留、修改批次库存、创建库存流水、删除任务或删除已作废运单。

## 执行前基线

| 对象 | 状态/数量 |
|---|---|
| 订单 13 / `SO20260610001` | `APPROVED_AWAITING_SHIPMENT`；任务 1、2；预留 0；库存流水 0；活动运单 0；已作废运单 1 |
| 订单 14 / `SO20260610002` | `APPROVED_AWAITING_SHIPMENT`；任务 3；预留 0；库存流水 0；运单 0 |
| 任务 1、2、3 | 均为 `PENDING`、`actualQty=0`、`reservationId=null` |
| 批次 6 | `quantity=0`、`reservedQuantity=0` |
| 批次 7 | `quantity=85`、`reservedQuantity=0` |

执行前预览指纹：

- 订单 13：`91361b03c910f98dc31e2ce0eebd69c4ac70a3b5a9e4e811df471b9a89cc7524`
- 订单 14：`b23357769cf5ab26fb1921aead249405f4c02d317e4565371a0cc72d6181886d`

## 首次提交与安全回滚

- 首次提交订单 13时，PostgreSQL 原有 `outbound_tasks_status_check` 尚未包含 `VOIDED`，数据库拒绝任务状态更新并返回 HTTP 400。
- 服务事务完整回滚；随后重新预览确认订单 13仍为 `APPROVED_AWAITING_SHIPMENT`、`eligible=true`，指纹保持不变，订单 14未被调用。
- 新增并应用 `V4_47__Allow_Voided_Outbound_Task_Status.sql`，将数据库约束与 Java 枚举对齐；迁移契约及归档定向测试 14/14 通过。

## 最终执行结果

| 订单 | 归档结果 | 保留任务 | 审计 ID | API 报告的库存影响 |
|---|---|---|---:|---|
| 13 | `VOIDED` | 1、2均为 `VOIDED` | 1 | 库存未变化、预留未变化、未创建库存流水 |
| 14 | `VOIDED` | 3为 `VOIDED` | 2 | 库存未变化、预留未变化、未创建库存流水 |

归档后独立只读复核：

- 两张订单及订单明细履约状态均为 `VOIDED`，明细 `shippedQty=0`。
- 任务 1、2、3均保留，状态为 `VOIDED`、`actualQty=0`、`reservationId=null`。
- 批次 6仍为 `quantity=0`、`reservedQuantity=0`；批次 7仍为 `quantity=85`、`reservedQuantity=0`。
- 两单预留记录数和库存流水数仍为 0。
- 订单 13的运单 ID 2仍保留且为 `VOIDED`；订单 14仍无运单。
- 归档后的预览被订单/任务终态门禁阻断，不能重复归档。

## 技术验证

- Flyway 已从 V4.46成功升级到 V4.47。
- 归档相关定向测试 14/14 通过。
- 最终 Surefire 报告：124 个测试套件、1282 项测试，失败 0、错误 0、跳过 0。
- 当前开发服务健康检查返回 HTTP 200。

