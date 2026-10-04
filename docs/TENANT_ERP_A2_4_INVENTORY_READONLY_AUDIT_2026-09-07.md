# A2-4 隔离 UAT 库存与单据只读核账

- 日期：2026-09-07；主体数据库快照：01:20:43 +08:00；附加只读护栏快照：08:15:02 +08:00。
- 范围：`127.0.0.1:55484/wms_a2_uat_20260902`，角色 `wms_p004c_app`。
- 结论：现用批次库存、按 SKU/库位汇总的库存流水、预留余量、收货/出库完成事实及调拨收发在途在本次快照中一致。发现遗留 `inventory` 空表与当前批次表不一致，以及治理租户 B 的已知诊断残留；均按下文单独说明，不能将所有表或全部 A2 验收笼统标记通过。
- 未实施：业务数据写入、样本重置、补账、审批/出库 API、迁移、服务重启、Git 提交/推送/部署。

## 1. 方法与证据

所有数据查询均在 `BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY` 中执行，按 `tenants.slug` 分别解析 `a2-ordinary` 和 `a2-optional`，使用事务级 `set_config('app.company_id', id::text, true)`，最后 `ROLLBACK`。实际读回 `transaction_read_only=on`。没有绕开 RLS 或使用超级用户。

可复查产物（均在本隔离工作树）：

- `target/a2-4-audit-readonly.sql`：两租户驱动和 RLS 元数据检查。
- `target/a2-4-audit-tenant.sql`：两租户共用的汇总、逐条、重复性和关联检查。
- `target/a2-4-audit-readonly.txt`：完整 SQL 输出，最终有效快照见上方时间。
- `target/a2-4-audit-schema.sql`：只读结构和 B 租户任务 ID 探查。
- `target/a2-4-audit-extra.sql`、`target/a2-4-audit-extra-tenant.sql`、`target/a2-4-audit-extra.txt`：附加逐批质量拆分平衡、数量护栏和无公司上下文检查。

SQL 内没有密码。执行时仅暂时将既有 `DB_PASSWORD` 赋给 `PGPASSWORD`，结束后恢复此前值，不输出认证信息。

注意：`stock_transactions.quantity` 保存正数绝对值；核账按 `transactiontype=OUT` 取负、`IN` 取正。现用代码 `InventoryPostingService.createTransaction` 约第 287～302 行亦明确如此。本报告的流水算术检查已按此口径执行，不能直接把 OUT 的正数字段当成入库。

## 2. 库存总量与现用流水

| 租户/仓库 | 批次数（含零库存） | 实存 | 预留 | 可用且未过期 |
| --- | ---: | ---: | ---: | ---: |
| A / A2-WH-A，仓库 ID 1 | 6 | 36 | 0 | 36 |
| A / A2-WH-B，仓库 ID 2 | 1 | 2 | 0 | 2 |
| B / A2-WH-A，仓库 ID 5 | 5 | 45 | 0 | 15 |

可用口径为启用批次、质量状态 `AVAILABLE`、数量减预留、未过效期；本快照使用 `Europe/London` 当地日期核对。现有样本没有日期边界效期，故不代表已通过时区边界专项验收。

A 租户流水共 10 条：采购入库 42 + 退货入库 1 + 调拨入库 2 − 销售出库 5 − 调拨出库 2 = **38**。其中手机本轮新增入库 10、出库 2，相对上一闭环 30 件增加 8 件。

B 租户流水共 5 条：采购入库 40 + 其他入库 5 = **45**。待检也是已收到的实物，不可为了让成功样本总量成为 15 而从总账剔除。

按 `(product_sku_id, location_id)` 进行批次数量和流水有符号净额 FULL JOIN：

| 租户 | SKU ID | 库位 ID | 批次实存 | 流水净额 | 差异 |
| --- | ---: | ---: | ---: | ---: | ---: |
| A | 1 | 1 | 14 | 14 | 0 |
| A | 1 | 5 | 2 | 2 | 0 |
| A | 2 | 1 | 5 | 5 | 0 |
| A | 3 | 1 | 17 | 17 | 0 |
| B | 10 | 17 | 5 | 5 | 0 |
| B | 12 | 17 | 40 | 40 | 0 |

两租户合计 12 个批次，`quantity<0`、`reserved_quantity<0` 或 `reserved_quantity>quantity` 的记录均为 0。

质量拆分不能错误当成缺失收货：A 的批次 1 曾有 2 件拆至批次 8，`inventory_quality_action_line` 第 1 行记录 `source_batch_id=1,result_batch_id=8,quantity=2`；第 2 行是在批次 8 原地放行 2 件。该同 SKU/同库位拆分保持实物总量不变，因此总账按 SKU/库位核账；直接要求每一拆分子批次都具有最初收货流水会误报。此次已读取拆分证据，但未完成覆盖全部质量转换类型的逐批重放算法。

