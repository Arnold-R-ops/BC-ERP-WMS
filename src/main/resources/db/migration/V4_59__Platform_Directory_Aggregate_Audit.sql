-- A tenant-directory page is one platform read spanning multiple tenants.
-- Keep the immutable audit record but allow it to have no single target tenant.
ALTER TABLE platform_audit_logs
    ALTER COLUMN target_tenant_id DROP NOT NULL;
