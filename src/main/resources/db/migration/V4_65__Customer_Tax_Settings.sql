ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS vat_rate DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS secondary_tax_rate DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS vat_number VARCHAR(100);

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS ck_customers_vat_rate,
    DROP CONSTRAINT IF EXISTS ck_customers_secondary_tax_rate;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_vat_rate CHECK (vat_rate IS NULL OR (vat_rate >= 0 AND vat_rate <= 100)),
    ADD CONSTRAINT ck_customers_secondary_tax_rate CHECK (secondary_tax_rate IS NULL OR (secondary_tax_rate >= 0 AND secondary_tax_rate <= 100));
