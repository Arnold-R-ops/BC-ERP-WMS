-- P1.5 coordinated model cutover:
-- Category -> Product (SPU) -> ProductSku (transaction/inventory unit).
-- This migration is intentionally a single PostgreSQL transaction under Flyway.

DO $$
BEGIN
    IF to_regclass('public.categories') IS NULL
       OR to_regclass('public.products') IS NULL
       OR to_regclass('public.product_spu') IS NULL THEN
        RAISE EXCEPTION 'P1.5 preflight failed: categories, products and product_spu must exist';
    END IF;
END $$;

-- Free the canonical Product table name, preserving every historical ID.
ALTER TABLE products RENAME TO product_skus;
ALTER SEQUENCE IF EXISTS products_id_seq RENAME TO product_skus_id_seq;
ALTER TABLE product_spu RENAME TO products;
ALTER SEQUENCE IF EXISTS product_spu_id_seq RENAME TO products_id_seq;

-- Legacy environments contain manually seeded IDs whose identity sequences may
-- lag behind the table. Align both renamed sequences before accepting writes.
SELECT setval(
    pg_get_serial_sequence('products', 'id'),
    GREATEST(COALESCE(MAX(id), 0), 1),
    COALESCE(MAX(id), 0) > 0
)
FROM products;
SELECT setval(
    pg_get_serial_sequence('product_skus', 'id'),
    GREATEST(COALESCE(MAX(id), 0), 1),
    COALESCE(MAX(id), 0) > 0
)
FROM product_skus;

-- Canonical Product (old SPU) columns.
ALTER TABLE products RENAME COLUMN spu_code TO product_code;
ALTER TABLE products RENAME COLUMN spu_name TO product_name;
ALTER TABLE products RENAME COLUMN category TO legacy_category;
ALTER TABLE products ADD COLUMN category_id BIGINT;
ALTER TABLE products ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- The old SPU ID is a PostgreSQL identity column. Renaming its owned sequence
-- above preserves identity semantics without replacing it with a column default.

-- Legacy Product timestamps were created before the UTC/TIMESTAMPTZ policy.
ALTER TABLE products
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Deterministic category migration. The old SPU category is authoritative;
-- SKU-level free text was test/legacy data and is deliberately not promoted.
WITH legacy_categories AS (
    SELECT DISTINCT ON (LOWER(TRIM(legacy_category)))
           TRIM(legacy_category) AS category_name,
           'MIG_' || UPPER(SUBSTRING(MD5(LOWER(TRIM(legacy_category))) FROM 1 FOR 12)) AS category_code
    FROM products
    WHERE legacy_category IS NOT NULL AND TRIM(legacy_category) <> ''
    ORDER BY LOWER(TRIM(legacy_category)), TRIM(legacy_category)
)
INSERT INTO categories (
    company_id, parent_id, category_code, category_name,
    sort_order, enabled, description, version, created_at, updated_at
)
SELECT 1, NULL, legacy.category_code, LEFT(legacy.category_name, 100),
       5000, TRUE, 'Migrated from legacy Product category', 0,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM legacy_categories legacy
WHERE NOT EXISTS (
    SELECT 1
    FROM categories existing
    WHERE existing.company_id = 1
      AND existing.parent_id IS NULL
      AND LOWER(existing.category_name) = LOWER(LEFT(legacy.category_name, 100))
);

WITH migrated_roots AS (
    SELECT DISTINCT root.id, root.category_code
    FROM products legacy_product
    JOIN categories root
      ON root.company_id = 1
     AND root.parent_id IS NULL
     AND LOWER(root.category_name) = LOWER(LEFT(TRIM(legacy_product.legacy_category), 100))
    WHERE legacy_product.legacy_category IS NOT NULL
      AND TRIM(legacy_product.legacy_category) <> ''
)
INSERT INTO categories (
    company_id, parent_id, category_code, category_name,
    sort_order, enabled, description, version, created_at, updated_at
)
SELECT 1, root.id,
       'MIG_UNSPEC_' || UPPER(SUBSTRING(MD5(root.id::TEXT) FROM 1 FOR 12)),
       '未细分', 9998, TRUE,
       'Temporary second-level category created during P1.5 migration',
       0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM migrated_roots root
