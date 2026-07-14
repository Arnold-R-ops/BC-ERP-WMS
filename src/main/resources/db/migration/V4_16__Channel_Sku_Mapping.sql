-- ====================================================================
-- V4.16: Channel SKU mapping (P1 batch 2)
--
-- Industry-standard four-layer SKU resolution:
-- 1. channel_sku_mapping: the memory layer - (channel, external_sku)
--    -> internal product, with quantity ratio (1 external unit = N
--    internal units) and VIRTUAL type for non-stock lines (custom
--    items / service fees). Learned once, applied forever.
-- 2. pending_sku_mapping: the human-in-the-loop queue - unknown SKUs
--    land here instead of being silently dropped; occurrence counters
--    give ops a priority signal. Resolving creates a mapping and the
--    blocked orders auto-replay on the next sync cycle (raw events
--    with FAILED status are retried by design, see P1 batch 1).
-- Lines WITHOUT any SKU use a synthetic key: 'NOSKU::' + line title.
--
-- Both tables are channel-generic (SHOPIFY today, AMAZON later).
-- ====================================================================

CREATE TABLE IF NOT EXISTS channel_sku_mapping (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    channel VARCHAR(50) NOT NULL,
    store_identifier VARCHAR(255),
    external_sku VARCHAR(200) NOT NULL,
    normalized_sku VARCHAR(200) NOT NULL,
    mapping_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    product_id BIGINT REFERENCES products(id),
    quantity_ratio INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sku_mapping UNIQUE (company_id, channel, external_sku),
    CONSTRAINT chk_sku_mapping_ratio CHECK (quantity_ratio >= 1),
    CONSTRAINT chk_sku_mapping_product CHECK (
        (mapping_type = 'PRODUCT' AND product_id IS NOT NULL)
        OR mapping_type = 'VIRTUAL'
    )
);

COMMENT ON TABLE channel_sku_mapping IS 'Channel SKU -> internal product mapping (memory layer); VIRTUAL = non-stock line, excluded from fulfillment';
COMMENT ON COLUMN channel_sku_mapping.normalized_sku IS 'Uppercased, whitespace-stripped external_sku for fuzzy lookup';
COMMENT ON COLUMN channel_sku_mapping.quantity_ratio IS '1 external unit = N internal units (e.g. a 20KG listing selling a 20-unit bulk pack)';
COMMENT ON COLUMN channel_sku_mapping.source IS 'MANUAL (ops confirmed) / AUTO (learned from barcode match)';

CREATE INDEX IF NOT EXISTS idx_sku_mapping_normalized ON channel_sku_mapping (company_id, channel, normalized_sku);

CREATE TABLE IF NOT EXISTS pending_sku_mapping (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    channel VARCHAR(50) NOT NULL,
    store_identifier VARCHAR(255),
    external_sku VARCHAR(200) NOT NULL,
    external_title VARCHAR(500),
    sample_unit_price NUMERIC(12, 2),
    sample_external_order_no VARCHAR(100),
    occurrence_count INT NOT NULL DEFAULT 1,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution VARCHAR(20),
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pending_sku UNIQUE (company_id, channel, external_sku)
);

COMMENT ON TABLE pending_sku_mapping IS 'Unknown channel SKUs awaiting human mapping (replaces silent order drop); NOSKU:: prefix marks lines without any SKU';
COMMENT ON COLUMN pending_sku_mapping.occurrence_count IS 'How many times this SKU has blocked an order - ops priority signal';
COMMENT ON COLUMN pending_sku_mapping.resolution IS 'MAPPED / VIRTUAL / IGNORED (set when status leaves PENDING)';

CREATE INDEX IF NOT EXISTS idx_pending_sku_status ON pending_sku_mapping (company_id, channel, status);

-- Runtime policy for order lines that carry no SKU at all:
-- PENDING (default) = block the order into the pending queue for a human;
-- SKIP = drop the line but keep the order (amount noted in remark).
INSERT INTO system_config (config_key, config_value, config_type, description, company_id, created_at, updated_at)
SELECT 'integration.no_sku_line_policy', 'PENDING', 'STRING',
       'How channel order lines without SKU are handled: PENDING (queue for human) / SKIP (drop line, keep order)',
       1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM system_config WHERE config_key = 'integration.no_sku_line_policy'
);
