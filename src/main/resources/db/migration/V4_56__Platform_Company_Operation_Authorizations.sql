-- Company-approved, one-time platform WRITE/DELETE capability.

ALTER TABLE platform_operation_authorizations
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS requested_by_platform_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS resource_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS resource_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS request_payload_json TEXT,
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS executed_by_platform_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS execution_key VARCHAR(80);

-- Existing V4.50 rows were not executable capabilities; preserve as historical expired records.
UPDATE platform_operation_authorizations
SET public_id = COALESCE(public_id, md5(id::text || ':legacy-auth')),
    status = CASE WHEN revoked_at IS NOT NULL THEN 'REVOKED' ELSE 'EXPIRED' END,
    resource_type = COALESCE(resource_type, 'legacy'),
    resource_id = COALESCE(resource_id, id::text),
    request_payload_json = COALESCE(request_payload_json, '{}'),
    request_fingerprint = COALESCE(request_fingerprint, md5(id::text || ':legacy-fingerprint'));

ALTER TABLE platform_operation_authorizations
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN resource_type SET NOT NULL,
    ALTER COLUMN resource_id SET NOT NULL,
    ALTER COLUMN request_payload_json SET NOT NULL,
    ALTER COLUMN request_fingerprint SET NOT NULL,
    ALTER COLUMN approved_by_tenant_user_id DROP NOT NULL,
    ALTER COLUMN approved_at DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_platform_operation_auth_public_id') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT uk_platform_operation_auth_public_id UNIQUE (public_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_platform_operation_auth_requester') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT fk_platform_operation_auth_requester
            FOREIGN KEY (requested_by_platform_user_id) REFERENCES platform_users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_platform_operation_auth_executor') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT fk_platform_operation_auth_executor
            FOREIGN KEY (executed_by_platform_user_id) REFERENCES platform_users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_platform_operation_auth_status') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT ck_platform_operation_auth_status CHECK (
            status IN ('PENDING','APPROVED','REVOKED','EXPIRED','CONSUMED','FAILED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_platform_operation_auth_resource_type') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT ck_platform_operation_auth_resource_type CHECK (
            resource_type IN ('product','customer','legacy'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_platform_operation_auth_payload_json') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT ck_platform_operation_auth_payload_json CHECK (request_payload_json IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_platform_operation_auth_consumed') THEN
        ALTER TABLE platform_operation_authorizations ADD CONSTRAINT ck_platform_operation_auth_consumed CHECK (
            consumed_at IS NULL OR (status IN ('CONSUMED','FAILED') AND executed_by_platform_user_id IS NOT NULL));
    END IF;
END $$;

CREATE UNIQUE INDEX uk_platform_operation_auth_execution_key
    ON platform_operation_authorizations(execution_key)
    WHERE execution_key IS NOT NULL;
CREATE INDEX idx_platform_operation_auth_company_status
    ON platform_operation_authorizations(tenant_id, status, created_at DESC);

ALTER TABLE platform_roles DROP CONSTRAINT IF EXISTS ck_platform_roles_code;
ALTER TABLE platform_roles ADD CONSTRAINT ck_platform_roles_code CHECK (
    role_code IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_TENANT_READ', 'PLATFORM_TENANT_EXPORT',
                  'PLATFORM_TENANT_WRITE', 'PLATFORM_TENANT_DELETE')
);

INSERT INTO platform_roles(role_code, display_name) VALUES
    ('PLATFORM_TENANT_WRITE', '公司数据修改'),
    ('PLATFORM_TENANT_DELETE', '公司数据删除')
ON CONFLICT (role_code) DO NOTHING;

COMMENT ON TABLE platform_operation_authorizations IS
    'One-time fixed-scope company approval for platform write/delete; not a general database permission';
