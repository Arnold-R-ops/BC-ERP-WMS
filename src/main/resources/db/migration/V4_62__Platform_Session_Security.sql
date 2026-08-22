ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL'));

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED')
);

-- Tokens issued before this migration may still carry the former shared
-- 24-hour expiry. Revoke them once so every subsequent platform token uses
-- the independent 12-hour maximum immediately.
UPDATE platform_users SET security_version = security_version + 1;