## 3. 预留和调拨在途

A 有 3 条销售预留，数量分别 3、1、1，`consumed_qty` 均等于 `reserved_qty`，`released_qty=0`，状态全部 `CONSUMED`。1 条调拨分配共 2 件，已消耗 2、释放 0，亦为 `CONSUMED`。B 无销售预留或调拨分配。

逐批核对：

`batch.reserved_quantity = SUM(sales reserved_qty-consumed_qty-released_qty) + SUM(transfer allocated_quantity-consumed_quantity-released_quantity)`

12 个批次的差异全部为 0。保留销售预留与调拨分配两个来源，未遗漏调拨占用。

调拨单 `TR-33E98998A19341D8A4F393DB`：

- 单据状态 `COMPLETED / DISPATCHED / RECEIVED`。
- 请求 2、当前分配 0、已发 2、已收 2，未发关闭和短收关闭均 0。
- 单据行与发货行在途公式 `dispatched-received-shortage_closed` 均为 0。
- 目的批次 ID 10 实存 2；调出/调入各有一条数量 2 的流水，ID 10/11。

该证据覆盖一张已完整收发的调拨单，不代表非零在途、短收、损坏到货等异常场景已重新执行。

## 4. 单据完成状态和逐条落账

普通租户 A：

- 采购单 ID 1 共 32 件、ID 6 共 10 件，均 `COMPLETED`；各采购行应收等于已收。
- ASN ID 1、6 均 `RECEIVED`，已关闭数量 0；收货任务 ID 1、7 均 `COMPLETED`。
- 本轮手机收货任务 `RCV-1F089BAFB1A640B1` 对应两行各 5 件，收货事件、批次 ID 11/12、流水 ID 12/13 一致。
- 销售单 ID 1、2 均 `COMPLETED / FULLY_ALLOCATED / SHIPPED`，发货分别 3、2 件。
- 出库任务 ID 1/2/3 均 `COMPLETED`，计划/实际分别 3/3、1/1、1/1，与预留消耗和 `SALE-OUT:1/2/3` 三条流水一致。
- 盘点任务 ID 1/2 均 `COMPLETED`、4/4、差异项 0；计数行数量与任务统计一致。

两租户 SQL 指标均为 0：收货事件行与库存流水的批次/数量/方向不匹配；完成出库任务与流水不匹配；收货任务行已收数量与事件合计不匹配；完成收货任务存在未结数量；完成盘点统计不匹配；盘点差异不等于实盘减快照。

B 无销售/出库/盘点/调拨样本。因此对应检查的 0 表示没有适用行，不能当成 B 这些业务端到端已通过。

## 5. 幂等和 Outbox 事实

本次仅检查已存在的事实，不重新发送任何收货/发货/审批请求。

- A 10 条、B 5 条库存流水的 `posting_id`、`idempotency_key`、`inventory_batch_id` 均非空。
- 两租户内重复 `posting_id`、重复 `(posting_id,batch_id)`、重复 `(idempotency_key,batch_id)` 组均为 0。
- 收货事件、调拨发货、调拨收货、库存状态事件的各自幂等键重复组均为 0。
- 同一次收货幂等键可对应多个不同明细，属于正常原子提交，不能把键出现两次本身当成重复落账。
- 全部 15 条库存流水与 15 条 `INVENTORY_POSTED` Outbox 事件的 posting ID、批次 ID 和有符号变化量一一对应；缺失、孤立或重复事件均为 0。
- 流水 `quantityafter-quantitybefore` 与按 IN/OUT 符号计算的数量一致，算术异常 0。

Outbox 当前全为 `PENDING`：A 共 19 条（库存 10、质量 3、RMA 2、调拨 4），B 共 6 条（库存 5、质量 1）。本次验证的是事件与账务一同持久化的事实，**不代表外部消费者发布/重试已验收**。没有启用渠道轮询、调用第三方或消费这些事件。

以上是静态“没有重复事实”的证据，不代替并发重复请求、故障重试等动态幂等测试。

## 6. B 租户诊断残留的界定

| 批次 ID | 数量 | 质量状态 | 质检任务 | 意义 |
| --- | ---: | --- | --- | --- |
| 3 | 10 | PENDING_INSPECTION | ID 1 / PENDING，待处理 10 | 既有诊断残留 |
| 4 | 10 | PENDING_INSPECTION | ID 2 / PENDING，待处理 10 | 既有诊断残留 |
| 5 | 10 | PENDING_INSPECTION | ID 3 / PENDING_REVIEW，待复核 10 | 既有诊断残留 |
| 6 | 10 | AVAILABLE | ID 4 / COMPLETED，通过 10；结果 ID 2 / APPROVED | 已通过的采购质检闭环 |
| 7 | 5 | AVAILABLE | 无收货质检任务 | 已通过的其他入库样本 |

