-- Rename the governed business-customer type from B2B to CLIENT.
-- V4.24 remains immutable because it may already be recorded in Flyway history.

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS ck_customers_customer_type;

UPDATE customers
SET customer_type = 'CLIENT'
WHERE customer_type = 'B2B';

ALTER TABLE customers
    ALTER COLUMN customer_type SET DEFAULT 'CLIENT';

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_customer_type
        CHECK (customer_type IN ('CLIENT', 'CONSUMER'));

COMMENT ON COLUMN customers.customer_type IS
    'CLIENT is governed customer master data; CONSUMER is a lightweight retail-channel identity.';
