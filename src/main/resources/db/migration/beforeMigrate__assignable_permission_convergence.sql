-- V4.79-V4.81 were authored before the database began enforcing that
-- CRITICAL permissions cannot be tenant-assignable. Keep their immutable
-- checksums while allowing the current release to converge those rows.
ALTER TABLE IF EXISTS public.sys_permission
    DROP CONSTRAINT IF EXISTS ck_sys_permission_critical_not_custom;
