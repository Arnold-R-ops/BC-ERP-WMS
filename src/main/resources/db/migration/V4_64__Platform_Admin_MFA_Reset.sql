-- Bind a privileged MFA reset challenge to one explicit target administrator.
-- Business/export jobs are intentionally outside this identity-only migration.
ALTER TABLE platform_mfa_challenges
    ADD COLUMN IF NOT EXISTS target_platform_user_id BIGINT;

ALTER TABLE platform_mfa_challenges
    DROP CONSTRAINT IF EXISTS fk_platform_mfa_challenge_target_user;
ALTER TABLE platform_mfa_challenges
    ADD CONSTRAINT fk_platform_mfa_challenge_target_user
        FOREIGN KEY (target_platform_user_id) REFERENCES platform_users(id);

CREATE INDEX IF NOT EXISTS idx_platform_mfa_challenge_target_user
    ON platform_mfa_challenges(target_platform_user_id);

ALTER TABLE platform_mfa_challenges DROP CONSTRAINT IF EXISTS ck_platform_mfa_challenge_purpose;
ALTER TABLE platform_mfa_challenges ADD CONSTRAINT ck_platform_mfa_challenge_purpose
    CHECK (purpose IN ('ENROLL', 'VERIFY', 'LOGOUT_ALL', 'RECOVERY_REGEN', 'ADMIN_MFA_RESET'));