WHERE NOT EXISTS (
    SELECT 1 FROM categories child
    WHERE child.company_id = 1
      AND child.parent_id = root.id
      AND LOWER(child.category_name) = LOWER('未细分')
);

UPDATE products product
SET category_id = COALESCE(
    (
        SELECT child.id
        FROM categories root
        JOIN categories child ON child.parent_id = root.id AND child.company_id = root.company_id
        WHERE root.company_id = product.company_id
          AND root.parent_id IS NULL
          AND product.legacy_category IS NOT NULL
          AND TRIM(product.legacy_category) <> ''
          AND LOWER(root.category_name) = LOWER(LEFT(TRIM(product.legacy_category), 100))
          AND LOWER(child.category_name) = LOWER('未细分')
        ORDER BY child.id
        LIMIT 1
    ),
    (
        SELECT pending.id
        FROM categories pending
        WHERE pending.company_id = product.company_id
          AND pending.category_code = 'PENDING_CLASSIFICATION'
        LIMIT 1
    )
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM products WHERE category_id IS NULL) THEN
        RAISE EXCEPTION 'P1.5 category migration failed: products remain without category_id';
    END IF;
END $$;

ALTER TABLE products ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE products
    ADD CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT;
ALTER TABLE products DROP COLUMN legacy_category;

-- Canonical ProductSku (old Product) columns and stable internal code.
ALTER TABLE product_skus RENAME COLUMN spu_id TO product_id;
ALTER TABLE product_skus ADD COLUMN sku_code VARCHAR(50);
UPDATE product_skus SET sku_code = 'SKU' || LPAD(id::TEXT, 8, '0');
ALTER TABLE product_skus ALTER COLUMN sku_code SET NOT NULL;

CREATE SEQUENCE product_sku_code_seq;
SELECT setval(
    'product_sku_code_seq',
    GREATEST(COALESCE((SELECT MAX(id) FROM product_skus), 0), 1),
    true
);

-- Category is a Product responsibility after normalization.
ALTER TABLE product_skus DROP COLUMN IF EXISTS category;

-- Every existing product_id below was audited as SKU semantics.
ALTER TABLE backorder_line RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE channel_sku_mapping RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE emergency_stock_correction RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE inbound_order_items RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE inventory RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE inventory_batch RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE inventory_reservations RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE purchase_order_item RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE sales_order_items RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE stock_transactions RENAME COLUMN product_id TO product_sku_id;
ALTER TABLE stocktake_items RENAME COLUMN product_id TO product_sku_id;

