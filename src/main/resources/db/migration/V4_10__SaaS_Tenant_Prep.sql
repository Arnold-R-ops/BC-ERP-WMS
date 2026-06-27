-- V4.10: SaaS tenant preparation and UTC schema guardrails
-- Scope:
-- 1. Add company_id placeholder to current core tables with default company 1.
-- 2. Convert business unique keys to tenant-scoped composite unique indexes.
-- 3. Do not introduce tenant runtime logic in this migration.
--
-- Timezone note:
-- This migration creates no new timestamp columns. All new tables created after
-- this migration should use TIMESTAMPTZ / TIMESTAMP WITH TIME ZONE for absolute
-- time storage.

CREATE OR REPLACE FUNCTION _wms_add_company_id(p_table_name text)
RETURNS void AS $$
DECLARE
    v_table regclass;
BEGIN
    v_table := to_regclass(p_table_name);
    IF v_table IS NULL THEN
        RETURN;
    END IF;

    EXECUTE format(
        'ALTER TABLE %s ADD COLUMN IF NOT EXISTS company_id BIGINT NOT NULL DEFAULT 1',
        v_table
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION _wms_drop_unique_by_columns(
    p_table_name text,
    p_columns text[]
)
RETURNS void AS $$
DECLARE
    v_table regclass;
    v_constraint record;
BEGIN
    v_table := to_regclass(p_table_name);
    IF v_table IS NULL THEN
        RETURN;
    END IF;

    FOR v_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = v_table
          AND c.contype = 'u'
          AND (
              SELECT array_agg(a.attname::text ORDER BY x.ord)
              FROM unnest(c.conkey) WITH ORDINALITY AS x(attnum, ord)
              JOIN pg_attribute a
                ON a.attrelid = c.conrelid
               AND a.attnum = x.attnum
          ) = p_columns
    LOOP
        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I',
            v_table,
            v_constraint.conname
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION _wms_create_unique_if_columns_exist(
    p_table_name text,
    p_index_name text,
    p_columns text[]
)
RETURNS void AS $$
DECLARE
    v_table regclass;
    v_column_list text;
BEGIN
    v_table := to_regclass(p_table_name);
    IF v_table IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM unnest(p_columns) AS requested_column(column_name)
        WHERE NOT EXISTS (
            SELECT 1
            FROM pg_attribute a
            WHERE a.attrelid = v_table
              AND a.attname = requested_column.column_name
              AND NOT a.attisdropped
        )
    ) THEN
        RETURN;
    END IF;

    SELECT string_agg(format('%I', column_name), ', ' ORDER BY ord)
    INTO v_column_list
    FROM unnest(p_columns) WITH ORDINALITY AS requested_column(column_name, ord);

    EXECUTE format(
        'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %s (%s)',
        p_index_name,
        v_table,
        v_column_list
    );
END;
$$ LANGUAGE plpgsql;

-- 1. Add company_id placeholder to identity, master data, inventory and order tables.
SELECT _wms_add_company_id(table_name)
FROM unnest(ARRAY[
    'users',
    'sys_user',
    'sys_role',
    'sys_permission',
    'sys_user_role',
    'sys_role_permission',
    'sys_role_inherit',
    'sys_excel_template',
    'sys_excel_templates',
    'products',
    'product_spu',
    'warehouses',
    'locations',
    'customers',
    'suppliers',
    'system_config',
    'integration_configs',
    'inventory',
    'inventory_batch',
    'inventory_reservations',
    'stock_transactions',
    'stock_transaction',
    'purchase_order',
    'purchase_order_item',
    'inbound_orders',
    'inbound_order_items',
    'sales_orders',
    'sales_order',
    'sales_order_items',
    'outbound_tasks',
    'outbound_task',
    'stocktake_tasks',
    'stocktake_items',
    'backorder_line',
    'domain_outbox',
    'idempotency_request',
    'emergency_stock_correction'
]) AS t(table_name);

-- 2. Drop old single-tenant unique keys.
SELECT _wms_drop_unique_by_columns('users', ARRAY['username']);
SELECT _wms_drop_unique_by_columns('sys_user', ARRAY['username']);
SELECT _wms_drop_unique_by_columns('sys_role', ARRAY['role_code']);
SELECT _wms_drop_unique_by_columns('sys_permission', ARRAY['permission_code']);
SELECT _wms_drop_unique_by_columns('sys_user_role', ARRAY['user_id', 'role_id']);
SELECT _wms_drop_unique_by_columns('sys_role_permission', ARRAY['role_id', 'permission_id']);
SELECT _wms_drop_unique_by_columns('sys_role_inherit', ARRAY['child_role_id', 'parent_role_id']);

