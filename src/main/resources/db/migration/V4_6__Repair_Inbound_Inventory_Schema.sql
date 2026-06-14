-- V4.6: Repair inbound inventory schema for V4.4 acceptance tests.
--
-- Some local databases were baselined at V4.5, so earlier V3.5 migrations
-- that changed inventory_batch and stock_transactions may not have been applied.

ALTER TABLE inventory_batch
ALTER COLUMN batch_code TYPE VARCHAR(50);

COMMENT ON COLUMN inventory_batch.batch_code IS
'System batch code. Supports SPU-SKU-DATE format such as SPU-TEA-TEA-V44-1781052142-20260610.';

ALTER TABLE inventory_batch
ALTER COLUMN purchase_order_item_id DROP NOT NULL;

COMMENT ON COLUMN inventory_batch.purchase_order_item_id IS
'Optional reference to purchase order item. NULL for inbound orders that are not linked to purchase orders.';

ALTER TABLE stock_transactions
DROP CONSTRAINT IF EXISTS stock_transactions_sourcetype_check;

ALTER TABLE stock_transactions
ADD CONSTRAINT stock_transactions_sourcetype_check
CHECK (sourceType IN (
    'PURCHASE_IN',
    'INBOUND_IN',
    'SALE_OUT',
    'RETURN_IN',
    'PRODUCTION_OUT',
    'PRODUCTION_IN',
    'TRANSFER_OUT',
    'TRANSFER_IN',
    'INVENTORY_GAIN',
    'INVENTORY_LOSS',
    'SCRAP_OUT',
    'GIFT_OUT',
    'SAMPLE_OUT',
    'MANUAL_ADJUST'
));

ALTER TABLE sales_orders
DROP COLUMN IF EXISTS is_deleted;

INSERT INTO system_config (config_key, config_value, description, config_type)
VALUES ('sales.approval.amount_threshold', '50000.00', 'Sales order approval amount threshold', 'DECIMAL')
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE sales_orders
DROP CONSTRAINT IF EXISTS sales_orders_status_check;

ALTER TABLE sales_orders
ADD CONSTRAINT sales_orders_status_check
CHECK (status IN (
    'DRAFT',
    'PENDING_APPROVAL',
    'APPROVED_AWAITING_SHIPMENT',
    'SHIPPED',
    'REJECTED',
    'CANCELLED',
    'VOIDED'
));