-- Normalize misleading legacy constraint names without relying on generated names.
CREATE OR REPLACE FUNCTION _wms_p15_rename_constraint(
    p_table_name TEXT, p_old_name TEXT, p_new_name TEXT
) RETURNS VOID AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = to_regclass(p_table_name) AND conname = p_old_name
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = to_regclass(p_table_name) AND conname = p_new_name
    ) THEN
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
                       to_regclass(p_table_name), p_old_name, p_new_name);
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT _wms_p15_rename_constraint('product_skus', 'products_pkey', 'product_skus_pkey');
SELECT _wms_p15_rename_constraint('products', 'product_spu_pkey', 'products_pkey');
SELECT _wms_p15_rename_constraint('product_skus', 'fk_product_spu', 'fk_product_skus_product');
SELECT _wms_p15_rename_constraint('product_skus', 'chk_products_batch_tracking_mode', 'chk_product_skus_batch_tracking_mode');
SELECT _wms_p15_rename_constraint('channel_sku_mapping', 'channel_sku_mapping_product_id_fkey', 'fk_channel_sku_mapping_product_sku');
SELECT _wms_p15_rename_constraint('channel_sku_mapping', 'chk_sku_mapping_product', 'chk_sku_mapping_product_sku');
SELECT _wms_p15_rename_constraint('inbound_order_items', 'fk_inbound_item_product', 'fk_inbound_item_product_sku');
SELECT _wms_p15_rename_constraint('inventory', 'fk_inventory_product', 'fk_inventory_product_sku');
SELECT _wms_p15_rename_constraint('inventory_batch', 'fk_batch_product', 'fk_inventory_batch_product_sku');
SELECT _wms_p15_rename_constraint('purchase_order_item', 'fk_po_item_product', 'fk_purchase_order_item_product_sku');
SELECT _wms_p15_rename_constraint('sales_order_items', 'fkkjyfnk6r23ukvjpybjq1q0b92', 'fk_sales_order_item_product_sku');
SELECT _wms_p15_rename_constraint('stock_transactions', 'fk_transaction_product', 'fk_stock_transaction_product_sku');
SELECT _wms_p15_rename_constraint('stocktake_items', 'fkgf29jn22e7sc3c82mbhbnma4u', 'fk_stocktake_item_product_sku');

-- Add the three audit-ledger FKs that were missing in the legacy schema.
ALTER TABLE backorder_line
    ADD CONSTRAINT fk_backorder_line_product_sku
    FOREIGN KEY (product_sku_id) REFERENCES product_skus(id) NOT VALID;
ALTER TABLE emergency_stock_correction
    ADD CONSTRAINT fk_emergency_correction_product_sku
    FOREIGN KEY (product_sku_id) REFERENCES product_skus(id) NOT VALID;
ALTER TABLE inventory_reservations
    ADD CONSTRAINT fk_inventory_reservation_product_sku
    FOREIGN KEY (product_sku_id) REFERENCES product_skus(id) NOT VALID;
ALTER TABLE backorder_line VALIDATE CONSTRAINT fk_backorder_line_product_sku;
ALTER TABLE emergency_stock_correction VALIDATE CONSTRAINT fk_emergency_correction_product_sku;
ALTER TABLE inventory_reservations VALIDATE CONSTRAINT fk_inventory_reservation_product_sku;

-- Rebuild canonical indexes. Renamed columns keep the old indexes functionally
-- valid, but the old names are dangerous after `products` changes meaning.
DROP INDEX IF EXISTS idx_name;
DROP INDEX IF EXISTS idx_spu_id;
DROP INDEX IF EXISTS idx_spu_category;
DROP INDEX IF EXISTS idx_spu_name;
DROP INDEX IF EXISTS uk_products_company_barcode;
DROP INDEX IF EXISTS uk_product_spu_company_code;
DROP INDEX IF EXISTS idx_product_id;
DROP INDEX IF EXISTS uk_inventory_company_product_location;
DROP INDEX IF EXISTS idx_inventory_batch_company_product;
DROP INDEX IF EXISTS idx_inventory_reservations_company_product;
DROP INDEX IF EXISTS idx_stock_transactions_company_product;

CREATE UNIQUE INDEX uk_products_company_code
    ON products(company_id, product_code);
CREATE INDEX idx_products_company_category
    ON products(company_id, category_id);
CREATE INDEX idx_products_company_name
    ON products(company_id, product_name);

CREATE UNIQUE INDEX uk_product_skus_company_code
    ON product_skus(company_id, sku_code);
CREATE UNIQUE INDEX uk_product_skus_company_barcode
    ON product_skus(company_id, barcode);
CREATE INDEX idx_product_skus_company_product
    ON product_skus(company_id, product_id);
CREATE INDEX idx_product_skus_company_name
    ON product_skus(company_id, name);

CREATE UNIQUE INDEX uk_inventory_company_product_sku_location
    ON inventory(company_id, product_sku_id, location_id);
CREATE INDEX idx_inventory_company_product_sku
    ON inventory(company_id, product_sku_id);
