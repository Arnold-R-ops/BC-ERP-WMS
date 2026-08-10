-- V4.32: Configurable role-template and permission governance metadata.
--
-- Current policy:
-- 1. SUPER_ADMIN is always a protected privileged role and is never importable.
-- 2. Other SYSTEM roles are provisionally classified as business templates,
--    but remain non-importable until their permission baselines are reviewed.
-- 3. Existing CUSTOM roles remain eligible copy sources.
-- 4. IAM/system-control permissions are reserved and cannot be assigned to
--    custom roles.

ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS system_category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS import_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_permission
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS custom_assignable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE sys_role
SET system_category = CASE
        WHEN role_code = 'SUPER_ADMIN' THEN 'PRIVILEGED'
        WHEN role_type = 'SYSTEM' THEN 'BUSINESS_TEMPLATE'
        ELSE NULL
    END,
    import_allowed = CASE
        WHEN role_type = 'CUSTOM' THEN TRUE
        ELSE FALSE
    END;

UPDATE sys_permission
SET risk_level = 'CRITICAL',
    custom_assignable = FALSE
WHERE permission_code IN ('menu:system', 'system:admin')
   OR permission_code LIKE 'system:role:%'
   OR permission_code LIKE 'system:permission:%'
   OR permission_code LIKE 'system:user:%';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_system_category'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_system_category
            CHECK (system_category IS NULL OR system_category IN ('BUSINESS_TEMPLATE', 'PRIVILEGED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_privileged_not_importable'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_privileged_not_importable
            CHECK (system_category IS DISTINCT FROM 'PRIVILEGED' OR import_allowed = FALSE);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_super_admin_governance'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_super_admin_governance
            CHECK (
                role_code <> 'SUPER_ADMIN'
                OR (
                    role_type = 'SYSTEM'
                    AND system_category = 'PRIVILEGED'
                    AND import_allowed = FALSE
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_permission_risk_level'
    ) THEN
        ALTER TABLE sys_permission
            ADD CONSTRAINT ck_sys_permission_risk_level
            CHECK (risk_level IN ('NORMAL', 'HIGH', 'CRITICAL'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_permission_critical_not_custom'
    ) THEN
        ALTER TABLE sys_permission
            ADD CONSTRAINT ck_sys_permission_critical_not_custom
            CHECK (risk_level <> 'CRITICAL' OR custom_assignable = FALSE);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_permission_reserved_policy'
    ) THEN
        ALTER TABLE sys_permission
            ADD CONSTRAINT ck_sys_permission_reserved_policy
            CHECK (
                NOT (
                    permission_code IN ('menu:system', 'system:admin')
                    OR permission_code LIKE 'system:role:%'
                    OR permission_code LIKE 'system:permission:%'
                    OR permission_code LIKE 'system:user:%'
                )
                OR (risk_level = 'CRITICAL' AND custom_assignable = FALSE)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_role_template_governance
    ON sys_role(company_id, system_category, import_allowed);

CREATE INDEX IF NOT EXISTS idx_permission_governance
    ON sys_permission(company_id, risk_level, custom_assignable);

COMMENT ON COLUMN sys_role.system_category IS
    'System-role governance category: BUSINESS_TEMPLATE or PRIVILEGED; null for custom roles.';
COMMENT ON COLUMN sys_role.import_allowed IS
    'Whether this role is approved as a role-copy/import source.';
COMMENT ON COLUMN sys_permission.risk_level IS
    'Permission governance risk: NORMAL, HIGH, or CRITICAL.';
COMMENT ON COLUMN sys_permission.custom_assignable IS
    'Whether the permission may be assigned to CUSTOM roles.';
