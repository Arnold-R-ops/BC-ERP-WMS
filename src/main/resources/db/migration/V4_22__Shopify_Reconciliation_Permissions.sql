-- Read-only reconciliation and explicitly separated repair permission.
-- Only SUPER_ADMIN receives these permissions by default; other roles must be
-- granted them deliberately through IAM.

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, sort_order, status,
    description, created_at, updated_at
)
SELECT 1, permission_code, permission_name, 'API', resource_path, http_method,
       sort_order, 'ACTIVE', description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('integration:reconcile:view', '执行渠道只读对账', '/api/integration/shopify/reconcile', 'POST', 410,
     'Pull remote orders and create an audit report without changing business master data or inventory'),
    ('integration:reconcile:repair', '补录渠道漏单', '/api/integration/shopify/reconcile/repair', 'POST', 411,
     'Create pending-approval sales orders from reviewed reconciliation events; cannot create customers or products')
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
  AND permission.permission_code IN ('integration:reconcile:view', 'integration:reconcile:repair')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.company_id = 1
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
