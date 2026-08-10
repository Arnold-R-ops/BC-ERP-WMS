-- Repair report facts for historical test orders archived before the
-- AFTER_COMMIT targeted refresh listener existed. This changes only the
-- derived sales_daily_summary table, never source orders or inventory.

INSERT INTO sales_daily_summary (
    company_id,
    summary_date,
    total_order_count,
    total_amount,
    draft_count,
    pending_approval_count,
    approved_awaiting_shipment_count,
    shipped_count,
    rejected_count,
    cancelled_count,
    voided_count,
    refreshed_at,
    created_at,
    updated_at
)
SELECT
    sales_order.company_id,
    sales_order.created_at::date AS summary_date,
    COUNT(*) FILTER (
        WHERE sales_order.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
    ) AS total_order_count,
    COALESCE(
        SUM(sales_order.total_amount) FILTER (
            WHERE sales_order.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
        ),
        0.00
    ) AS total_amount,
    COUNT(*) FILTER (WHERE sales_order.status = 'DRAFT') AS draft_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'PENDING_APPROVAL') AS pending_approval_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'APPROVED_AWAITING_SHIPMENT')
        AS approved_awaiting_shipment_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'SHIPPED') AS shipped_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'REJECTED') AS rejected_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'CANCELLED') AS cancelled_count,
    COUNT(*) FILTER (WHERE sales_order.status = 'VOIDED') AS voided_count,
    CURRENT_TIMESTAMP AS refreshed_at,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM sales_orders sales_order
WHERE EXISTS (
    SELECT 1
    FROM historical_test_data_registry registry
    JOIN sales_orders registered_order
      ON registered_order.id = registry.sales_order_id
     AND registered_order.company_id = registry.company_id
     AND registered_order.order_no = registry.order_no
    WHERE registered_order.company_id = sales_order.company_id
      AND registered_order.created_at::date = sales_order.created_at::date
)
GROUP BY sales_order.company_id, sales_order.created_at::date
ON CONFLICT (company_id, summary_date)
DO UPDATE SET
    total_order_count = EXCLUDED.total_order_count,
    total_amount = EXCLUDED.total_amount,
    draft_count = EXCLUDED.draft_count,
    pending_approval_count = EXCLUDED.pending_approval_count,
    approved_awaiting_shipment_count = EXCLUDED.approved_awaiting_shipment_count,
    shipped_count = EXCLUDED.shipped_count,
    rejected_count = EXCLUDED.rejected_count,
    cancelled_count = EXCLUDED.cancelled_count,
    voided_count = EXCLUDED.voided_count,
    refreshed_at = EXCLUDED.refreshed_at,
    updated_at = EXCLUDED.updated_at;
