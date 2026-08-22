CREATE TABLE IF NOT EXISTS platform_access_grants (
    id BIGSERIAL PRIMARY KEY,
    grantee_platform_user_id BIGINT NOT NULL,
    granted_by_platform_user_id BIGINT NOT NULL,
    capability VARCHAR(20) NOT NULL,
    tenant_id BIGINT NOT NULL,
    dataset_code VARCHAR(80) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_platform_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_access_grant_grantee FOREIGN KEY (grantee_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_access_grant_grantor FOREIGN KEY (granted_by_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_access_grant_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_platform_access_grant_revoker FOREIGN KEY (revoked_by_platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT ck_platform_access_grant_capability CHECK (capability IN ('READ', 'EXPORT')),
    CONSTRAINT ck_platform_access_grant_dataset CHECK (dataset_code IN ('users', 'roles', 'warehouses', 'products', 'inventory', 'sales_orders')),
    CONSTRAINT ck_platform_access_grant_period CHECK (expires_at > effective_from),
    CONSTRAINT ck_platform_access_grant_revoke CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_platform_access_grant_lookup
    ON platform_access_grants(grantee_platform_user_id, capability, tenant_id, dataset_code, effective_from, expires_at);

ALTER TABLE platform_audit_logs DROP CONSTRAINT IF EXISTS ck_platform_audit_action;
-- Some development databases may already contain immutable audit rows written
-- by newer platform identity features before this migration is applied. Keep
-- the constraint forward-compatible so those rows never need to be rewritten
-- or deleted merely to complete the control-plane schema upgrade.
ALTER TABLE platform_audit_logs ADD CONSTRAINT ck_platform_audit_action CHECK (
    action IN ('READ', 'EXPORT', 'WRITE', 'DELETE', 'AUDIT_READ',
               'OPERATION_REQUESTED', 'OPERATION_EXECUTED',
               'MFA_ENROLLED', 'MFA_VERIFIED', 'MFA_FAILED',
               'MFA_RECOVERY_USED', 'MFA_RECOVERY_REGENERATED', 'MFA_RESET',
               'ACCESS_GRANTED', 'ACCESS_REVOKED', 'SESSIONS_REVOKED',
               'ADMIN_INVITED', 'ADMIN_INVITATION_REVOKED', 'ADMIN_INVITATION_ACCEPTED')
);
