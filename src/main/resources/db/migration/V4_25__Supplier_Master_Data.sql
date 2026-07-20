-- P1.6 Step 3: govern the existing supplier master and link purchase orders to it.

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS remark VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_suppliers_company_active_deleted
    ON suppliers (company_id, is_active, is_deleted);

ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS supplier_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_purchase_order_supplier_id
    ON purchase_order (supplier_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_purchase_order_supplier'
          AND conrelid = 'purchase_order'::regclass
    ) THEN
        ALTER TABLE purchase_order
            ADD CONSTRAINT fk_purchase_order_supplier
            FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- Create one deterministic supplier record for each historical name that does not
-- already exist in the same company. The legacy text remains the order snapshot.
WITH purchase_supplier_names AS (
    SELECT
        po.company_id,
        LOWER(BTRIM(po.supplier)) AS normalized_name,
        MIN(BTRIM(po.supplier)) AS display_name
    FROM purchase_order po
    WHERE NULLIF(BTRIM(po.supplier), '') IS NOT NULL
    GROUP BY po.company_id, LOWER(BTRIM(po.supplier))
), missing_suppliers AS (
    SELECT names.company_id, names.normalized_name, names.display_name
    FROM purchase_supplier_names names
    WHERE NOT EXISTS (
        SELECT 1
        FROM suppliers supplier
        WHERE supplier.company_id = names.company_id
          AND LOWER(BTRIM(supplier.name)) = names.normalized_name
          AND supplier.is_deleted = FALSE
    )
)
INSERT INTO suppliers (
    company_id, code, name, is_active, is_deleted, remark, created_at, updated_at
)
SELECT
    company_id,
    'SUP-LEGACY-' || UPPER(SUBSTRING(MD5(normalized_name) FROM 1 FOR 12)),
    display_name,
    TRUE,
    FALSE,
    'Generated from historical purchase order supplier snapshot by V4.25',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM missing_suppliers;

-- Resolve duplicate names deterministically to the earliest active master record.
UPDATE purchase_order po
SET supplier_id = (
    SELECT MIN(supplier.id)
    FROM suppliers supplier
    WHERE supplier.company_id = po.company_id
      AND LOWER(BTRIM(supplier.name)) = LOWER(BTRIM(po.supplier))
      AND supplier.is_deleted = FALSE
)
WHERE po.supplier_id IS NULL
  AND NULLIF(BTRIM(po.supplier), '') IS NOT NULL;

COMMENT ON COLUMN suppliers.is_deleted IS
    'Logical deletion flag. Deleted suppliers are hidden from master-data APIs.';
COMMENT ON COLUMN suppliers.remark IS 'Supplier master-data remark.';
COMMENT ON COLUMN purchase_order.supplier_id IS
    'Supplier master reference. The supplier text column remains the order-time name snapshot.';

-- Supplier master-data permissions belong to the existing purchase menu.
INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, sort_order, status,
    description, created_at, updated_at
)
SELECT
    1,
    permission_code,
    permission_name,
    'API',
    resource_path,
    http_method,
    (SELECT id FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:purchase'),
    sort_order,
    'ACTIVE',
    description,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('supplier:view', '查看供应商', '/api/suppliers/**', 'GET', 10, 'View supplier master data'),
    ('supplier:create', '创建供应商', '/api/suppliers', 'POST', 11, 'Create supplier master data'),
    ('supplier:update', '维护供应商', '/api/suppliers/**', 'PUT', 12, 'Update and enable supplier master data'),
    ('supplier:delete', '删除供应商', '/api/suppliers/*', 'DELETE', 13, 'Logically delete an unused supplier')
) AS permissions(permission_code, permission_name, resource_path, http_method, sort_order, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = permissions.permission_code
);

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = 1
WHERE role.company_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'BUYER')
  AND permission.permission_code IN (
      'supplier:view', 'supplier:create', 'supplier:update', 'supplier:delete'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = 1
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