CREATE INDEX idx_inventory_batch_company_product_sku
    ON inventory_batch(company_id, product_sku_id);
CREATE INDEX idx_inventory_reservations_company_product_sku
    ON inventory_reservations(company_id, product_sku_id);
CREATE INDEX idx_stock_transactions_company_product_sku
    ON stock_transactions(company_id, product_sku_id);

-- Pending v1 events have not crossed the integration boundary yet. Rewrite
-- only those unpublished payloads so a post-cutover consumer cannot interpret
-- an old SKU ID as the new Product (SPU) aggregate. Published history remains
-- immutable and continues to document the schema that was emitted at the time.
UPDATE domain_outbox
SET aggregate_type = CASE
        WHEN aggregate_type = 'Product' THEN 'ProductSku'
        ELSE aggregate_type
    END,
    payload_json = (
        (payload_json::JSONB - 'productId')
        || CASE
            WHEN payload_json::JSONB ? 'productId'
                THEN JSONB_BUILD_OBJECT('productSkuId', payload_json::JSONB -> 'productId')
            ELSE '{}'::JSONB
        END
        || JSONB_BUILD_OBJECT('schemaVersion', 2)
    )::TEXT,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING'
  AND event_type IN ('INVENTORY_AVAILABLE', 'BACKORDER_CREATED', 'BACKORDER_ALLOCATED');

-- Preserve existing role assignments by reinterpreting legacy product:*
-- permissions as SKU permissions, then create fresh Product permissions.
UPDATE sys_permission
SET permission_code = 'product-sku:' || SUBSTRING(permission_code FROM LENGTH('product:') + 1),
    resource_path = REPLACE(resource_path, '/api/products', '/api/product-skus'),
    permission_name = REPLACE(permission_name, '商品', 'SKU'),
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code LIKE 'product:%'
  AND permission_code <> 'product:catalog';

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, sort_order, status,
    description, created_at, updated_at
)
SELECT 1, permission_code, permission_name, 'API', resource_path, http_method,
       (SELECT id FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:product-catalog'),
       sort_order, 'ACTIVE', description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('product:view', '查看产品', '/api/products/**', 'GET', 10, 'View Product master data'),
    ('product:create', '创建产品', '/api/products', 'POST', 11, 'Create Product master data'),
    ('product:edit', '编辑产品', '/api/products/**', 'PUT', 12, 'Edit Product master data'),
    ('product:status', '启停产品', '/api/products/**', 'PUT', 13, 'Activate or deactivate Products'),
    ('product-sku:view', '查看SKU', '/api/product-skus/**', 'GET', 20, 'View ProductSku master data'),
    ('product-sku:create', '创建SKU', '/api/product-skus', 'POST', 21, 'Create ProductSku master data'),
    ('product-sku:edit', '编辑SKU', '/api/product-skus/**', 'PUT', 22, 'Edit ProductSku master data'),
    ('product-sku:status', '启停SKU', '/api/product-skus/**', 'PUT', 23, 'Activate or deactivate ProductSkus')
) AS permissions(permission_code, permission_name, resource_path, http_method, sort_order, description)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = permissions.permission_code
);

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = 1
WHERE role.company_id = 1
  AND role.role_code = 'SUPER_ADMIN'
  AND (
      permission.permission_code LIKE 'product:%'
      OR permission.permission_code LIKE 'product-sku:%'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.company_id = 1
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMENT ON TABLE products IS 'Product master (SPU); categorized business product family';
COMMENT ON TABLE product_skus IS 'ProductSku master; inventory, procurement, sales and fulfillment unit';
COMMENT ON COLUMN product_skus.sku_code IS 'Stable internal SKU code, independent from barcode and immutable';
COMMENT ON COLUMN products.category_id IS 'Required level-2 category; enforced by application service';

DROP FUNCTION _wms_p15_rename_constraint(TEXT, TEXT, TEXT);
