-- Platform administrator TOTP MFA. Secrets are encrypted by the application;
-- recovery codes and challenge tokens are stored only as one-way hashes.

ALTER TABLE platform_users
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mfa_secret_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS recovery_code_hashes_json TEXT,
    ADD COLUMN IF NOT EXISTS mfa_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS mfa_locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mfa_enrolled_at TIMESTAMPTZ;

ALTER TABLE platform_users DROP CONSTRAINT IF EXISTS ck_platform_users_mfa_attempts;
ALTER TABLE platform_users ADD CONSTRAINT ck_platform_users_mfa_attempts
    CHECK (mfa_failed_attempts >= 0 AND mfa_failed_attempts <= 5);

-- Revoke pre-MFA platform JWTs when this migration is activated.
UPDATE platform_users SET security_version = security_version + 1;

CREATE TABLE IF NOT EXISTS platform_mfa_challenges (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    platform_user_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    pending_secret_encrypted TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_platform_mfa_challenge_token UNIQUE (token_hash),
    CONSTRAINT fk_platform_mfa_challenge_user FOREIGN KEY (platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT ck_platform_mfa_challenge_purpose CHECK (purpose IN ('ENROLL', 'VERIFY')),
    CONSTRAINT ck_platform_mfa_challenge_attempts CHECK (attempt_count >= 0 AND attempt_count <= 5)
);

CREATE INDEX IF NOT EXISTS idx_platform_mfa_challenge_user_created
    ON platform_mfa_challenges(platform_user_id, created_at);

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET')
);
