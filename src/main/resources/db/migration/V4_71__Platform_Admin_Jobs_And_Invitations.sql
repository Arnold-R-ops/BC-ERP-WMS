-- T6 platform administrator jobs, ordinary invitations and atomic activation staging.
-- This expand migration does not update platform users, their role assignments,
-- MFA bindings, recovery codes or delegated access grants.

ALTER TABLE platform_roles DROP CONSTRAINT IF EXISTS ck_platform_roles_code;
ALTER TABLE platform_roles ADD CONSTRAINT ck_platform_roles_code CHECK (
    role_code IN ('PLATFORM_SUPER_ADMIN',
                  'PLATFORM_OPERATIONS_ADMIN', 'PLATFORM_SECURITY_AUDITOR',
                  'PLATFORM_TENANT_READ', 'PLATFORM_TENANT_EXPORT',
                  'PLATFORM_TENANT_WRITE', 'PLATFORM_TENANT_DELETE')
);

INSERT INTO platform_roles(role_code, display_name) VALUES
    ('PLATFORM_OPERATIONS_ADMIN', '平台运营管理员'),
    ('PLATFORM_SECURITY_AUDITOR', '安全审计员')
ON CONFLICT (role_code) DO NOTHING;

ALTER TABLE platform_admin_invitations
    ADD COLUMN IF NOT EXISTS invitation_type VARCHAR(20);

UPDATE platform_admin_invitations
SET invitation_type = 'SUPER_ADMIN'
WHERE invitation_type IS NULL;

ALTER TABLE platform_admin_invitations
    ALTER COLUMN invitation_type SET NOT NULL;

ALTER TABLE platform_admin_invitations
    DROP CONSTRAINT IF EXISTS ck_platform_admin_invitation_type;
ALTER TABLE platform_admin_invitations
    ADD CONSTRAINT ck_platform_admin_invitation_type
    CHECK (invitation_type IN ('SUPER_ADMIN', 'ORDINARY_ADMIN'));

ALTER TABLE platform_admin_invitations
    DROP CONSTRAINT IF EXISTS ck_platform_admin_invitation_role;
ALTER TABLE platform_admin_invitations
    ADD CONSTRAINT ck_platform_admin_invitation_role CHECK (
        (invitation_type = 'SUPER_ADMIN' AND role_code = 'PLATFORM_SUPER_ADMIN')
        OR
        (invitation_type = 'ORDINARY_ADMIN'
            AND role_code IN ('PLATFORM_OPERATIONS_ADMIN', 'PLATFORM_SECURITY_AUDITOR'))
    );

CREATE TABLE platform_admin_invitation_active_emails (
    normalized_email VARCHAR(254) PRIMARY KEY,
    invitation_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_admin_invitation_active_email
        FOREIGN KEY (invitation_id) REFERENCES platform_admin_invitations(id),
    CONSTRAINT ck_platform_admin_invitation_active_email_normalized
        CHECK (normalized_email = lower(btrim(normalized_email)))
);

INSERT INTO platform_admin_invitation_active_emails(normalized_email, invitation_id, created_at)
SELECT normalized_email, id, created_at
FROM platform_admin_invitations
WHERE accepted_at IS NULL
  AND revoked_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP;

CREATE TABLE platform_admin_invitation_activations (
    id BIGSERIAL PRIMARY KEY,
    invitation_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    pending_secret_encrypted TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_admin_invitation_activation
        FOREIGN KEY (invitation_id) REFERENCES platform_admin_invitations(id),
    CONSTRAINT uk_platform_admin_invitation_activation_token UNIQUE (token_hash),
    CONSTRAINT ck_platform_admin_invitation_activation_token
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_admin_invitation_activation_attempts
        CHECK (attempt_count >= 0 AND attempt_count <= 5),
    CONSTRAINT ck_platform_admin_invitation_activation_period
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_platform_admin_invitation_activation_invitation_created
    ON platform_admin_invitation_activations(invitation_id, created_at DESC);

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL', 'RECOVERY_REGEN',
                       'ADMIN_MFA_RESET', 'ADMIN_INVITE', 'ADMIN_STATUS_CHANGE',
                       'ADMIN_SESSIONS_REVOKE', 'ADMIN_ROLES_CHANGE'));

ALTER TABLE platform_admin_commands DROP CONSTRAINT IF EXISTS ck_platform_admin_command_action;
ALTER TABLE platform_admin_commands ADD CONSTRAINT ck_platform_admin_command_action
    CHECK (action IN ('ADMIN_DISABLE', 'ADMIN_ENABLE',
                      'ADMIN_SESSIONS_REVOKE', 'ADMIN_MFA_RESET',
                      'ADMIN_ROLES_CHANGE'));

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED',
               'ADMIN_DIRECTORY_READ', 'ADMIN_DISABLED', 'ADMIN_ENABLED',
               'ADMIN_SESSIONS_REVOKED', 'ADMIN_ROLES_CHANGED')
);
