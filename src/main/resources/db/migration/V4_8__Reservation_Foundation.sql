-- V4.5 architecture foundation implemented as V4_8 Flyway migration
-- because V4_5, V4_6, and V4_7 already exist in this repository.

ALTER TABLE inventory_batch
    ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE inventory_batch
SET reserved_quantity = 0
WHERE reserved_quantity IS NULL;

ALTER TABLE sales_orders
    ADD COLUMN IF NOT EXISTS commercial_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(40) NOT NULL DEFAULT 'UNALLOCATED',
    ADD COLUMN IF NOT EXISTS allocation_policy VARCHAR(30) NOT NULL DEFAULT 'FULL_ONLY',
    ADD COLUMN IF NOT EXISTS requested_ship_date DATE,
    ADD COLUMN IF NOT EXISTS promised_ship_date DATE,
    ADD COLUMN IF NOT EXISTS shortage_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS fulfillment_version BIGINT NOT NULL DEFAULT 0;

UPDATE sales_orders
SET commercial_status = CASE status
    WHEN 'DRAFT' THEN 'DRAFT'
    WHEN 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
    WHEN 'APPROVED_AWAITING_SHIPMENT' THEN 'APPROVED'
    WHEN 'SHIPPED' THEN 'APPROVED'
    WHEN 'REJECTED' THEN 'REJECTED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'VOIDED' THEN 'VOIDED'
    ELSE commercial_status
END,
fulfillment_status = CASE status
    WHEN 'APPROVED_AWAITING_SHIPMENT' THEN 'RESERVED'
    WHEN 'SHIPPED' THEN 'SHIPPED'
    WHEN 'REJECTED' THEN 'CANCELLED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'VOIDED' THEN 'VOIDED'
    ELSE fulfillment_status
END
WHERE commercial_status = 'DRAFT'
   OR fulfillment_status = 'UNALLOCATED';

ALTER TABLE sales_order_items
    ADD COLUMN IF NOT EXISTS requested_qty INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS allocated_qty INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipped_qty INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backorder_qty INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_qty INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(40) NOT NULL DEFAULT 'UNALLOCATED';

UPDATE sales_order_items
SET requested_qty = quantity
WHERE requested_qty = 0;

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    reserved_qty INTEGER NOT NULL,
    consumed_qty INTEGER NOT NULL DEFAULT 0,
    released_qty INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    source_type VARCHAR(40) NOT NULL DEFAULT 'SALES_ORDER',
    idempotency_key VARCHAR(120),
    created_by BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_reservation_reserved_qty CHECK (reserved_qty > 0),
    CONSTRAINT chk_inventory_reservation_consumed_qty CHECK (consumed_qty >= 0),
    CONSTRAINT chk_inventory_reservation_released_qty CHECK (released_qty >= 0)
);

ALTER TABLE outbound_tasks
    ADD COLUMN IF NOT EXISTS reservation_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_inventory_batch_reserved_quantity
    ON inventory_batch(reserved_quantity);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_inventory_batch_reserved_quantity'
    ) THEN
        ALTER TABLE inventory_batch
            ADD CONSTRAINT chk_inventory_batch_reserved_quantity
            CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sales_orders_commercial_status
    ON sales_orders(commercial_status);

CREATE INDEX IF NOT EXISTS idx_sales_orders_fulfillment_status
    ON sales_orders(fulfillment_status);

CREATE INDEX IF NOT EXISTS idx_sales_order_items_fulfillment_status
    ON sales_order_items(fulfillment_status);

CREATE INDEX IF NOT EXISTS idx_reservation_order
    ON inventory_reservations(sales_order_id);

CREATE INDEX IF NOT EXISTS idx_reservation_item
    ON inventory_reservations(sales_order_item_id);

CREATE INDEX IF NOT EXISTS idx_reservation_batch
    ON inventory_reservations(inventory_batch_id);

CREATE INDEX IF NOT EXISTS idx_reservation_status
    ON inventory_reservations(status);

CREATE INDEX IF NOT EXISTS idx_outbound_task_reservation
    ON outbound_tasks(reservation_id);
