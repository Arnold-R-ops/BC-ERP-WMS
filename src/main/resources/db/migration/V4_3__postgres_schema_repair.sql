-- =====================================================
-- V4.3 PostgreSQL Schema Repair
-- =====================================================
-- Purpose:
-- 1) Backfill newly-added NOT NULL columns on existing data.
-- 2) Add safe defaults before enforcing NOT NULL.
-- 3) Align index names with entity annotations (globally unique names).
--
-- Note:
-- - All statements are idempotent.
-- - Compatible with PostgreSQL.
-- =====================================================

BEGIN;

-- -----------------------------------------------------
-- products: backfill columns and enforce NOT NULL
-- -----------------------------------------------------
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
UPDATE products SET is_deleted = FALSE WHERE is_deleted IS NULL;
ALTER TABLE products ALTER COLUMN is_deleted SET DEFAULT FALSE;
ALTER TABLE products ALTER COLUMN is_deleted SET NOT NULL;

ALTER TABLE products ADD COLUMN IF NOT EXISTS near_expiry_days INTEGER;
UPDATE products SET near_expiry_days = 90 WHERE near_expiry_days IS NULL;
ALTER TABLE products ALTER COLUMN near_expiry_days SET DEFAULT 90;
ALTER TABLE products ALTER COLUMN near_expiry_days SET NOT NULL;

ALTER TABLE products ADD COLUMN IF NOT EXISTS per_pack_qty INTEGER;
UPDATE products SET per_pack_qty = 1 WHERE per_pack_qty IS NULL;
ALTER TABLE products ALTER COLUMN per_pack_qty SET DEFAULT 1;
ALTER TABLE products ALTER COLUMN per_pack_qty SET NOT NULL;

ALTER TABLE products ADD COLUMN IF NOT EXISTS safety_stock INTEGER;
UPDATE products SET safety_stock = 0 WHERE safety_stock IS NULL;
ALTER TABLE products ALTER COLUMN safety_stock SET DEFAULT 0;
ALTER TABLE products ALTER COLUMN safety_stock SET NOT NULL;

-- -----------------------------------------------------
-- warehouses: backfill isactive and enforce NOT NULL
-- -----------------------------------------------------
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS isactive BOOLEAN;
UPDATE warehouses SET isactive = TRUE WHERE isactive IS NULL;
ALTER TABLE warehouses ALTER COLUMN isactive SET DEFAULT TRUE;
ALTER TABLE warehouses ALTER COLUMN isactive SET NOT NULL;

-- -----------------------------------------------------
-- Rename old indexes if they belong to expected tables
-- -----------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'inventory'
          AND indexname = 'idx_product_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_product_id RENAME TO idx_inventory_product_id';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'inventory'
          AND indexname = 'idx_location_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_location_id RENAME TO idx_inventory_location_id';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'stock_transactions'
          AND indexname = 'idx_product_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_product_id RENAME TO idx_stock_tx_product_id';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'stock_transactions'
          AND indexname = 'idx_location_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_location_id RENAME TO idx_stock_tx_location_id';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'stock_transactions'
          AND indexname = 'idx_created_at'
    ) THEN
        EXECUTE 'ALTER INDEX idx_created_at RENAME TO idx_stock_tx_created_at';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'purchase_order_item'
          AND indexname = 'idx_product_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_product_id RENAME TO idx_po_item_product_id';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'locations'
          AND indexname = 'idx_warehouse_code'
    ) THEN
        EXECUTE 'ALTER INDEX idx_warehouse_code RENAME TO idx_location_warehouse_code';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'purchase_order'
          AND indexname = 'idx_created_at'
    ) THEN
        EXECUTE 'ALTER INDEX idx_created_at RENAME TO idx_purchase_order_created_at';
    END IF;
END$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'inventory_batch'
          AND indexname = 'idx_location_id'
    ) THEN
        EXECUTE 'ALTER INDEX idx_location_id RENAME TO idx_inventory_batch_location_id';
    END IF;
END$$;

-- -----------------------------------------------------
-- Ensure target index names exist
-- -----------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_inventory_product_id ON inventory (product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_location_id ON inventory (location_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_product_id ON stock_transactions (product_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_location_id ON stock_transactions (location_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_created_at ON stock_transactions (created_at);
CREATE INDEX IF NOT EXISTS idx_po_item_product_id ON purchase_order_item (product_id);
CREATE INDEX IF NOT EXISTS idx_location_warehouse_code ON locations (warehouseCode);
CREATE INDEX IF NOT EXISTS idx_purchase_order_created_at ON purchase_order (created_at);
CREATE INDEX IF NOT EXISTS idx_inventory_batch_location_id ON inventory_batch (location_id);

COMMIT;

