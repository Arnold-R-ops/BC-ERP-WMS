-- =====================================================
-- V3.6 三层穿透式库存视图 - 数据库变更
-- =====================================================
-- 功能：支持智能数量显示、有效期排序、库存预警
-- 作者：WMS Team
-- 日期：2026-01-28
-- =====================================================

-- 1. products 表：新增 safety_stock 字段
ALTER TABLE products ADD COLUMN IF NOT EXISTS safety_stock INT NOT NULL DEFAULT 0;
COMMENT ON COLUMN products.safety_stock IS '安全库存（预警阈值）';
CREATE INDEX IF NOT EXISTS idx_product_safety_stock ON products(safety_stock);

-- 注意：inventory_batch 和 inbound_order_items 表已有 expiry_date 字段，无需新增
-- 直接使用现有的 expiry_date 字段即可
-- inventory_batch.expiry_date 已存在，已有索引 idx_product_expiry_active
-- inbound_order_items.expiry_date 已存在，已有索引 idx_inbound_item_expiry
