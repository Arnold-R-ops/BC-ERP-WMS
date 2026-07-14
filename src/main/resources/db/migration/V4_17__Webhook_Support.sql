-- ====================================================================
-- V4.17: Webhook support (P1 batch 3)
--
-- channel_raw_events gains webhook_event_id: Shopify redelivers
-- unacknowledged webhooks, and X-Shopify-Webhook-Id is the exact
-- dedup key for that (order-level dedup alone cannot distinguish
-- "redelivered create" from "new event about the same order").
-- ====================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'channel_raw_events' AND column_name = 'webhook_event_id'
    ) THEN
        ALTER TABLE channel_raw_events ADD COLUMN webhook_event_id VARCHAR(100);
    END IF;
END $$;

COMMENT ON COLUMN channel_raw_events.webhook_event_id IS 'X-Shopify-Webhook-Id for exact webhook redelivery dedup (P1-B3)';

CREATE INDEX IF NOT EXISTS idx_raw_events_webhook_id ON channel_raw_events (webhook_event_id);
