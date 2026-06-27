-- ============================================================================
-- WMS Phase 3.4: Warehouse-Location Refactoring Migration (PostgreSQL)
-- SAFE VERSION - Can be re-run without errors
-- ============================================================================

-- ============================================================================
-- Step 1: Create warehouses table (with IF NOT EXISTS)
-- ============================================================================
CREATE TABLE IF NOT EXISTS warehouses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    contact VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add comments (safe to re-run)
COMMENT ON TABLE warehouses IS 'Warehouse master data table';
COMMENT ON COLUMN warehouses.code IS 'Warehouse business code (e.g., WH01)';
COMMENT ON COLUMN warehouses.name IS 'Warehouse name';
COMMENT ON COLUMN warehouses.address IS 'Warehouse address';
COMMENT ON COLUMN warehouses.contact IS 'Contact person or phone';
COMMENT ON COLUMN warehouses.is_active IS 'Active status';
COMMENT ON COLUMN warehouses.created_at IS 'Creation timestamp';
COMMENT ON COLUMN warehouses.updated_at IS 'Last update timestamp';

-- Create indexes (with IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_warehouse_code ON warehouses(code);
CREATE INDEX IF NOT EXISTS idx_warehouse_active ON warehouses(is_active);

-- ============================================================================
-- Step 2: Extract unique warehouse codes from locations (skip if data exists)
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM warehouses LIMIT 1) THEN
        INSERT INTO warehouses (code, name, is_active, created_at, updated_at)
        SELECT DISTINCT
            warehouse_code AS code,
            CONCAT('Warehouse ', warehouse_code) AS name,
            TRUE AS is_active,
            NOW() AS created_at,
            NOW() AS updated_at
        FROM locations
        WHERE warehouse_code IS NOT NULL
        ORDER BY warehouse_code;

        RAISE NOTICE 'Warehouses created from existing location data';
    ELSE
        RAISE NOTICE 'Warehouses table already has data, skipping insert';
    END IF;
END $$;

-- Verification: Check created warehouses
SELECT 'Created warehouses:' AS step, COUNT(*) AS count FROM warehouses;
SELECT * FROM warehouses ORDER BY code;

-- ============================================================================
-- Step 3: Add warehouse_id column to locations table (if not exists)
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'locations' AND column_name = 'warehouse_id'
    ) THEN
        ALTER TABLE locations ADD COLUMN warehouse_id BIGINT;
        RAISE NOTICE 'Added warehouse_id column to locations table';
    ELSE
        RAISE NOTICE 'warehouse_id column already exists in locations table';
    END IF;
END $$;

-- Add comment
COMMENT ON COLUMN locations.warehouse_id IS 'Foreign key to warehouses table';

-- Add index for foreign key performance (with IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_location_warehouse_id ON locations(warehouse_id);

-- ============================================================================
-- Step 4: Populate warehouse_id (only if NULL values exist)
-- ============================================================================
DO $$
DECLARE
    null_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO null_count FROM locations WHERE warehouse_id IS NULL;

    IF null_count > 0 THEN
        UPDATE locations l
        SET warehouse_id = w.id
        FROM warehouses w
        WHERE l.warehouse_code = w.code
        AND l.warehouse_id IS NULL;

        RAISE NOTICE 'Updated % locations with warehouse_id', null_count;
    ELSE
        RAISE NOTICE 'All locations already have warehouse_id, skipping update';
    END IF;
END $$;

-- Verification: Check all locations have warehouse_id populated
SELECT 'Locations with NULL warehouse_id:' AS step, COUNT(*) AS count
FROM locations
WHERE warehouse_id IS NULL;

-- ============================================================================
-- Step 5: Add NOT NULL constraint and foreign key constraint (if not exists)
-- ============================================================================
DO $$
BEGIN
    -- Check if any locations still have NULL warehouse_id
    IF EXISTS (SELECT 1 FROM locations WHERE warehouse_id IS NULL) THEN
        RAISE EXCEPTION 'Migration failed: Some locations have NULL warehouse_id';
    END IF;

    -- Add NOT NULL constraint if not already set
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'locations'
        AND column_name = 'warehouse_id'
        AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE locations ALTER COLUMN warehouse_id SET NOT NULL;
        RAISE NOTICE 'Added NOT NULL constraint to warehouse_id';
    ELSE
        RAISE NOTICE 'warehouse_id already has NOT NULL constraint';
    END IF;

    -- Add foreign key constraint if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_location_warehouse'
    ) THEN
        ALTER TABLE locations
        ADD CONSTRAINT fk_location_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;
        RAISE NOTICE 'Added foreign key constraint fk_location_warehouse';
    ELSE
        RAISE NOTICE 'Foreign key constraint fk_location_warehouse already exists';
    END IF;
END $$;

-- ============================================================================
-- Step 6: Update warehouseCode column comment
-- ============================================================================
COMMENT ON COLUMN locations.warehouse_code IS 'Warehouse code (redundant, synced from warehouse.code for performance)';

-- ============================================================================
-- Step 7: Verification queries
-- ============================================================================

-- Verify all locations have valid warehouse_id
SELECT 'Final verification - Locations with NULL warehouse_id:' AS step,
       COUNT(*) AS count
FROM locations
WHERE warehouse_id IS NULL;

-- Verify warehouseCode matches warehouse.code
SELECT 'Verification - warehouseCode sync check:' AS step,
       COUNT(*) AS mismatched_count
FROM locations l
INNER JOIN warehouses w ON l.warehouse_id = w.id
WHERE l.warehouse_code != w.code;

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
RAISE NOTICE '✅ Migration V3_4 completed successfully!';
