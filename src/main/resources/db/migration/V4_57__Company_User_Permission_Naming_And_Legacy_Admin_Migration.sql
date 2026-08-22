-- V4.57: company-user permission naming and legacy administrator migration.
--
-- Confirmed policy:
-- 1. TENANT_ADMIN is the only effective top-level role inside a company.
-- 2. The single legacy SUPER_ADMIN becomes TENANT_ADMIN without changing its
--    user id, company, password or business ownership.
-- 3. Every other active company user is logically deleted. Historical business
--    records remain intact; deleted identities cannot log in or consume seats.
-- 4. Platform identities remain exclusively in platform_users and are not
--    changed by this migration.

DO $$
DECLARE
    active_legacy_admin_count BIGINT;
    duplicate_target_role_count BIGINT;
BEGIN
    SELECT COUNT(DISTINCT user_role.user_id)
    INTO active_legacy_admin_count
    FROM sys_user_role user_role
    JOIN sys_role role
      ON role.id = user_role.role_id
     AND role.company_id = user_role.company_id
    JOIN users account
      ON account.id = user_role.user_id
     AND account.company_id = user_role.company_id
    WHERE role.role_code = 'SUPER_ADMIN'
      AND account.enabled = TRUE
      AND account.is_deleted = FALSE;

    IF active_legacy_admin_count > 1 THEN
        RAISE EXCEPTION
            'V4.57 expects at most one active legacy SUPER_ADMIN, found %',
            active_legacy_admin_count;
    END IF;

    SELECT COUNT(*)
    INTO duplicate_target_role_count
    FROM sys_role legacy_role
    JOIN sys_role tenant_role
      ON tenant_role.company_id = legacy_role.company_id
     AND tenant_role.role_code = 'TENANT_ADMIN'
    WHERE legacy_role.role_code = 'SUPER_ADMIN';

    IF duplicate_target_role_count > 0 THEN
        RAISE EXCEPTION
            'V4.57 cannot merge SUPER_ADMIN where TENANT_ADMIN already exists in the same company';
    END IF;
END $$;

-- Soft-delete every active company account other than the confirmed legacy
-- administrator before renaming its role. The old user id and business history
-- remain available for audit and foreign-key references.
UPDATE users account
SET username = 'deleted_' || account.id || '_' || substring(md5(account.id::text || clock_timestamp()::text), 1, 12),
    displayname = 'Deleted User',
    enabled = FALSE,
    default_role_id = NULL,
    remark = 'Logically deleted by V4.57 company-account migration',
    is_deleted = TRUE,
    deleted_at = CURRENT_TIMESTAMP,
    deleted_by = NULL,
    security_version = GREATEST(COALESCE(account.security_version, 1), 1) + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE account.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role user_role
      JOIN sys_role role
        ON role.id = user_role.role_id
       AND role.company_id = user_role.company_id
      WHERE user_role.company_id = account.company_id
        AND user_role.user_id = account.id
        AND role.role_code = 'SUPER_ADMIN'
  );

-- Deleted company accounts retain historical business references but no longer
-- retain assignable roles, warehouse scope or live company membership.
DELETE FROM sys_user_role user_role
USING users account
WHERE account.company_id = user_role.company_id
  AND account.id = user_role.user_id
  AND account.is_deleted = TRUE;

DELETE FROM sys_user_warehouse user_warehouse
USING users account
WHERE account.company_id = user_warehouse.company_id
  AND account.id = user_warehouse.user_id
  AND account.is_deleted = TRUE;

UPDATE tenant_memberships membership
SET status = 'REVOKED',
    updated_at = CURRENT_TIMESTAMP
FROM users account
WHERE account.company_id = membership.tenant_id
  AND account.id = membership.tenant_user_id
  AND account.is_deleted = TRUE
  AND membership.status <> 'REVOKED';

-- Preserve the legacy administrator's role id and granted permissions while
-- replacing the ambiguous role code with the company-scoped administrator code.
UPDATE sys_role
SET role_code = 'TENANT_ADMIN',
    role_name = '公司管理员',
    description = '公司最高管理权限；仅管理所属公司，不具备平台跨公司权限。',
    role_type = 'SYSTEM',
    system_category = 'PRIVILEGED',
    import_allowed = FALSE,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'SUPER_ADMIN';

-- A role-code change invalidates existing company JWTs for the retained
-- administrator, forcing a new token with current_role=TENANT_ADMIN.
UPDATE users account
SET security_version = GREATEST(COALESCE(account.security_version, 1), 1) + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE account.is_deleted = FALSE
  AND EXISTS (
      SELECT 1
      FROM sys_user_role user_role
      JOIN sys_role role
        ON role.id = user_role.role_id
       AND role.company_id = user_role.company_id
      WHERE user_role.company_id = account.company_id
        AND user_role.user_id = account.id
        AND role.role_code = 'TENANT_ADMIN'
  );

ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS ck_sys_role_super_admin_governance;
ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS ck_sys_role_tenant_admin_governance;
ALTER TABLE sys_role ADD CONSTRAINT ck_sys_role_tenant_admin_governance
    CHECK (
        role_code <> 'TENANT_ADMIN'
        OR (
            role_type = 'SYSTEM'
            AND system_category = 'PRIVILEGED'
            AND import_allowed = FALSE
        )
    );

UPDATE sys_approval_template
SET description = replace(description, '超级管理员', '公司管理员'),
    config_json = replace(config_json, 'SUPER_ADMIN', 'TENANT_ADMIN'),
    updated_at = CURRENT_TIMESTAMP
WHERE config_json LIKE '%SUPER_ADMIN%'
   OR description LIKE '%超级管理员%';

COMMENT ON TABLE users IS
    'Company user accounts only. Platform administrators are stored exclusively in platform_users.';
COMMENT ON TABLE platform_users IS
    'Platform-only accounts. They never belong to a company and must not be returned by company IAM queries.';
