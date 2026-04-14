-- =====================================================
-- V4.2.1 - AI 数据策略优化 (AI Data Strategy Refinement)
-- =====================================================
-- 功能说明：
-- 1. 移除交易单据的 is_deleted 字段（改用状态机）
-- 2. 为交易单据状态枚举新增 CANCELLED 和 VOIDED
-- 3. 保留主数据的 is_deleted 字段（customers, products, warehouses, locations）
--
-- 设计原则：
-- - 主数据（Master Data）：维持逻辑删除（is_deleted）
-- - 交易单据（Transactional Data）：使用状态机区分业务失败与数据噪音
--   * CANCELLED：业务取消（真实失败，AI 学习）
--   * VOIDED：系统作废（数据噪音，AI 过滤）
-- - 草稿期允许物理删除，生效期只能变更状态
--
-- @author WMS Team
-- @since 2026-03-11
-- @version 4.2.1 (AI Data Strategy Refinement)
-- =====================================================


-- =====================================================
-- Part 1: 移除交易单据的 is_deleted 字段
-- =====================================================
-- 说明：
-- - 交易单据通过状态机管理生命周期，不需要 is_deleted
-- - 状态 CANCELLED 和 VOIDED 替代逻辑删除功能
-- =====================================================

-- 1.1 销售订单表：移除 is_deleted
ALTER TABLE sales_orders
    DROP COLUMN is_deleted;

DROP INDEX IF EXISTS idx_sales_orders_not_deleted ON sales_orders;

-- 1.2 采购单表：移除 is_deleted
ALTER TABLE purchase_order
    DROP COLUMN is_deleted;

DROP INDEX IF EXISTS idx_purchase_order_not_deleted ON purchase_order;


-- =====================================================
-- Part 2: 更新状态枚举值说明
-- =====================================================
-- 说明：
-- - 数据库层面不需要修改（MySQL ENUM 在 JPA 中使用 VARCHAR 存储）
-- - 新增的 CANCELLED 和 VOIDED 状态将由应用层（Java Enum）管理
-- - 现有数据不受影响，新状态值可以直接写入
-- =====================================================

-- 状态说明（仅文档用途）：
--
-- sales_orders.status:
--   - DRAFT: 草稿（可物理删除）
--   - PENDING_APPROVAL: 待审批
--   - APPROVED_AWAITING_SHIPMENT: 已批准待发货
--   - SHIPPED: 已发货
--   - REJECTED: 已拒绝
--   - CANCELLED: 已取消（业务失败，AI 学习）
--   - VOIDED: 已作废（数据噪音，AI 过滤）
--
-- purchase_order.status:
--   - ORDERING: 下单中（可物理删除）
--   - IN_TRANSIT: 待入库
--   - PARTIALLY_RECEIVED: 部分收货
--   - COMPLETED: 已入库
--   - CANCELLED: 已取消（业务失败，AI 学习）
--   - VOIDED: 已作废（数据噪音，AI 过滤）
--
-- inbound_orders.status:
--   - PENDING_APPROVAL: 待总经理审批（可物理删除）
--   - APPROVED_PLAN: 总经理已批，待采购员确认
--   - AWAITING_RECEIVAL: 已确认，待仓库收货
--   - COMPLETED: 完成
--   - REJECTED: 拒绝
--   - CANCELLED: 已取消（业务失败，AI 学习）
--   - VOIDED: 已作废（数据噪音，AI 过滤）
--
-- stocktake_tasks.status:
--   - CREATED: 已创建（可物理删除）
--   - COUNTING: 盘点中
--   - REVIEWING: 审核中
--   - COMPLETED: 已完成
--   - CANCELLED: 已取消（业务失败，AI 学习）
--   - VOIDED: 已作废（数据噪音，AI 过滤）


-- =====================================================
-- Part 3: 保留主数据的 is_deleted 字段
-- =====================================================
-- 说明：
-- - 主数据（customers, products, warehouses, locations）保留 is_deleted
-- - 主数据可能被历史订单关联，不能物理删除
-- - 逻辑删除确保数据完整性和历史追溯
-- =====================================================

-- 主数据表保留 is_deleted 字段（无需修改）：
-- - customers.is_deleted
-- - products.is_deleted
-- - warehouses.is_deleted (如果存在)
-- - locations.is_deleted (如果存在)


-- =====================================================
-- 迁移完成
-- =====================================================
-- 影响表汇总：
-- - sales_orders       -1 字段（移除 is_deleted）
-- - purchase_order     -1 字段（移除 is_deleted）
-- - customers          保留 is_deleted
-- - products           保留 is_deleted
-- =====================================================
