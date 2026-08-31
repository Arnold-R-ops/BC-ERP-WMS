# TENANT-ERP-P0-04A 销售订单四层状态重构实施报告

- 日期：2026-08-31
- 分支：`codex/tenant-erp-p0-04a`
- 基线提交：`abb4954`
- 交付状态：开发和自动化验证已完成；尚未合并、推送、部署或执行现有环境数据迁移。

## 1. 本阶段交付结果

P0-04A 已删除同时表达商务审批、库存分配和仓库发货的旧 `SalesOrderStatus` 与 `FulfillmentStatus` 真相源，将销售订单收口为四个相互独立的业务维度：业务主状态、库存分配、仓库进度和物流进度。列表默认展示前三列，物流明细在订单详情内展示。

订单创建现在明确区分“保存草稿”与“提交订单”。审批策略与套餐解耦，普通租户默认 `DIRECT`，也可由有独立策略权限的租户管理员切换为 `RISK_BASED` 或 `ALWAYS`。

## 2. 四层状态真相

- 业务主状态 `commercialStatus`：`DRAFT`、`PENDING_APPROVAL`、`APPROVED`、`COMPLETED`、`CLOSED`、`REJECTED`、`CANCELLED`、`VOIDED`。
- 库存分配 `allocationStatus`：`UNALLOCATED`、`FULLY_ALLOCATED`、`PARTIALLY_ALLOCATED`、`BACKORDERED`、`WAITING_INBOUND`。
- 仓库进度 `warehouseStatus`：当前使用 `NOT_RELEASED`、`PENDING_PICK`、`PICKING`、`PARTIALLY_SHIPPED`、`SHIPPED`、`EXCEPTION`；保留已确认但本阶段不产生的 `PICKED`、`PENDING_CHECK`、`PACKING`、`READY_TO_HANDOVER`。
- 物流进度 `logisticsStatus`：`NOT_CREATED`、`LABEL_CREATED`、`HANDED_TO_CARRIER`、`IN_TRANSIT`、`DELIVERED`、`EXCEPTION`、`RETURNED`。

四层状态均由服务端根据真实业务证据写入或投影，客户端不能直接传入状态。未出库数量不足时保留部分分配与欠货明细；部分发货后关闭剩余数量会保留已发货事实，业务状态为 `CLOSED`、仓库状态为 `PARTIALLY_SHIPPED`。

## 3. 销售订单流转

- 草稿可编辑或删除，不分配库存。
- 提交后按租户策略直接批准或进入待审批；待审批期间不分配库存。
- 批准后幂等地执行分配，分配、欠货与入库等待状态由服务端汇总。
- 未开始拣货的已批准订单可取消并释放预留；已开始拣货时禁止取消。
- 部分发货只能关闭剩余数量，已发货数量、库存流水和发货证据不变。
- 无预留、无仓库任务、无发货、无库存流水的已提交错单才允许作废。
- 所有关键操作人均来自登录账号，取消、关闭、作废、驳回和物流变更保留原因及审计时间。

## 4. 真实发货与物流

旧“确认拣货”实际会消耗预留、扣减库存并完成出库，现已按真实行为改名为“确认发货”：

- 单任务接口改为 `POST /api/outbound-tasks/{id}/dispatch`。
- 批量接口改为 `POST /api/outbound-tasks/batch-dispatch`。
- 旧 `/confirm` 与 `/batch-confirm` 路径已删除，不保留兼容别名。

基础承运商和运单号继续可用。详细物流生命周期为租户级可选开关，默认关闭；开启后只有 `sales:logistics:update` 或租户管理员可人工更新，每次更新要求原因并写入追加式时间线。关闭开关只隐藏功能，不删除历史。

## 5. 幂等、权限与租户隔离

- 销售写操作统一要求 `Idempotency-Key`，按 `company_id + idempotency_key` 唯一，同一公司内重放返回原子执行结果。
- 幂等证据和物流事件表均启用并强制 RLS，并由数据库触发器保护为追加式证据。
- 新增 `sales:close`、`sales:logistics:update`、`sales:policy:manage` 三项独立权限，普通用户默认不获得。
- `sales:policy:manage` 定义为 `HIGH + 可分配`，与“租户管理员可开启、普通用户默认无权、可独立授权”一致，也遵守数据库对 `CRITICAL` 权限禁止自定义分配的约束。

## 6. 前端、接口与数据库

- 销售列表默认显示业务主状态、库存分配和仓库进度，支持四层状态筛选。
- 新增销售策略弹窗、草稿/提交操作、取消/关闭/作废操作与物流时间线。
- 仓库任务页和移动端同步使用“确认发货”语义，没有伪造拣货与发货的阶段拆分。
- 新增迁移 `V4_82__Sales_Four_Layer_Status_And_Command_Governance.sql`，删除旧字段与旧索引，新增四层状态约束、物流事件、幂等事件、审计字段、RLS、不可变触发器和权限目录。
- 实时 OpenAPI 已同步到 `docs/frontend/openapi.json`，TypeScript 契约已重新生成到 `frontend/src/api/schema.d.ts`。旧出库路径、`SalesOrderStatus` 和 `FulfillmentStatus` 模型残留数均为 0。

## 7. 验证结果

- 后端最终全量 `mvn test`：`1605` 项，失败 `0`，错误 `0`，跳过 `0`。
- 前端全量 Vitest：`75` 个测试文件、`283` 项全部通过。
- 前端 TypeScript 类型检查和生产构建：通过。
- 全新 PostgreSQL `18.6` 空库：Flyway 成功校验 `98` 项迁移，从空库实际执行 `V4.71`–`V4.82`，Hibernate `validate` 和应用启动均通过。
- 已有租户升级：在 `V4.81` 创建租户和旧 `outbound:pick` 权限后，单独执行 `V4.82` 并通过启动校验；两项默认策略、三项新权限和新发货路径全部正确。
- 数据库核对：旧 `status`/`fulfillment_status` 字段残留为 0，6 项销售状态约束存在，2 张证据表启用并强制 RLS，2 个不可变触发器、租户复合外键和公司级幂等唯一键均正确。
- 验证应用、两个隔离 PostgreSQL 实例及相关端口已停止，一次性数据目录已删除；现有开发数据库未执行迁移。
- `git diff --check`：通过，仅有 Windows 换行符提示。

## 8. 明确未纳入 P0-04A 的事项

- 不开发 RMA、销售退货、退货入库或已发货订单撤销；这些属于 P0-04B。
- 不开发调拨申请、调出、在途、调入或跨仓库闭环；这些属于 P0-04C。
- 不启用拣货完成、复核、打包和待交接的仓内细分阶段；待 P0-05 再按真实作业开发。
- 不执行手机、手持终端、摄像头扫码、标签打印机或面单打印机的真实设备验收。
- 不执行合并、推送、部署或现有环境数据迁移。

## 9. 下一步

P0-04A 完成后，下一阶段应按租户 ERP 主交接队列进入 P0-04B（RMA/销售退货闭环）的阶段方案确认，不在本提交内自动扩展。
