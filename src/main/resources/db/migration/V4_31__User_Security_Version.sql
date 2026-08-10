-- P0 current-role authorization closure.
-- Existing tokens do not contain security_version, so setting every account to
-- version 1 intentionally requires a fresh login after deployment.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS security_version BIGINT;

UPDATE users
SET security_version = 1
WHERE security_version IS NULL OR security_version < 1;

ALTER TABLE users
    ALTER COLUMN security_version SET DEFAULT 1,
    ALTER COLUMN security_version SET NOT NULL;

COMMENT ON COLUMN users.security_version IS
    'Monotonic JWT authorization context version; incremented after credential, account, role, or effective permission changes.';
