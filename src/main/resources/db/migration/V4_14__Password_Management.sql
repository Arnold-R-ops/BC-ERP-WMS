-- ====================================================================
-- V4.14: Password management (P0.5)
--
-- Adds users.must_change_password:
-- - Set to TRUE when an administrator resets an account's password to a
--   temporary value (POST /api/users/{id}/reset-password).
-- - Cleared when the user changes the password themselves
--   (PUT /api/users/me/password).
-- - While TRUE, authorization restricts the account to the
--   change-password endpoint (enforced in DynamicAuthorizationManager),
--   so a temporary password cannot be used to operate the system.
-- ====================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'must_change_password'
    ) THEN
        ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

COMMENT ON COLUMN users.must_change_password IS
    'TRUE after an admin password reset; the account may only call the change-password endpoint until the user sets a new password';
