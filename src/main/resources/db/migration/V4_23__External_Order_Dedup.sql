-- V4.23: Enforce external sales-order idempotency at database level.
-- The application-level lookup remains for friendly early rejection; this
-- index closes the race between polling and webhook delivery.

UPDATE sales_orders
SET channel = 'MANUAL'
WHERE channel IS NULL OR BTRIM(channel) = '';

ALTER TABLE sales_orders
    ALTER COLUMN channel SET DEFAULT 'MANUAL',
    ALTER COLUMN channel SET NOT NULL;

DO $migration$
DECLARE
    duplicate_details TEXT;
BEGIN
    SELECT STRING_AGG(
        FORMAT(
            'company_id=%s, channel=%s, external_order_id=%s, count=%s',
            duplicate_row.company_id,
            duplicate_row.channel,
            duplicate_row.external_order_id,
            duplicate_row.duplicate_count
        ),
        '; '
    )
    INTO duplicate_details
    FROM (
        SELECT
            company_id,
            channel,
            external_order_id,
            COUNT(*) AS duplicate_count
        FROM sales_orders
        WHERE external_order_id IS NOT NULL
        GROUP BY company_id, channel, external_order_id
        HAVING COUNT(*) > 1
        ORDER BY company_id, channel, external_order_id
        LIMIT 20
    ) duplicate_row;

    IF duplicate_details IS NOT NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = '23505',
            MESSAGE = 'Cannot create external-order unique index. Existing duplicates: ' || duplicate_details,
            HINT = 'Resolve duplicate sales orders manually, then rerun Flyway. No data was deleted.';
    END IF;
END
$migration$;

CREATE UNIQUE INDEX uq_sales_orders_company_channel_external_order
    ON sales_orders (company_id, channel, external_order_id)
    WHERE external_order_id IS NOT NULL;

COMMENT ON INDEX uq_sales_orders_company_channel_external_order IS
    'Prevents duplicate channel orders within a company while allowing the same external id across channels or companies.';
