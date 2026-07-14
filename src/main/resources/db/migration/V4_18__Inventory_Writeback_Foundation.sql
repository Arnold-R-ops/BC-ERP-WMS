-- ====================================================================
-- V4.18: Inventory writeback foundation (P1 batch 4, DORMANT feature)
--
-- Purpose: push internal ATP (on-hand minus reserved) to the channel
-- (Shopify inventory_levels/set) so the storefront cannot oversell.
-- The dispatcher is gated behind
--   wms.integration.shopify.inventory-writeback-enabled (default OFF)
-- - schema and code ship now, activation happens after deployment.
--
-- 1. integration_configs.shopify_location_id: Shopify keeps stock per
--    location; pushes must name one. Resolvable via the detect-location
--    admin endpoint (requires read_locations scope on the app).
-- 2. channel_sku_mapping.external_item_ref: Shopify addresses stock by
--    numeric inventory_item_id, not by SKU string. Cached per mapping,
--    lazily resolved from the product catalog (read_products scope).
-- 3. channel_inventory_state: last successfully pushed value per
--    mapping - the differential sweep only pushes changes.
-- ====================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'integration_configs' AND column_name = 'shopify_location_id'
    ) THEN
        ALTER TABLE integration_configs ADD COLUMN shopify_location_id VARCHAR(50);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'channel_sku_mapping' AND column_name = 'external_item_ref'
    ) THEN
        ALTER TABLE channel_sku_mapping ADD COLUMN external_item_ref VARCHAR(100);
    END IF;
END $$;

COMMENT ON COLUMN integration_configs.shopify_location_id IS 'Shopify location id for inventory_levels/set (P1-B4); auto-detectable via /detect-location';
COMMENT ON COLUMN channel_sku_mapping.external_item_ref IS 'Channel-side inventory item reference (Shopify inventory_item_id), lazily resolved from catalog (P1-B4)';

CREATE TABLE IF NOT EXISTS channel_inventory_state (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    channel VARCHAR(50) NOT NULL,
    mapping_id BIGINT NOT NULL REFERENCES channel_sku_mapping(id) ON DELETE CASCADE,
    last_pushed_available INT,
    last_pushed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'OK',
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_state_mapping UNIQUE (mapping_id)
);

COMMENT ON TABLE channel_inventory_state IS 'Last pushed inventory level per channel SKU mapping - differential writeback state (P1-B4)';
