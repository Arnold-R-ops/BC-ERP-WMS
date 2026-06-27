-- V4.5.1: Ensure admin user has SUPER_ADMIN role.
-- Use NOT EXISTS instead of ON CONFLICT so this migration remains compatible
-- with both legacy single-column unique keys and future tenant composite keys.

INSERT INTO sys_role (role_code, role_name, role_type, status, created_at, updated_at)
SELECT 'SUPER_ADMIN', 'Super Administrator', 'SYSTEM', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE role_code = 'SUPER_ADMIN'
);

INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
SELECT
    u.id,
    r.id,
    u.id,
    NOW()
FROM users u
JOIN sys_role r ON r.role_code = 'SUPER_ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role sur
      WHERE sur.user_id = u.id
        AND sur.role_id = r.id
  );
