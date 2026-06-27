-- V4.5 advanced fulfillment modules: backorder, ATP, emergency correction, outbox, idempotency.

ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS supplier_reliability_score NUMERIC(5,2) NOT NULL DEFAULT 100.00;

ALTER TABLE purchase_order_item
    ADD COLUMN IF NOT EXISTS committed_qty INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS backorder_line (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    requested_qty INTEGER NOT NULL,
    remaining_qty INTEGER NOT NULL,
    allocated_qty INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 100,
    promised_date DATE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_backorder_product_status
    ON backorder_line(product_id, status);
CREATE INDEX IF NOT EXISTS idx_backorder_sales_order
    ON backorder_line(sales_order_id);
CREATE INDEX IF NOT EXISTS idx_backorder_priority_created
    ON backorder_line(priority, created_at);

CREATE TABLE IF NOT EXISTS domain_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON domain_outbox(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON domain_outbox(aggregate_type, aggregate_id);

CREATE TABLE IF NOT EXISTS idempotency_request (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    request_hash VARCHAR(128) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_idempotency_status
    ON idempotency_request(status);

CREATE TABLE IF NOT EXISTS emergency_stock_correction (
    id BIGSERIAL PRIMARY KEY,
    correction_no VARCHAR(40) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    inventory_batch_id BIGINT,
    batch_code VARCHAR(80),
    production_date DATE,
    expiry_date DATE,
    system_qty INTEGER NOT NULL,
    counted_qty INTEGER NOT NULL,
    adjustment_qty INTEGER NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(1000),
    evidence_url VARCHAR(500),
    related_sales_order_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_by BIGINT,
    submitted_at TIMESTAMP WITH TIME ZONE,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    applied_by BIGINT,
    applied_at TIMESTAMP WITH TIME ZONE,
    review_comment VARCHAR(500),
    approval_comment VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_emergency_correction_status
    ON emergency_stock_correction(status);
CREATE INDEX IF NOT EXISTS idx_emergency_correction_product
    ON emergency_stock_correction(product_id);
