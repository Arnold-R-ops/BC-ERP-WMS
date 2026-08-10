-- Add a protected SECURITY_ADMIN identity with narrowly scoped IAM access.
--
-- Confirmed policy A:
-- 1. SECURITY_ADMIN has no global authorization bypass.
-- 2. It may administer ordinary identities and ordinary-risk role packages.
-- 3. It may not grant or modify SUPER_ADMIN / SECURITY_ADMIN identities.
-- 4. A HIGH-risk role package still requires another SUPER_ADMIN to approve it.

INSERT INTO sys_role (
    company_id, role_code, role_name, description, role_type,
    system_category, import_allowed, approval_template_code, review_status,
    status, sort_order, created_at, updated_at
)
SELECT
    1,
    'SECURITY_ADMIN',
    '安全管理员',
    '管理普通用户、普通权限包审批和安全审计；无全局绕过，不能管理受保护特权身份。',
    'SYSTEM',
    'PRIVILEGED',
    FALSE,
    NULL,
    'APPROVED',
    'ACTIVE',
    2,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE company_id = 1 AND role_code = 'SECURITY_ADMIN'
);

UPDATE sys_role
SET role_type = 'SYSTEM',
    system_category = 'PRIVILEGED',
    import_allowed = FALSE,
    approval_template_code = NULL,
    review_status = 'APPROVED',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND role_code = 'SECURITY_ADMIN';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_sys_role_privileged_system_protected'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_privileged_system_protected
            CHECK (
                system_category IS DISTINCT FROM 'PRIVILEGED'
                OR (role_type = 'SYSTEM' AND import_allowed = FALSE)
            );
    END IF;
END $$;

-- Align the historical IAM catalogue with the controller paths used today.
UPDATE sys_permission
SET resource_path = CASE permission_code
        WHEN 'system:role:view' THEN '/api/roles/**'
        WHEN 'system:role:create' THEN '/api/roles/*/copies'
        WHEN 'system:role:update' THEN '/api/roles/**'
        WHEN 'system:permission:view' THEN '/api/permissions/**'
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
    ('system:user:view', '查看用户', '/api/users', 'GET', 20, 'View the IAM user catalogue'),
    ('system:user:create', '创建普通用户', '/api/users', 'POST', 21, 'Create an ordinary user account'),
    ('system:user:update', '更新普通用户', '/api/users/*', 'PUT', 22, 'Update an ordinary user profile'),
    ('system:user:roles:update', '分配普通角色', '/api/users/*/roles', 'POST', 23, 'Replace ordinary user role assignments'),
    ('system:user:roles:remove', '移除普通角色', '/api/users/*/roles/*', 'DELETE', 24, 'Remove one ordinary user role assignment'),
    ('system:user:reset-password', '重置普通用户密码', '/api/users/*/reset-password', 'POST', 25, 'Reset an ordinary user password'),
    ('system:user:delete', '停用并删除普通用户', '/api/users/*', 'DELETE', 26, 'Logically delete an ordinary user account')
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

-- Converge pre-existing rows as well as newly inserted rows to the protected policy.
UPDATE sys_permission
SET risk_level = 'CRITICAL',
    custom_assignable = FALSE,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND permission_code LIKE 'system:user:%';

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
      'system:permission:view',
      'system:user:view',
      'system:user:create',
      'system:user:update',
      'system:user:roles:update',
      'system:user:roles:remove',
      'system:user:reset-password',
      'system:user:delete'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = role.company_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

UPDATE sys_approval_template
SET description = '单级复核：普通风险允许提交人复核；高风险必须由另一名超级管理员复核。',
    config_json = '{"stages":1,"normalRiskSelfReview":true,"highRiskRequiresDifferentReviewer":true,"highRiskApproverRole":"SUPER_ADMIN"}',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND template_code = 'ROLE_PACKAGE_SIMPLE_APPROVAL';

COMMENT ON CONSTRAINT ck_sys_role_privileged_system_protected ON sys_role IS
    'Every privileged identity is a protected, non-importable SYSTEM role.';
