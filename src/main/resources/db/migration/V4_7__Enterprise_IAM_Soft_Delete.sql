-- V4.7 Enterprise IAM: legacy-role compatibility and user soft deletion.

-- The multi-role relation is the only authorization source from V3.3 onward.
-- Keep the legacy column readable for historical compatibility, but stop
-- requiring or validating values written by the new user-management flow.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;

COMMENT ON COLUMN users.role IS
    'Deprecated legacy role. Authorization uses sys_user_role exclusively.';

-- Preserve user identities for historical order/audit references while hiding
-- deleted accounts from active IAM operations.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;

CREATE INDEX IF NOT EXISTS idx_users_active
    ON users (is_deleted, enabled);

CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON users (deleted_at)
    WHERE is_deleted = TRUE;

COMMENT ON COLUMN users.is_deleted IS
    'Logical deletion marker. Deleted users remain for historical audit links.';
COMMENT ON COLUMN users.deleted_at IS
    'UTC timestamp when the user was logically deleted.';
COMMENT ON COLUMN users.deleted_by IS
    'Administrator user ID that performed the logical deletion.';
