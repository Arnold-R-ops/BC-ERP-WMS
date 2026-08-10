-- Repair the first reviewed business role templates using least privilege.
--
-- Reviewed and importable:
--   WAREHOUSE_STAFF: mobile receive, pick and blind-count operations (8)
--   WAREHOUSE_ADMIN: WAREHOUSE_STAFF plus product/SKU/category maintenance (21)
--
-- GENERAL_MANAGER and SALESPERSON remain non-importable until their missing
-- approval and sales permission catalogues are completed.

CREATE TEMP TABLE _reviewed_role_permission (
    role_code VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
) ON COMMIT DROP;

INSERT INTO _reviewed_role_permission (role_code, permission_code)
SELECT role_code, permission_code
FROM (VALUES
    ('WAREHOUSE_STAFF', 'menu:warehouse-mobile'),
    ('WAREHOUSE_STAFF', 'inbound:list'),
    ('WAREHOUSE_STAFF', 'inbound:view'),
    ('WAREHOUSE_STAFF', 'inbound:receive_goods'),
    ('WAREHOUSE_STAFF', 'outbound:view'),
    ('WAREHOUSE_STAFF', 'outbound:pick'),
    ('WAREHOUSE_STAFF', 'stocktake:view'),
    ('WAREHOUSE_STAFF', 'stocktake:count'),

    ('WAREHOUSE_ADMIN', 'menu:warehouse-mobile'),
    ('WAREHOUSE_ADMIN', 'inbound:list'),
    ('WAREHOUSE_ADMIN', 'inbound:view'),
    ('WAREHOUSE_ADMIN', 'inbound:receive_goods'),
    ('WAREHOUSE_ADMIN', 'outbound:view'),
    ('WAREHOUSE_ADMIN', 'outbound:pick'),
    ('WAREHOUSE_ADMIN', 'stocktake:view'),
    ('WAREHOUSE_ADMIN', 'stocktake:count'),
    ('WAREHOUSE_ADMIN', 'menu:product-catalog'),
    ('WAREHOUSE_ADMIN', 'category:create'),
    ('WAREHOUSE_ADMIN', 'category:update'),
    ('WAREHOUSE_ADMIN', 'category:delete'),
    ('WAREHOUSE_ADMIN', 'category:view'),
    ('WAREHOUSE_ADMIN', 'product:create'),
    ('WAREHOUSE_ADMIN', 'product:edit'),
    ('WAREHOUSE_ADMIN', 'product:status'),
    ('WAREHOUSE_ADMIN', 'product:view'),
    ('WAREHOUSE_ADMIN', 'product-sku:create'),
    ('WAREHOUSE_ADMIN', 'product-sku:edit'),
    ('WAREHOUSE_ADMIN', 'product-sku:status'),
    ('WAREHOUSE_ADMIN', 'product-sku:view')
) AS baseline(role_code, permission_code);

DO $$
DECLARE
    missing_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO missing_count
    FROM _reviewed_role_permission baseline
    LEFT JOIN sys_role role
      ON role.company_id = 1
     AND role.role_code = baseline.role_code
    LEFT JOIN sys_permission permission
      ON permission.company_id = 1
     AND permission.permission_code = baseline.permission_code
     AND permission.status = 'ACTIVE'
    WHERE role.id IS NULL OR permission.id IS NULL;

    IF missing_count > 0 THEN
        RAISE EXCEPTION 'Cannot repair business templates: % required role/permission rows are missing', missing_count;
    END IF;
END $$;

-- Capture affected users before changing the baseline so their existing JWTs
-- are invalidated in accordance with the IAM security-version rule.
CREATE TEMP TABLE _affected_template_user (
    user_id BIGINT PRIMARY KEY
) ON COMMIT DROP;

WITH RECURSIVE affected_roles AS (
    SELECT id
    FROM sys_role
    WHERE company_id = 1
      AND role_code IN ('WAREHOUSE_STAFF', 'WAREHOUSE_ADMIN')
    UNION
    SELECT inherit.child_role_id
    FROM sys_role_inherit inherit
    JOIN affected_roles parent ON parent.id = inherit.parent_role_id
)
INSERT INTO _affected_template_user (user_id)
SELECT DISTINCT assignment.user_id
FROM sys_user_role assignment
JOIN affected_roles role ON role.id = assignment.role_id;

-- Converge the reviewed templates to the exact baseline.
DELETE FROM sys_role_permission assignment
USING sys_role role, sys_permission permission
WHERE assignment.role_id = role.id
  AND assignment.permission_id = permission.id
  AND role.company_id = 1
  AND role.role_code IN ('WAREHOUSE_STAFF', 'WAREHOUSE_ADMIN')
  AND NOT EXISTS (
      SELECT 1
      FROM _reviewed_role_permission baseline
      WHERE baseline.role_code = role.role_code
        AND baseline.permission_code = permission.permission_code
  );

INSERT INTO sys_role_permission (
    company_id, role_id, permission_id, granted_at
)
SELECT
    role.company_id,
    role.id,
    permission.id,
    CURRENT_TIMESTAMP
FROM _reviewed_role_permission baseline
JOIN sys_role role
  ON role.company_id = 1
 AND role.role_code = baseline.role_code
JOIN sys_permission permission
  ON permission.company_id = role.company_id
 AND permission.permission_code = baseline.permission_code
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission existing
    WHERE existing.company_id = role.company_id
      AND existing.role_id = role.id
      AND existing.permission_id = permission.id
);

UPDATE sys_role
SET import_allowed = CASE
        WHEN role_code IN ('WAREHOUSE_STAFF', 'WAREHOUSE_ADMIN') THEN TRUE
        ELSE FALSE
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND role_type = 'SYSTEM'
  AND system_category = 'BUSINESS_TEMPLATE';

UPDATE users
SET security_version = security_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT user_id FROM _affected_template_user);

