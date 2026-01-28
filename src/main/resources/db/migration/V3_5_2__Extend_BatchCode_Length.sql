-- V3.5.2: Extend batch_code length to support SPU-SKU-DATE format
--
-- Purpose: Support new batch code format (SPU001-SKU001-20260126)
--
-- Changes:
-- 1. Extend batch_code from VARCHAR(20) to VARCHAR(50)
-- 2. Update comment to reflect new format support

-- Extend batch_code column length
ALTER TABLE inventory_batch
ALTER COLUMN batch_code TYPE VARCHAR(50);

-- Add comment to explain the change
COMMENT ON COLUMN inventory_batch.batch_code IS
'System batch code. Supports both Hashids format (6 chars, e.g., R7M4K9) and SPU-SKU-DATE format (e.g., SPU001-SKU001-20260126). V3.5: Extended to VARCHAR(50) to accommodate longer formats.';
