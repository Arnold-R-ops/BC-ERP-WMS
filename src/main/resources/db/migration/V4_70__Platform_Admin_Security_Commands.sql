-- Target platform-administrator session revocation and controlled MFA reset.
-- This migration expands command/challenge/audit vocabulary only. It does not
-- update any account, role, MFA binding, recovery code or delegated grant.

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL', 'RECOVERY_REGEN',
                       'ADMIN_MFA_RESET', 'ADMIN_INVITE', 'ADMIN_STATUS_CHANGE',
                       'ADMIN_SESSIONS_REVOKE'));

ALTER TABLE platform_admin_commands DROP CONSTRAINT IF EXISTS ck_platform_admin_command_action;
ALTER TABLE platform_admin_commands ADD CONSTRAINT ck_platform_admin_command_action
    CHECK (action IN ('ADMIN_DISABLE', 'ADMIN_ENABLE',
                      'ADMIN_SESSIONS_REVOKE', 'ADMIN_MFA_RESET'));

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED',
               'ADMIN_DIRECTORY_READ', 'ADMIN_DISABLED', 'ADMIN_ENABLED',
               'ADMIN_SESSIONS_REVOKED')
);