来源归属与 A2 执行方案第 12.4 节的诊断记录一致。四张采购、四张 ASN 均已收货完成，不意味着其质检已全部完成。剩余 30 件实物与质检待办对应，没有发现无账库存或越出租户的问题。

本轮保留全部记录，既不删除，也不把 30 件自动审批为可用。成功闭环样本可明确限定为批次 6/7 共 15 件；全租户真实总账仍为 45 件。原方案“重建干净样本”未在本次只读阶段执行，是否需要新建隔离样本应单独纳入后续测试安排。

## 7. 遗留 inventory 表：不一致但不是当前汇总源

`inventory` 在 A、B 均无记录；与 `inventory_batch` 按 SKU/库位 FULL JOIN 分别缺 4 组（38 件）和 2 组（45 件）。**两表不一致，不能写成 inventory 与 batch 对账通过。**

源码确认当前库存查询真相源：

- `InventoryService.getTotalStock/getReservedStock/getAvailableStock` 均查询 `InventoryBatchRepository`（约第 35～51 行）。
- `InventoryController` 仅暴露 total-stock 和 health，没有把 legacy 分布方法暴露为接口。
- 主要汇总页 `InventoryQueryService` 第 41 行调用 `InventoryBatchRepository.findInventorySummaryRows`。
- 仍残留 `getStockDistribution/getStockByLocation/getLowStockInventories` 三个读旧 `InventoryRepository` 的服务方法；本次在主源码和前端搜索只找到声明/实现，没有业务调用者。

故现用汇总与流水/批次一致；空 `inventory` 表属于遗留双模型技术债，未来若复用这些旧方法会返回空值。建议后续专门确认清理或统一查询口径，不为通过验收直接补写旧表，也不在本轮擅自改业务代码。

## 8. RLS 与边界

角色实测 `rolsuper=false`、`rolbypassrls=false`。

对 14 张核心表读取配置：`inventory`、`inventory_batch`、`inventory_reservations`、`outbound_tasks`、`receipt_event`、`receipt_event_item`、`receiving_task`、`sales_orders`、`stock_transactions`、`stocktake_tasks`、`transfer_allocation`、`transfer_dispatch`、`transfer_order`、`transfer_receipt` 均启用 RLS 且 FORCE RLS；策略 `ALL` 的 `USING` 和 `WITH CHECK` 均为 `company_id=bcwms_current_company_id()`。

A、B 两种事务上下文下实际扫描库存批次、库存流水、销售预留、收货任务、销售单、出库任务、盘点任务和调拨单，外公司行数均 0；两租户可见总量不同，且 B 的已存在收货任务 ID 2～6 不会出现在 A 的扫描结果。

此次核实了写入 RLS 策略配置，但**未执行非法 INSERT/UPDATE 来实测拒绝**；没有关闭 RLS。全表覆盖、HTTP 身份/仓库权限由其他验收项补充，不从本报告推定已通过。附加检查将事务级公司上下文清空后，批次库存和库存流水可见行数均为 0，确认这两个核心表无上下文时不会返回全部租户数据。

## 9. 本轮状态

可记为通过：当前有效样本现用批次/流水汇总、预留结余、已完整调拨零在途、完成单据与落账关联、库存事件配对和两租户读取隔离的只读核账。

仍需明确处理或补验：遗留库存双模型、B 诊断残留和最终样本口径、动态幂等/并发与异常场景、HTTP 权限、Outbox 发布链、手机设备/证书状态以及生产多租户/部署门禁。因此本报告不单独宣布 A2-4 或上线验收全部完成。

## 10. 工具恢复后的附加只读结果

补充脚本首次写入曾受 Windows sandbox helper 的 `spawn_ready` 超时影响，工具恢复后于 08:15:02 重新执行独立只读查询，没有重跑业务操作或修改数据。

- 12 个批次均满足 `当前批次数量=该批次库存流水净额+质量拆分转入量-质量拆分转出量`。A 批次 1 为 `15-2=13`、批次 8 为 `-1+2=1`，其余批次无拆分净额，全部差异 0。这是现存质量操作行覆盖范围内的逐批平衡，不是全部未来转换类型的通用回放证明。
- 批次启用标志与正库存不一致、预留负余量/超消耗、调拨分配超消耗均为 0。
- 调拨发货/收货行与各自流水 ID、posting ID、批次、数量、IN/OUT 方向不匹配均为 0；发货行累计已收与收货明细之和一致。
- 质检行满足 `计划=待处理+待复核+通过+隔离`，两租户异常行数 0；该恒等关系不会把仍待检的 B 诊断记录当成已通过质检。
- 清空公司上下文后，`inventory_batch` 和 `stock_transactions` 返回 0 行；事务最终 `ROLLBACK`。
