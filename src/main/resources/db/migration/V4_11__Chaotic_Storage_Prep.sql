-- V4.5.2 flexible chaotic storage preparation.
-- Existing reservation math remains unchanged: Available = OnHand - Reserved.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS batch_tracking_mode VARCHAR(30) NOT NULL DEFAULT 'PRINTED_LABEL';

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'EMPTY';

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS pos_x INTEGER NOT NULL DEFAULT 0;

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS pos_y INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_batch_tracking_mode') THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_batch_tracking_mode
            CHECK (batch_tracking_mode IN ('PRINTED_LABEL', 'LOCATION_VISUAL'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_locations_status') THEN
        ALTER TABLE locations
            ADD CONSTRAINT chk_locations_status
            CHECK (status IN ('EMPTY', 'OCCUPIED'));
    END IF;
END $$;

UPDATE locations
SET status = 'OCCUPIED'
WHERE id IN (
    SELECT DISTINCT location_id
    FROM inventory_batch
    WHERE location_id IS NOT NULL
      AND active = TRUE
      AND quantity > 0
);

UPDATE locations
SET status = 'EMPTY'
WHERE id NOT IN (
    SELECT DISTINCT location_id
    FROM inventory_batch
    WHERE location_id IS NOT NULL
      AND active = TRUE
      AND quantity > 0
);

CREATE INDEX IF NOT EXISTS idx_locations_status ON locations(status);
CREATE INDEX IF NOT EXISTS idx_locations_coordinates ON locations(pos_x, pos_y);
CREATE INDEX IF NOT EXISTS idx_inventory_batch_fefo_fragment
    ON inventory_batch(product_id, active, expiry_date, quantity, id);
