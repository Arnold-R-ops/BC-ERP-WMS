-- V4.58: apply the V4.57 company-user migration inside each company's RLS context.
--
-- V4.57 was recorded successfully while RLS was already enforced for wms_app.
-- Its statements had no app.company_id setting, so PostgreSQL correctly made the
-- company rows invisible. This remedial migration deliberately iterates tenants
-- and sets the transaction-local company context before touching company tables.

DO $$
DECLARE
    company_record RECORD;
    active_legacy_admin_count BIGINT;
    duplicate_target_role_count BIGINT;
BEGIN
    FOR company_record IN SELECT id FROM tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        SELECT COUNT(DISTINCT user_role.user_id)
        INTO active_legacy_admin_count
        FROM sys_user_role user_role
        JOIN sys_role role
          ON role.id = user_role.role_id
         AND role.company_id = user_role.company_id
        JOIN users account
          ON account.id = user_role.user_id
         AND account.company_id = user_role.company_id
        WHERE role.company_id = company_record.id
          AND role.role_code = 'SUPER_ADMIN'
          AND account.enabled = TRUE
          AND account.is_deleted = FALSE;

        IF active_legacy_admin_count > 1 THEN
            RAISE EXCEPTION
                'V4.58 expects at most one active legacy SUPER_ADMIN in company %, found %',
                company_record.id, active_legacy_admin_count;
        END IF;

        SELECT COUNT(*)
        INTO duplicate_target_role_count
        FROM sys_role legacy_role
        JOIN sys_role tenant_role
          ON tenant_role.company_id = legacy_role.company_id
         AND tenant_role.role_code = 'TENANT_ADMIN'
        WHERE legacy_role.company_id = company_record.id
          AND legacy_role.role_code = 'SUPER_ADMIN';

        IF duplicate_target_role_count > 0 THEN
            RAISE EXCEPTION
                'V4.58 cannot merge SUPER_ADMIN where TENANT_ADMIN already exists in company %',
                company_record.id;
        END IF;

        -- Companies already created with TENANT_ADMIN are intentionally untouched.
        IF active_legacy_admin_count = 1 THEN
            UPDATE users account
            SET username = 'deleted_' || account.id || '_' || substring(md5(account.id::text || clock_timestamp()::text), 1, 12),
                displayname = 'Deleted User',
                enabled = FALSE,
                default_role_id = NULL,
                remark = 'Logically deleted by V4.58 company-account migration',
                is_deleted = TRUE,
                deleted_at = CURRENT_TIMESTAMP,
                deleted_by = NULL,
                security_version = GREATEST(COALESCE(account.security_version, 1), 1) + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE account.company_id = company_record.id
              AND account.is_deleted = FALSE
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

            DELETE FROM sys_user_role user_role
            USING users account
            WHERE account.company_id = company_record.id
              AND account.company_id = user_role.company_id
              AND account.id = user_role.user_id
              AND account.is_deleted = TRUE;

            DELETE FROM sys_user_warehouse user_warehouse
            USING users account
            WHERE account.company_id = company_record.id
              AND account.company_id = user_warehouse.company_id
              AND account.id = user_warehouse.user_id
              AND account.is_deleted = TRUE;

            UPDATE tenant_memberships membership
            SET status = 'REVOKED',
                updated_at = CURRENT_TIMESTAMP
            FROM users account
            WHERE membership.tenant_id = company_record.id
              AND account.company_id = company_record.id
              AND account.id = membership.tenant_user_id
              AND account.is_deleted = TRUE
              AND membership.status <> 'REVOKED';

            UPDATE sys_role
            SET role_code = 'TENANT_ADMIN',
                role_name = '公司管理员',
                description = '公司最高管理权限；仅管理所属公司，不具备平台跨公司权限。',
                role_type = 'SYSTEM',
                system_category = 'PRIVILEGED',
                import_allowed = FALSE,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            WHERE company_id = company_record.id
              AND role_code = 'SUPER_ADMIN';

            UPDATE users account
            SET security_version = GREATEST(COALESCE(account.security_version, 1), 1) + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE account.company_id = company_record.id
              AND account.is_deleted = FALSE
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
        END IF;
    END LOOP;
END $$;

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