SELECT _wms_drop_unique_by_columns('products', ARRAY['barcode']);
SELECT _wms_drop_unique_by_columns('products', ARRAY['sku_code']);
SELECT _wms_drop_unique_by_columns('product_spu', ARRAY['spu_code']);
SELECT _wms_drop_unique_by_columns('warehouses', ARRAY['code']);
SELECT _wms_drop_unique_by_columns('customers', ARRAY['code']);
SELECT _wms_drop_unique_by_columns('suppliers', ARRAY['code']);
SELECT _wms_drop_unique_by_columns('system_config', ARRAY['config_key']);

SELECT _wms_drop_unique_by_columns('locations', ARRAY['location_code']);
SELECT _wms_drop_unique_by_columns('locations', ARRAY['locationcode']);
SELECT _wms_drop_unique_by_columns('locations', ARRAY['warehouse_code', 'zone', 'shelf_number', 'position_number']);
SELECT _wms_drop_unique_by_columns('locations', ARRAY['warehousecode', 'zone', 'shelfnumber', 'positionnumber']);

SELECT _wms_drop_unique_by_columns('inventory', ARRAY['product_id', 'location_id']);
SELECT _wms_drop_unique_by_columns('inventory_batch', ARRAY['batch_code']);
SELECT _wms_drop_unique_by_columns('inventory_batch', ARRAY['batch_code', 'location_code']);
SELECT _wms_drop_unique_by_columns('inventory_batch', ARRAY['batch_code', 'locationcode']);

SELECT _wms_drop_unique_by_columns('purchase_order', ARRAY['po_number']);
SELECT _wms_drop_unique_by_columns('inbound_orders', ARRAY['order_no']);
SELECT _wms_drop_unique_by_columns('sales_orders', ARRAY['order_no']);
SELECT _wms_drop_unique_by_columns('sales_order', ARRAY['order_no']);
SELECT _wms_drop_unique_by_columns('stocktake_tasks', ARRAY['task_no']);
SELECT _wms_drop_unique_by_columns('idempotency_request', ARRAY['idempotency_key']);
SELECT _wms_drop_unique_by_columns('emergency_stock_correction', ARRAY['correction_no']);

DROP INDEX IF EXISTS idx_username;
DROP INDEX IF EXISTS idx_role_code;
DROP INDEX IF EXISTS idx_permission_code;
DROP INDEX IF EXISTS idx_barcode;
DROP INDEX IF EXISTS idx_spu_code;
DROP INDEX IF EXISTS idx_po_number;
DROP INDEX IF EXISTS idx_inbound_order_no;
DROP INDEX IF EXISTS idx_sales_order_no;
DROP INDEX IF EXISTS idx_stocktake_task_no;
DROP INDEX IF EXISTS idx_system_config_key;
DROP INDEX IF EXISTS idx_customer_code;
DROP INDEX IF EXISTS idx_idempotency_key;
DROP INDEX IF EXISTS idx_emergency_correction_no;

-- 3. Recreate tenant-scoped composite unique indexes. company_id is always leftmost.
SELECT _wms_create_unique_if_columns_exist('users', 'uk_users_company_username', ARRAY['company_id', 'username']);
SELECT _wms_create_unique_if_columns_exist('sys_user', 'uk_sys_user_company_username', ARRAY['company_id', 'username']);
SELECT _wms_create_unique_if_columns_exist('sys_role', 'uk_sys_role_company_code', ARRAY['company_id', 'role_code']);
SELECT _wms_create_unique_if_columns_exist('sys_permission', 'uk_sys_permission_company_code', ARRAY['company_id', 'permission_code']);
SELECT _wms_create_unique_if_columns_exist('sys_user_role', 'uk_sys_user_role_company_user_role', ARRAY['company_id', 'user_id', 'role_id']);
SELECT _wms_create_unique_if_columns_exist('sys_role_permission', 'uk_sys_role_permission_company_role_permission', ARRAY['company_id', 'role_id', 'permission_id']);
SELECT _wms_create_unique_if_columns_exist('sys_role_inherit', 'uk_sys_role_inherit_company_child_parent', ARRAY['company_id', 'child_role_id', 'parent_role_id']);

