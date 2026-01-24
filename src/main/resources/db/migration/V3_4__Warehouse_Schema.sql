-- ============================================================================
-- WMS Phase 3.4: Warehouse-Location Refactoring Migration (PostgreSQL)
-- ============================================================================
-- Purpose: Refactor Location entity to use proper foreign key relationship
--          with a new Warehouse entity, replacing String-based warehousecode
--          with normalized database design.
--
-- Strategy: KEEP warehousecode as redundant denormalized field for:
--           - Performance optimization (avoid JOINs)
--           - Index efficiency (existing composite unique constraint)
--           - Backward compatibility
--           - Auto-sync via @PrePersist/@PreUpdate lifecycle callbacks
-- ============================================================================

-- ============================================================================
-- Step 1: Create warehouses table
-- ============================================================================
CREATE TABLE warehouses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    contact VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add comments
COMMENT ON TABLE warehouses IS 'Warehouse master data table';
COMMENT ON COLUMN warehouses.code IS 'Warehouse business code (e.g., WH01)';
COMMENT ON COLUMN warehouses.name IS 'Warehouse name';
COMMENT ON COLUMN warehouses.address IS 'Warehouse address';
COMMENT ON COLUMN warehouses.contact IS 'Contact person or phone';
COMMENT ON COLUMN warehouses.is_active IS 'Active status';
COMMENT ON COLUMN warehouses.created_at IS 'Creation timestamp';
COMMENT ON COLUMN warehouses.updated_at IS 'Last update timestamp';

-- Create indexes
CREATE INDEX idx_warehouse_code ON warehouses(code);
CREATE INDEX idx_warehouse_active ON warehouses(is_active);

-- ============================================================================
-- Step 2: Extract unique warehouse codes from locations and create warehouse records
-- ============================================================================
INSERT INTO warehouses (code, name, is_active, created_at, updated_at)
SELECT DISTINCT
    warehousecode AS code,
    CONCAT('Warehouse ', warehousecode) AS name,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at
FROM locations
WHERE warehousecode IS NOT NULL
ORDER BY warehousecode;

-- Verification: Check created warehouses
SELECT 'Created warehouses:' AS step, COUNT(*) AS count FROM warehouses;
SELECT * FROM warehouses ORDER BY code;

-- ============================================================================
-- Step 3: Add warehouse_id column to locations table (nullable first)
-- ============================================================================
ALTER TABLE locations
ADD COLUMN warehouse_id BIGINT;

-- Add comment
COMMENT ON COLUMN locations.warehouse_id IS 'Foreign key to warehouses table';

-- Add index for foreign key performance
CREATE INDEX idx_location_warehouse_id ON locations(warehouse_id);

-- ============================================================================
-- Step 4: Populate warehouse_id by matching warehousecode with warehouses.code
-- ============================================================================
UPDATE locations l
SET warehouse_id = w.id
FROM warehouses w
WHERE l.warehousecode = w.code;

-- Verification: Check all locations have warehouse_id populated
SELECT 'Locations with NULL warehouse_id:' AS step, COUNT(*) AS count
FROM locations
WHERE warehouse_id IS NULL;

-- If any locations have NULL warehouse_id, this indicates data inconsistency
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM locations WHERE warehouse_id IS NULL) > 0 THEN
        RAISE EXCEPTION 'Migration failed: Some locations have NULL warehouse_id';
    END IF;
END $$;

-- ============================================================================
-- Step 5: Add NOT NULL constraint and foreign key constraint
-- ============================================================================
ALTER TABLE locations
ALTER COLUMN warehouse_id SET NOT NULL;

ALTER TABLE locations
ADD CONSTRAINT fk_location_warehouse
FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- ============================================================================
-- Step 6: Keep warehousecode column (no removal)
-- ============================================================================
-- Note: warehousecode column is intentionally kept as a redundant denormalized field
-- It will be auto-synced from warehouse.code via @PrePersist/@PreUpdate in the entity
-- This provides:
-- 1. Performance optimization (avoid JOINs in frequently-used queries)
-- 2. Index efficiency (existing composite unique constraint relies on it)
-- 3. Backward compatibility with existing queries

-- Update column comment to reflect new redundancy strategy
COMMENT ON COLUMN locations.warehousecode IS 'Warehouse code (redundant, synced from warehouse.code for performance)';

-- ============================================================================
-- Step 7: Verification queries
-- ============================================================================

-- Verify all locations have valid warehouse_id
SELECT 'Final verification - Locations with NULL warehouse_id:' AS step,
       COUNT(*) AS count
FROM locations
WHERE warehouse_id IS NULL;

-- Verify warehousecode matches warehouse.code
SELECT 'Verification - warehousecode sync check:' AS step,
       COUNT(*) AS mismatched_count
FROM locations l
INNER JOIN warehouses w ON l.warehouse_id = w.id
WHERE l.warehousecode != w.code;

-- Verify foreign key constraint exists
SELECT 'Foreign key constraint verification:' AS step,
       conname AS constraint_name,
       conrelid::regclass AS table_name,
       confrelid::regclass AS referenced_table_name
FROM pg_constraint
WHERE conname = 'fk_location_warehouse';

-- Display summary statistics
SELECT 'Migration Summary:' AS step,
       (SELECT COUNT(*) FROM warehouses) AS total_warehouses,
       (SELECT COUNT(*) FROM locations) AS total_locations,
       (SELECT COUNT(*) FROM locations WHERE warehouse_id IS NOT NULL) AS locations_with_warehouse;

-- ============================================================================
-- Migration Complete
-- ============================================================================
