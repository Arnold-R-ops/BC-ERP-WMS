-- V4.5: Add phone column to warehouses table
-- Business reason: Warehouse contact phone is a required field for operations
-- Author: WMS Team | Date: 2026-03-19
-- DB: PostgreSQL

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

COMMENT ON COLUMN warehouses.phone IS '仓库联系电话';
