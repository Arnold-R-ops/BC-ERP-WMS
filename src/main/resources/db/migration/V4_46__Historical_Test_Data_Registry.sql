-- Version-controlled identity evidence for historical test orders.
-- There is deliberately no online API for inserting, updating, or deleting these rows.

CREATE TABLE IF NOT EXISTS historical_test_data_registry (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id) ON DELETE RESTRICT,
    order_no VARCHAR(30) NOT NULL,
    source_version VARCHAR(30) NOT NULL,
    registration_reason VARCHAR(500) NOT NULL,
    registered_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_historical_test_registry_order_id UNIQUE (company_id, sales_order_id),
    CONSTRAINT uk_historical_test_registry_order_no UNIQUE (company_id, order_no)
);

CREATE OR REPLACE FUNCTION prevent_historical_test_registry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'historical_test_data_registry is migration-managed and immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_historical_test_registry_immutable
    ON historical_test_data_registry;

CREATE TRIGGER trg_historical_test_registry_immutable
BEFORE UPDATE OR DELETE ON historical_test_data_registry
FOR EACH ROW EXECUTE FUNCTION prevent_historical_test_registry_mutation();

INSERT INTO historical_test_data_registry (
    company_id,
    sales_order_id,
    order_no,
    source_version,
    registration_reason,
    registered_by
)
SELECT
    sales_order.company_id,
    sales_order.id,
    sales_order.order_no,
    'V4.4',
    'User-approved disposition scope for preserved V4.4 historical test evidence',
    'FLYWAY_V4_46'
FROM sales_orders sales_order
WHERE (sales_order.id = 13 AND sales_order.order_no = 'SO20260610001')
   OR (sales_order.id = 14 AND sales_order.order_no = 'SO20260610002')
ON CONFLICT (company_id, sales_order_id) DO NOTHING;

COMMENT ON TABLE historical_test_data_registry IS
    'Immutable migration-managed identity evidence for explicitly approved historical test orders';