SELECT _wms_create_unique_if_columns_exist('products', 'uk_products_company_barcode', ARRAY['company_id', 'barcode']);
SELECT _wms_create_unique_if_columns_exist('products', 'uk_products_company_sku_code', ARRAY['company_id', 'sku_code']);
SELECT _wms_create_unique_if_columns_exist('product_spu', 'uk_product_spu_company_code', ARRAY['company_id', 'spu_code']);
SELECT _wms_create_unique_if_columns_exist('warehouses', 'uk_warehouses_company_code', ARRAY['company_id', 'code']);
SELECT _wms_create_unique_if_columns_exist('customers', 'uk_customers_company_code', ARRAY['company_id', 'code']);
SELECT _wms_create_unique_if_columns_exist('suppliers', 'uk_suppliers_company_code', ARRAY['company_id', 'code']);
SELECT _wms_create_unique_if_columns_exist('system_config', 'uk_system_config_company_key', ARRAY['company_id', 'config_key']);

SELECT _wms_create_unique_if_columns_exist('locations', 'uk_locations_company_location_code', ARRAY['company_id', 'location_code']);
SELECT _wms_create_unique_if_columns_exist('locations', 'uk_locations_company_locationcode', ARRAY['company_id', 'locationcode']);
SELECT _wms_create_unique_if_columns_exist('locations', 'uk_locations_company_warehouse_position', ARRAY['company_id', 'warehouse_code', 'zone', 'shelf_number', 'position_number']);
SELECT _wms_create_unique_if_columns_exist('locations', 'uk_locations_company_warehouseposition', ARRAY['company_id', 'warehousecode', 'zone', 'shelfnumber', 'positionnumber']);

SELECT _wms_create_unique_if_columns_exist('inventory', 'uk_inventory_company_product_location', ARRAY['company_id', 'product_id', 'location_id']);
SELECT _wms_create_unique_if_columns_exist('inventory_batch', 'uk_inventory_batch_company_batch_location', ARRAY['company_id', 'batch_code', 'location_code']);
SELECT _wms_create_unique_if_columns_exist('inventory_batch', 'uk_inventory_batch_company_batchlocation', ARRAY['company_id', 'batch_code', 'locationcode']);

SELECT _wms_create_unique_if_columns_exist('purchase_order', 'uk_purchase_order_company_po_number', ARRAY['company_id', 'po_number']);
SELECT _wms_create_unique_if_columns_exist('inbound_orders', 'uk_inbound_orders_company_order_no', ARRAY['company_id', 'order_no']);
SELECT _wms_create_unique_if_columns_exist('sales_orders', 'uk_sales_orders_company_order_no', ARRAY['company_id', 'order_no']);
SELECT _wms_create_unique_if_columns_exist('sales_order', 'uk_sales_order_company_order_no', ARRAY['company_id', 'order_no']);
SELECT _wms_create_unique_if_columns_exist('stocktake_tasks', 'uk_stocktake_tasks_company_task_no', ARRAY['company_id', 'task_no']);
SELECT _wms_create_unique_if_columns_exist('idempotency_request', 'uk_idempotency_company_key', ARRAY['company_id', 'idempotency_key']);
SELECT _wms_create_unique_if_columns_exist('emergency_stock_correction', 'uk_emergency_correction_company_no', ARRAY['company_id', 'correction_no']);

-- Frequent tenant query anchors for high-volume operational tables.
CREATE INDEX IF NOT EXISTS idx_inventory_batch_company_product ON inventory_batch(company_id, product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_company_product ON inventory_reservations(company_id, product_id);
CREATE INDEX IF NOT EXISTS idx_sales_orders_company_status ON sales_orders(company_id, status);
CREATE INDEX IF NOT EXISTS idx_outbound_tasks_company_status ON outbound_tasks(company_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_transactions_company_product ON stock_transactions(company_id, product_id);

COMMENT ON COLUMN users.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';
COMMENT ON COLUMN inventory_batch.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';
COMMENT ON COLUMN sales_orders.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';
COMMENT ON COLUMN outbound_tasks.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';
COMMENT ON COLUMN stock_transactions.company_id IS 'SaaS tenant placeholder. Current single-company deployment uses 1.';

DROP FUNCTION IF EXISTS _wms_create_unique_if_columns_exist(text, text, text[]);
DROP FUNCTION IF EXISTS _wms_drop_unique_by_columns(text, text[]);
DROP FUNCTION IF EXISTS _wms_add_company_id(text);
