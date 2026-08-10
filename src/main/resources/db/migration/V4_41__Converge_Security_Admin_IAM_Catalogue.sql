-- V4.41: Converge IAM catalogue permissions on upgraded installations.
-- Some databases predate the complete V2 permission seed, so V4.39 could only
-- grant the new user permissions. Restore and protect the missing IAM catalogue.

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    menu_url, menu_icon, sort_order, status, description,
    risk_level, custom_assignable, created_at, updated_at
)
SELECT
    1, 'menu:system', '系统管理', 'MENU',
    '/system', 'icon-system', 5, 'ACTIVE', 'IAM administration workspace',
    'CRITICAL', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE company_id = 1 AND permission_code = 'menu:system'
);

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, data_scope,
    risk_level, custom_assignable, description, status, sort_order,
    created_at, updated_at
)
SELECT
    1,
    permission_code,
    permission_name,
    'API',
    resource_path,
    http_method,
    (SELECT id FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:system'),
    'ALL',
    'CRITICAL',
    FALSE,
    description,
    'ACTIVE',
    sort_order,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('system:role:view', '查看角色', '/api/roles', 'GET', 1, 'View the IAM role catalogue'),
    ('system:role:create', '复制角色', '/api/roles/*/copies', 'POST', 2, 'Copy an approved ordinary role package'),
    ('system:role:update', '管理角色', '/api/roles/**', '*', 3, 'Manage ordinary role package governance'),
    ('system:permission:view', '查看权限', '/api/permissions', 'GET', 4, 'View the IAM permission catalogue')
) AS permission_seed(
    permission_code,
    permission_name,
    resource_path,
    http_method,
    sort_order,
    description
)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = permission_seed.permission_code
);

UPDATE sys_permission
SET resource_path = CASE permission_code
        WHEN 'system:role:view' THEN '/api/roles'
        WHEN 'system:role:create' THEN '/api/roles/*/copies'
        WHEN 'system:role:update' THEN '/api/roles/**'
        WHEN 'system:permission:view' THEN '/api/permissions'
        ELSE resource_path
    END,
    http_method = CASE permission_code
        WHEN 'system:role:view' THEN 'GET'
        WHEN 'system:role:create' THEN 'POST'
        WHEN 'system:role:update' THEN '*'
        WHEN 'system:permission:view' THEN 'GET'
        ELSE http_method
    END,
    risk_level = 'CRITICAL',
    custom_assignable = FALSE,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND permission_code IN (
      'system:role:view',
      'system:role:create',
      'system:role:update',
      'system:permission:view'
  );

INSERT INTO sys_role_permission (
    company_id, role_id, permission_id, granted_at
)
SELECT
    role.company_id,
    role.id,
    permission.id,
    CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission
  ON permission.company_id = role.company_id
WHERE role.company_id = 1
  AND role.role_code = 'SECURITY_ADMIN'
  AND permission.permission_code IN (
      'menu:system',
      'system:role:view',
      'system:role:create',
      'system:role:update',
      'system:permission:view'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = role.company_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
