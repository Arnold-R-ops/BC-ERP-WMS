CREATE TABLE IF NOT EXISTS platform_admin_invitations (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    invited_by_platform_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_platform_user_id BIGINT,
    revoked_at TIMESTAMPTZ,
    revoked_by_platform_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_platform_admin_invitation_token UNIQUE (token_hash),
    CONSTRAINT fk_platform_admin_invitation_inviter FOREIGN KEY (invited_by_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_admin_invitation_accepted_user FOREIGN KEY (accepted_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_admin_invitation_revoker FOREIGN KEY (revoked_by_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT ck_platform_admin_invitation_role CHECK (role_code = 'PLATFORM_SUPER_ADMIN'),
    CONSTRAINT ck_platform_admin_invitation_period CHECK (expires_at > created_at),
    CONSTRAINT ck_platform_admin_invitation_terminal CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_platform_admin_invitation_email
    ON platform_admin_invitations(normalized_email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_admin_invitation_expiry
    ON platform_admin_invitations(expires_at);

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL', 'RECOVERY_REGEN', 'ADMIN_MFA_RESET', 'ADMIN_INVITE'));

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED')
);
