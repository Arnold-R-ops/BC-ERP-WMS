-- Audit rows retain source/target IDs and codes as immutable snapshots.
-- They must not block later deletion of an otherwise deletable custom role.

ALTER TABLE sys_role_copy_audit
    DROP CONSTRAINT IF EXISTS fk_role_copy_audit_source_role,
    DROP CONSTRAINT IF EXISTS fk_role_copy_audit_target_role;

COMMENT ON COLUMN sys_role_copy_audit.source_role_id IS
    'Source role ID snapshot; intentionally not a lifecycle-blocking foreign key';

COMMENT ON COLUMN sys_role_copy_audit.target_role_id IS
    'Target role ID snapshot; intentionally not a lifecycle-blocking foreign key';

