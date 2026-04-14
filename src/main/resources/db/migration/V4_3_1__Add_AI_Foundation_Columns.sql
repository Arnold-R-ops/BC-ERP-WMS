-- =====================================================
-- V4.3.1 Add AI Foundation Columns
-- =====================================================
-- Purpose:
-- 1) Backfill AI foundation columns introduced in entities.
-- 2) Keep existing rows valid with safe defaults.
-- 3) Make the migration idempotent for local repair and new environments.
-- =====================================================

BEGIN;

-- -----------------------------------------------------
-- products: soft delete and AI inventory support fields
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
-- warehouses: align with current Hibernate validation
-- -----------------------------------------------------
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS isactive BOOLEAN;
UPDATE warehouses
SET isactive = COALESCE(is_active, TRUE)
WHERE isactive IS NULL;
ALTER TABLE warehouses ALTER COLUMN isactive SET DEFAULT TRUE;
ALTER TABLE warehouses ALTER COLUMN isactive SET NOT NULL;

COMMIT;
