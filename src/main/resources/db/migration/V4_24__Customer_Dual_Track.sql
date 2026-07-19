-- V4.24: B2B/consumer customer tracks and order shipping snapshots.
-- Existing customers and integration configs retain their current strict B2B behavior.

ALTER TABLE customers
    ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'B2B',
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN external_customer_id VARCHAR(64),
    ADD COLUMN normalized_email VARCHAR(100);

UPDATE customers
SET normalized_email = LOWER(BTRIM(email))
WHERE email IS NOT NULL AND BTRIM(email) <> '';

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_customer_type
        CHECK (customer_type IN ('B2B', 'CONSUMER')),
    ADD CONSTRAINT ck_customers_source
        CHECK (source IN ('MANUAL', 'CHANNEL'));

CREATE UNIQUE INDEX uq_customers_company_consumer_external_id
    ON customers (company_id, external_customer_id)
    WHERE customer_type = 'CONSUMER'
      AND is_deleted = false
      AND external_customer_id IS NOT NULL;

CREATE UNIQUE INDEX uq_customers_company_consumer_email
    ON customers (company_id, normalized_email)
    WHERE customer_type = 'CONSUMER'
      AND is_deleted = false
      AND normalized_email IS NOT NULL;

CREATE INDEX idx_customers_company_type_active
    ON customers (company_id, customer_type, is_active)
    WHERE is_deleted = false;

ALTER TABLE integration_configs
    ADD COLUMN retail_mode BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE sales_orders
    ADD COLUMN consignee_name VARCHAR(200),
    ADD COLUMN consignee_phone VARCHAR(50),
    ADD COLUMN ship_address1 VARCHAR(255),
    ADD COLUMN ship_address2 VARCHAR(255),
    ADD COLUMN ship_city VARCHAR(100),
    ADD COLUMN ship_province VARCHAR(100),
    ADD COLUMN ship_zip VARCHAR(30),
    ADD COLUMN ship_country_code VARCHAR(10);

COMMENT ON COLUMN customers.customer_type IS
    'B2B is governed master data; CONSUMER is a lightweight retail-channel identity.';
COMMENT ON COLUMN customers.source IS
    'Customer origin: MANUAL or CHANNEL.';
COMMENT ON COLUMN customers.external_customer_id IS
    'Channel-side customer identity used by the current Shopify retail resolver.';
COMMENT ON COLUMN customers.normalized_email IS
    'Lowercase trimmed email used for deterministic customer matching.';
COMMENT ON COLUMN integration_configs.retail_mode IS
    'When true, automatic channel ingestion may create lightweight consumer records. Defaults off.';
COMMENT ON COLUMN sales_orders.consignee_name IS
    'Order-time shipping addressee snapshot; never backfilled into the customer master.';
COMMENT ON COLUMN sales_orders.consignee_phone IS
    'Order-time shipping phone snapshot.';
COMMENT ON COLUMN sales_orders.ship_address1 IS
    'Order-time primary shipping address snapshot.';
COMMENT ON COLUMN sales_orders.ship_address2 IS
    'Order-time secondary shipping address snapshot.';
COMMENT ON COLUMN sales_orders.ship_city IS
    'Order-time shipping city snapshot.';
COMMENT ON COLUMN sales_orders.ship_province IS
    'Order-time shipping province/state snapshot.';
COMMENT ON COLUMN sales_orders.ship_zip IS
    'Order-time shipping postal-code snapshot.';
COMMENT ON COLUMN sales_orders.ship_country_code IS
    'Order-time ISO country-code snapshot.';
