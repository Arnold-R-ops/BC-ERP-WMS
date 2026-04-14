-- =====================================================
-- V4.4 架构加固：乐观锁与幂等性防御
-- =====================================================
-- Purpose:
-- 1) 防御一：乐观锁与防超卖机制 (Optimistic Locking)
--    - 为 inventory_batch 和 products 表添加 version 字段
--    - 激活 JPA @Version 注解的底层乐观锁机制
--
-- 2) 防御二：物理级接口幂等性 (Database-level Idempotency)
--    - 为核心单据表的业务单号字段建立唯一索引
--    - 防止 Shopify 网络抖动重发和前端连击导致的重复提交
--
-- Target Tables:
-- - inventory_batch: 库存批次表（高并发扣减核心）
-- - products: 商品主表（价格并发更新）
-- - sales_orders: 销售订单表（order_no 唯一约束）
-- - purchase_order: 采购单表（po_number 唯一约束）
-- - inbound_orders: 入库单表（order_no 唯一约束）
--
-- Note:
-- - 所有语句幂等（IF NOT EXISTS / IF EXISTS）
-- - 兼容 PostgreSQL
-- =====================================================

BEGIN;

-- =====================================================
-- 防御一：乐观锁字段 (version)
-- =====================================================

-- inventory_batch: 添加 version 字段
ALTER TABLE inventory_batch ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0 NOT NULL;

-- products: 添加 version 字段
ALTER TABLE products ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0 NOT NULL;

-- =====================================================
-- 防御二：唯一索引（幂等性保障）
-- =====================================================

-- sales_orders: order_no 唯一索引（已存在，确保存在）
-- 注意：根据 SalesOrder.java 第54行，idx_sales_order_no 已经是 unique = true
-- 此处仅做防御性检查，如果不存在则创建
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'sales_orders'
        AND indexname = 'idx_sales_order_no'
    ) THEN
        CREATE UNIQUE INDEX idx_sales_order_no ON sales_orders(order_no);
    END IF;
END $$;

-- purchase_order: po_number 唯一索引（已存在，确保存在）
-- 注意：根据 PurchaseOrder.java 第32行，idx_po_number 已经是 unique = true
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'purchase_order'
        AND indexname = 'idx_po_number'
    ) THEN
        CREATE UNIQUE INDEX idx_po_number ON purchase_order(po_number);
    END IF;
END $$;

-- inbound_orders: order_no 唯一索引（已存在，确保存在）
-- 注意：根据 InboundOrder.java 第58行，order_no 已经是 unique = true
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'inbound_orders'
        AND indexname = 'idx_inbound_order_no'
    ) THEN
        CREATE UNIQUE INDEX idx_inbound_order_no ON inbound_orders(order_no);
    END IF;
END $$;

-- =====================================================
-- V4.4 架构加固完成
-- =====================================================
-- 变更摘要：
-- 1. inventory_batch.version: 乐观锁字段（防超卖）
-- 2. products.version: 乐观锁字段（防价格并发冲突）
-- 3. 确保三张核心单据表的业务单号唯一索引存在
--
-- 下一步：
-- 1. 修改 Java Entity 类，添加 @Version 注解
-- 2. 在 GlobalExceptionHandler 中拦截 DataIntegrityViolationException
-- =====================================================

COMMIT;
