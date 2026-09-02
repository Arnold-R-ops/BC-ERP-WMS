-- V4.79-V4.81 originally seeded these permissions before the database began
-- enforcing that CRITICAL permissions cannot be tenant-assignable. Converge
-- deployed rows before restoring the constraint. Historical script checksum
-- handling is a separate release gate and must never be hidden by this callback.
ALTER TABLE IF EXISTS public.sys_permission
    DROP CONSTRAINT IF EXISTS ck_sys_permission_critical_not_custom;
