-- Controlled platform-administrator enable/disable commands.
-- This migration adds command infrastructure only and does not change any
-- existing account status, role, MFA binding, recovery code or access grant.

ALTER TABLE platform_mfa_challenges
    ALTER COLUMN purpose TYPE VARCHAR(40),
    ADD COLUMN IF NOT EXISTS action_context_hash CHAR(64),
    ADD COLUMN IF NOT EXISTS target_security_version BIGINT;

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL', 'RECOVERY_REGEN',
                       'ADMIN_MFA_RESET', 'ADMIN_INVITE', 'ADMIN_STATUS_CHANGE'));

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_context_hash;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_context_hash
    CHECK (action_context_hash IS NULL OR action_context_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_target_version;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_target_version
    CHECK (target_security_version IS NULL OR target_security_version >= 1);

CREATE TABLE IF NOT EXISTS platform_admin_commands (
    id BIGSERIAL PRIMARY KEY,
    actor_platform_user_id BIGINT NOT NULL,
    target_platform_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    action VARCHAR(30) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    safe_response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_platform_admin_command_actor
        FOREIGN KEY (actor_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_admin_command_target
        FOREIGN KEY (target_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT uk_platform_admin_command_actor_key
        UNIQUE (actor_platform_user_id, idempotency_key),
    CONSTRAINT ck_platform_admin_command_action
        CHECK (action IN ('ADMIN_DISABLE', 'ADMIN_ENABLE')),
    CONSTRAINT ck_platform_admin_command_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_admin_command_status
        CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED')),
    CONSTRAINT ck_platform_admin_command_completion
        CHECK ((status = 'IN_PROGRESS' AND safe_response_json IS NULL AND completed_at IS NULL)
            OR (status = 'SUCCEEDED' AND safe_response_json IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_platform_admin_command_target_created
    ON platform_admin_commands(target_platform_user_id, created_at DESC);

ALTER TABLE platform_audit_logs
    ADD COLUMN IF NOT EXISTS target_platform_user_id BIGINT;

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS fk_platform_audit_target_user;
ALTER TABLE platform_audit_logs ADD CONSTRAINT fk_platform_audit_target_user
    FOREIGN KEY (target_platform_user_id) REFERENCES platform_users(id);

CREATE INDEX IF NOT EXISTS idx_platform_audit_target_user_created
    ON platform_audit_logs(target_platform_user_id, created_at DESC);

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED',
               'ADMIN_DIRECTORY_READ', 'ADMIN_DISABLED', 'ADMIN_ENABLED')
);
