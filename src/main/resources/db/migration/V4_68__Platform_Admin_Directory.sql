-- Read-only platform administrator directory support.
-- This migration changes no platform account, role, MFA or access-grant rows.

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED',
               'ADMIN_DIRECTORY_READ')
);

CREATE INDEX IF NOT EXISTS idx_platform_users_admin_directory
    ON platform_users(created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_platform_user_roles_role_user
    ON platform_user_roles(platform_role_id, platform_user_id);

CREATE INDEX IF NOT EXISTS idx_platform_access_grants_active_grantee
    ON platform_access_grants(grantee_platform_user_id, capability, effective_from, expires_at)
    WHERE revoked_at IS NULL;
