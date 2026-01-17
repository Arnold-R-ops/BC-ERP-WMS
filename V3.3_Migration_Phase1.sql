-- ============================================================================
-- BC ERP-WMS V3.3 Migration Script - Phase 1
-- Database & Entity Layer Migration
-- ============================================================================
--
-- Purpose: Migrate from old version to V3.3 architecture
-- - Create product_spu table (SPU-SKU hierarchy)
-- - Update products table (add spu_id, sku_name, specs)
-- - Update inventory_batch table (add location_code, modify constraints)
-- - Data cleaning (create default SPU, update existing products)
--
-- Database: PostgreSQL 16
-- Execution Order:
-- 1. Create new tables
-- 2. Modify existing tables (add columns)
-- 3. Data cleaning (default SPU + update existing data)
-- 4. Add constraints (FK, indexes)
--
-- Author: WMS Team
-- Date: 2026-01-17
-- Version: 3.3
-- ============================================================================

-- ============================================================================
-- SECTION 1: Create New Tables
-- ============================================================================

-- 1.1 Create product_spu table (Product Family)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_spu (
    id BIGSERIAL PRIMARY KEY,
    spu_code VARCHAR(50) NOT NULL UNIQUE,
    spu_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    brand VARCHAR(100),
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for product_spu
CREATE INDEX IF NOT EXISTS idx_spu_code ON product_spu(spu_code);
CREATE INDEX IF NOT EXISTS idx_spu_name ON product_spu(spu_name);
CREATE INDEX IF NOT EXISTS idx_spu_category ON product_spu(category);

-- Add comments for product_spu table
COMMENT ON TABLE product_spu IS 'Product SPU (Standard Product Unit) - Product Family';
COMMENT ON COLUMN product_spu.spu_code IS 'SPU code (unique identifier)';
COMMENT ON COLUMN product_spu.spu_name IS 'SPU name (product family name)';
COMMENT ON COLUMN product_spu.category IS 'Product category (optional)';
COMMENT ON COLUMN product_spu.brand IS 'Brand name (optional)';
COMMENT ON COLUMN product_spu.description IS 'SPU description (optional)';
COMMENT ON COLUMN product_spu.enabled IS 'Whether SPU is enabled (default: true)';

-- ============================================================================
-- SECTION 2: Modify Existing Tables
-- ============================================================================

-- 2.1 Modify products table (add SPU-SKU fields)
-- ----------------------------------------------------------------------------
-- Add spu_id column (nullable at first, will set NOT NULL after data cleaning)
ALTER TABLE products ADD COLUMN IF NOT EXISTS spu_id BIGINT;

-- Add sku_name column
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku_name VARCHAR(100);

-- Add specs column (JSON or text)
ALTER TABLE products ADD COLUMN IF NOT EXISTS specs VARCHAR(500);

-- Add comments for new columns
COMMENT ON COLUMN products.spu_id IS 'Foreign key to product_spu (Product Family)';
COMMENT ON COLUMN products.sku_name IS 'SKU-specific name (short identifier)';
COMMENT ON COLUMN products.specs IS 'Specification description (JSON or text)';

-- 2.2 Modify inventory_batch table (add location_code, modify constraints)
-- ----------------------------------------------------------------------------
-- Add location_code column (nullable at first, will update later)
ALTER TABLE inventory_batch ADD COLUMN IF NOT EXISTS location_code VARCHAR(50);

-- Add comment for location_code
COMMENT ON COLUMN inventory_batch.location_code IS 'V3.3: Location code string (redundant field for quick query)';

-- Drop old unique constraint on batch_code (if exists)
DO $$
BEGIN
    -- Drop unique constraint on batch_code if exists
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_batch_code' AND conrelid = 'inventory_batch'::regclass
    ) THEN
        ALTER TABLE inventory_batch DROP CONSTRAINT uk_batch_code;
    END IF;

    -- Drop old unique index on batch_code if exists
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_batch_code' AND tablename = 'inventory_batch'
    ) THEN
        DROP INDEX idx_batch_code;
    END IF;
END $$;

-- ============================================================================
-- SECTION 3: Data Cleaning (Critical for FK Constraints)
-- ============================================================================

-- 3.1 Insert Default SPU (ID=0) for existing products
-- ----------------------------------------------------------------------------
-- IMPORTANT: This prevents FK constraint errors when adding NOT NULL constraint on spu_id
INSERT INTO product_spu (id, spu_code, spu_name, category, brand, description, enabled)
VALUES (
    0,
    'DEFAULT-SPU',
    'Default Product Family (Legacy Data)',
    'Uncategorized',
    NULL,
    'Default SPU for existing products before V3.3 migration. Please update product-spu mapping manually.',
    true
)
ON CONFLICT (id) DO NOTHING;  -- Skip if already exists

-- Reset sequence to ensure next auto-generated ID starts from 1
SELECT setval('product_spu_id_seq', (SELECT GREATEST(1, MAX(id)) FROM product_spu), true);

-- 3.2 Update existing products: set spu_id to 0 (default SPU)
-- ----------------------------------------------------------------------------
UPDATE products
SET spu_id = 0
WHERE spu_id IS NULL;

-- 3.3 Update existing products: set sku_name from name (if NULL)
-- ----------------------------------------------------------------------------
UPDATE products
SET sku_name = name
WHERE sku_name IS NULL OR sku_name = '';

-- 3.4 Update inventory_batch: set location_code from location.location_code
-- ----------------------------------------------------------------------------
-- For batches that already have a location assigned, copy location_code
UPDATE inventory_batch ib
SET location_code = l.location_code
FROM locations l
WHERE ib.location_id = l.id
  AND ib.location_code IS NULL;

