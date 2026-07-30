-- P2 sales analytics fact layer.
-- Read APIs use these compact summaries instead of scanning hot order tables.

ALTER TABLE sales_daily_summary
    ADD COLUMN IF NOT EXISTS draft_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pending_approval_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS approved_awaiting_shipment_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipped_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rejected_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS voided_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS customer_product_summary (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    customer_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    total_order_count BIGINT NOT NULL DEFAULT 0,
    total_quantity BIGINT NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    first_order_date DATE,
    last_order_date DATE,
    average_interval_days NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_product_summary_scope
        UNIQUE (company_id, customer_id, product_sku_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_product_summary_customer_rank
    ON customer_product_summary(company_id, customer_id, total_amount DESC);

CREATE INDEX IF NOT EXISTS idx_customer_product_summary_sku
    ON customer_product_summary(company_id, product_sku_id);

CREATE INDEX IF NOT EXISTS idx_customer_product_summary_refreshed
    ON customer_product_summary(refreshed_at);

COMMENT ON TABLE customer_product_summary IS
    'Customer-by-SKU purchase facts refreshed by the nightly reporting batch.';
COMMENT ON COLUMN customer_product_summary.total_order_count IS
    'Distinct effective sales order count containing this SKU.';
COMMENT ON COLUMN customer_product_summary.total_quantity IS
    'Total ordered quantity from effective sales orders.';
COMMENT ON COLUMN customer_product_summary.average_interval_days IS
    'Average days between effective orders containing this SKU.';
