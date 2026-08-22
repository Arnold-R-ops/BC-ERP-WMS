-- PostgreSQL database-level company isolation for every tenant-owned table.
--
-- The application transaction manager sets transaction-local app.company_id.
-- current_setting(..., true) returns NULL when absent, so reads return zero
-- rows and writes fail the WITH CHECK policy instead of falling back to company 1.

CREATE OR REPLACE FUNCTION bcwms_current_company_id()
RETURNS BIGINT
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('app.company_id', true), '')::BIGINT
$$;

COMMENT ON FUNCTION bcwms_current_company_id() IS
    'Transaction-local company discriminator used by BCWMS RLS policies';

DO $rls$
DECLARE
    tenant_table TEXT;
    tenant_tables CONSTANT TEXT[] := ARRAY[
        'backorder_line',
        'categories',
        'channel_inventory_state',
        'channel_raw_events',
        'channel_sku_mapping',
        'customer_fact_summary',
        'customer_product_summary',
        'customers',
        'domain_outbox',
        'emergency_stock_correction',
        'historical_test_data_archive_audit',
        'historical_test_data_registry',
        'idempotency_request',
        'inbound_order_items',
        'inbound_orders',
        'integration_configs',
        'inventory',
        'inventory_batch',
        'inventory_reservations',
        'locations',
        'outbound_tasks',
        'pending_sku_mapping',
        'product_skus',
        'products',
        'purchase_order',
        'purchase_order_item',
        'sales_daily_summary',
        'sales_order_items',
        'sales_order_shipments',
        'sales_orders',
        'stock_transactions',
        'stocktake_items',
        'stocktake_tasks',
        'suppliers',
        'sys_approval_template',
        'sys_excel_templates',
        'sys_permission',
        'sys_permission_request',
        'sys_permission_request_audit',
        'sys_permission_request_warehouse',
        'sys_role',
        'sys_role_copy_audit',
        'sys_role_governance_audit',
        'sys_role_inherit',
        'sys_role_permission',
        'sys_user_role',
        'sys_user_warehouse',
        'system_config',
        'users',
        'warehouses'
    ];
BEGIN
    FOREACH tenant_table IN ARRAY tenant_tables LOOP
        IF to_regclass('public.' || tenant_table) IS NULL THEN
            RAISE EXCEPTION 'RLS migration expected tenant table public.%', tenant_table;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = tenant_table
              AND column_name = 'company_id'
        ) THEN
            RAISE EXCEPTION 'RLS migration expected company_id on public.%', tenant_table;
        END IF;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tenant_table);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', tenant_table);
        EXECUTE format('DROP POLICY IF EXISTS company_isolation ON public.%I', tenant_table);
        EXECUTE format(
            'CREATE POLICY company_isolation ON public.%I '
            || 'FOR ALL '
            || 'USING (company_id = bcwms_current_company_id()) '
            || 'WITH CHECK (company_id = bcwms_current_company_id())',
            tenant_table
        );
    END LOOP;
END
$rls$;

-- Control-plane tables intentionally have no RLS company policy. They are
-- accessed only through platform/app services and explicit authorization.
