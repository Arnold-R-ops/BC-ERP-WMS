-- P1 batch 5 revised: internal multi-shipment tracking only.
-- No Shopify inventory or fulfillment writeback is implemented.

CREATE TABLE IF NOT EXISTS sales_order_shipments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id) ON DELETE RESTRICT,
    tracking_no VARCHAR(100) NOT NULL,
    carrier VARCHAR(50),
    tracking_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    shipped_at TIMESTAMP WITH TIME ZONE,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    remark VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_order_shipment_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT uk_sales_order_shipment_tracking UNIQUE (company_id, sales_order_id, tracking_no)
);

CREATE INDEX IF NOT EXISTS idx_sales_order_shipment_order
    ON sales_order_shipments(company_id, sales_order_id, status);

COMMENT ON TABLE sales_order_shipments IS 'Internal shipment and tracking records; one sales order can have multiple shipments';
COMMENT ON COLUMN sales_order_shipments.status IS 'ACTIVE or VOIDED; records are retained for audit';
