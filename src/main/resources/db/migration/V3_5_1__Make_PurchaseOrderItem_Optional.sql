-- V3.5.1: Make purchase_order_item_id optional in inventory_batch
--
-- Purpose: Support inbound order system which doesn't depend on purchase orders
--
-- Changes:
-- 1. Make purchase_order_item_id nullable
-- 2. Drop NOT NULL constraint

-- Make purchase_order_item_id nullable
ALTER TABLE inventory_batch
ALTER COLUMN purchase_order_item_id DROP NOT NULL;

-- Add comment to explain the change
COMMENT ON COLUMN inventory_batch.purchase_order_item_id IS
'Optional reference to purchase order item. NULL for inbound orders that are not linked to purchase orders (V3.5+)';
