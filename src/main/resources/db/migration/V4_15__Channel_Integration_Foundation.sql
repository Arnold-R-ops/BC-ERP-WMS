-- ====================================================================
-- V4.15: Channel integration foundation (P1 batch 1)
--
-- 1. integration_configs: support OAuth client-credentials auth
--    (2026 Shopify dev-dashboard apps issue Client ID + Secret; access
--    tokens are short-lived and fetched at runtime, so the static
--    access_token column becomes optional/legacy).
-- 2. channel_raw_events: raw payload landing table. Every payload
--    pulled from (or later pushed by) a sales channel is persisted
--    BEFORE processing, with per-order status, so failures can be
--    diagnosed and replayed, and daily reconciliation has raw material.
--    Channel-generic by design (SHOPIFY today, AMAZON etc. later).
-- ====================================================================

-- 1. integration_configs: client-credentials columns
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'integration_configs' AND column_name = 'client_id'
    ) THEN
        ALTER TABLE integration_configs ADD COLUMN client_id VARCHAR(100);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'integration_configs' AND column_name = 'client_secret'
    ) THEN
        ALTER TABLE integration_configs ADD COLUMN client_secret VARCHAR(200);
    END IF;
END $$;

-- access_token becomes optional: legacy static-token mode only
ALTER TABLE integration_configs ALTER COLUMN access_token DROP NOT NULL;

COMMENT ON COLUMN integration_configs.client_id IS 'OAuth app Client ID (2026 dev-dashboard apps); used with client_secret to fetch short-lived access tokens at runtime';
COMMENT ON COLUMN integration_configs.client_secret IS 'OAuth app Client Secret (shpss_...); also the webhook HMAC key. Stored plaintext for now - credential ladder upgrade planned (see ROADMAP P3)';
COMMENT ON COLUMN integration_configs.access_token IS 'Legacy static Admin API token (shpat_...); optional. Used only when client_id/client_secret are absent';

-- 2. channel_raw_events
CREATE TABLE IF NOT EXISTS channel_raw_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    channel VARCHAR(50) NOT NULL,
    store_identifier VARCHAR(255),
    source VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(100),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE channel_raw_events IS 'Raw channel payload landing table: persist before processing, per-payload status for diagnosis/replay/reconciliation';
COMMENT ON COLUMN channel_raw_events.channel IS 'Sales channel: SHOPIFY / AMAZON / ... (channel-generic)';
COMMENT ON COLUMN channel_raw_events.source IS 'How the payload arrived: POLL / WEBHOOK / RECONCILE';
COMMENT ON COLUMN channel_raw_events.event_type IS 'Payload kind, e.g. ORDER';
COMMENT ON COLUMN channel_raw_events.external_id IS 'Channel-side identifier (e.g. Shopify order id) for dedup and correlation';
COMMENT ON COLUMN channel_raw_events.status IS 'RECEIVED / PROCESSED / FAILED / SKIPPED';

CREATE INDEX IF NOT EXISTS idx_raw_events_channel_status ON channel_raw_events (channel, status);
CREATE INDEX IF NOT EXISTS idx_raw_events_external ON channel_raw_events (channel, event_type, external_id);
CREATE INDEX IF NOT EXISTS idx_raw_events_created ON channel_raw_events (created_at);