-- For batches without location (Stage 2: IN_TRANSIT), set temporary placeholder
-- These will be updated when location is assigned in Stage 3
UPDATE inventory_batch
SET location_code = 'PENDING'
WHERE location_code IS NULL AND location_id IS NULL;

-- If still NULL (edge case), set to 'UNASSIGNED'
UPDATE inventory_batch
SET location_code = 'UNASSIGNED'
WHERE location_code IS NULL;

-- ============================================================================
-- SECTION 4: Add Constraints (After Data Cleaning)
-- ============================================================================

-- 4.1 Add NOT NULL constraint on products.spu_id
-- ----------------------------------------------------------------------------
ALTER TABLE products ALTER COLUMN spu_id SET NOT NULL;

-- 4.2 Add NOT NULL constraint on products.sku_name
-- ----------------------------------------------------------------------------
ALTER TABLE products ALTER COLUMN sku_name SET NOT NULL;

-- 4.3 Add NOT NULL constraint on inventory_batch.location_code
-- ----------------------------------------------------------------------------
ALTER TABLE inventory_batch ALTER COLUMN location_code SET NOT NULL;

-- 4.4 Add Foreign Key constraint: products.spu_id -> product_spu.id
-- ----------------------------------------------------------------------------
ALTER TABLE products
ADD CONSTRAINT fk_product_spu
FOREIGN KEY (spu_id)
REFERENCES product_spu(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- 4.5 Add index on products.spu_id (for join performance)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_spu_id ON products(spu_id);

-- 4.6 Create new indexes for inventory_batch (V3.3 multi-location support)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_batch_code ON inventory_batch(batch_code);
CREATE INDEX IF NOT EXISTS idx_batch_location ON inventory_batch(batch_code, location_code);
CREATE INDEX IF NOT EXISTS idx_location_code ON inventory_batch(location_code);

-- ============================================================================
-- SECTION 5: Verification Queries (Optional, for manual check)
-- ============================================================================

-- Check 1: Verify default SPU was created
-- Expected: 1 row with id=0
SELECT * FROM product_spu WHERE id = 0;

-- Check 2: Verify all products have spu_id assigned
-- Expected: 0 rows (no NULL spu_id)
SELECT COUNT(*) AS products_without_spu FROM products WHERE spu_id IS NULL;

-- Check 3: Verify all products have sku_name assigned
-- Expected: 0 rows (no NULL sku_name)
SELECT COUNT(*) AS products_without_sku_name FROM products WHERE sku_name IS NULL;

-- Check 4: Count products by SPU
SELECT
    spu_id,
    COUNT(*) AS product_count
FROM products
GROUP BY spu_id
ORDER BY product_count DESC;

-- Check 5: Verify inventory_batch table structure and data
SELECT
    CASE
        WHEN location_code = 'PENDING' THEN 'In Transit (No Location)'
        WHEN location_code = 'UNASSIGNED' THEN 'Unassigned (Edge Case)'
        ELSE 'Received (Has Location)'
    END AS status,
    COUNT(*) AS batch_count
FROM inventory_batch
GROUP BY status;

-- Check 6: Verify no NULL location_code in inventory_batch
SELECT COUNT(*) AS batches_without_location_code
FROM inventory_batch
WHERE location_code IS NULL;

-- ============================================================================
-- SECTION 6: Migration Summary
-- ============================================================================

SELECT
    'V3.3 Migration Completed!' AS status,
    (SELECT COUNT(*) FROM product_spu) AS total_spus,
    (SELECT COUNT(*) FROM products) AS total_skus,
    (SELECT COUNT(*) FROM inventory_batch) AS total_batches,
    CURRENT_TIMESTAMP AS completed_at;

-- ============================================================================
-- SECTION 7: Rollback Script (Use with caution!)
-- ============================================================================
-- Uncomment below to rollback this migration
-- WARNING: This will lose all SPU data and product-spu mappings!

/*
-- Rollback Step 1: Drop FK constraint
ALTER TABLE products DROP CONSTRAINT IF EXISTS fk_product_spu;

-- Rollback Step 2: Drop indexes
DROP INDEX IF EXISTS idx_spu_id;
DROP INDEX IF EXISTS idx_batch_code;
DROP INDEX IF EXISTS idx_batch_location;
DROP INDEX IF EXISTS idx_location_code;

-- Rollback Step 3: Drop columns from products
ALTER TABLE products DROP COLUMN IF EXISTS spu_id;
ALTER TABLE products DROP COLUMN IF EXISTS sku_name;
ALTER TABLE products DROP COLUMN IF EXISTS specs;

-- Rollback Step 4: Drop columns from inventory_batch
ALTER TABLE inventory_batch DROP COLUMN IF EXISTS location_code;

-- Rollback Step 5: Drop product_spu table
DROP TABLE IF EXISTS product_spu CASCADE;

-- Rollback Step 6: Restore unique constraint on batch_code (if needed)
-- Note: Only if you want to restore old architecture
-- CREATE UNIQUE INDEX idx_batch_code ON inventory_batch(batch_code);

SELECT 'V3.3 Migration Rolled Back!' AS status, CURRENT_TIMESTAMP AS completed_at;
*/

-- ============================================================================
-- Migration Complete!
-- ============================================================================
-- Next Steps:
-- 1. Run this script in your PostgreSQL database
-- 2. Verify data integrity with Section 5 queries
-- 3. Update Java entity classes (ProductSpu.java, Product.java)
-- 4. Test application startup (JPA should auto-detect schema changes)
-- 5. Manually update product-spu mappings (change spu_id from 0 to actual SPU)
-- ============================================================================
