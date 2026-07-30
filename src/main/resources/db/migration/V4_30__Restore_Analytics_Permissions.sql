-- Some upgraded installations predate the current RBAC seed and therefore do
-- not contain the reports menu or global read permission. Restore them before
-- assigning the P2 analytics access.

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    menu_url, menu_icon, sort_order, status, description, created_at, updated_at
)
SELECT
    1, 'menu:reports', '经营分析', 'MENU',
    '/reports', 'BarChartOutlined', 50, 'ACTIVE',
    'Sales and customer analytics workspace',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE company_id = 1 AND permission_code = 'menu:reports'
);

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, sort_order, status,
    description, created_at, updated_at
)
SELECT
    1, 'global:view', '查看经营分析', 'API',
    '/api/reports/**', 'GET',
    (SELECT id
     FROM sys_permission
     WHERE company_id = 1 AND permission_code = 'menu:reports'),
    1, 'ACTIVE', 'Read sales and customer analytics',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE company_id = 1 AND permission_code = 'global:view'
);

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = role.company_id
WHERE role.company_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'GENERAL_MANAGER')
  AND permission.permission_code IN ('menu:reports', 'global:view')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = role.company_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
