ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN purchase_order.version IS
    'Optimistic-lock version used to prevent concurrent ORDERING edits from overwriting each other';
