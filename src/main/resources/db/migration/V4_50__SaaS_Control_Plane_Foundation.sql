-- V4.50: SaaS shared-database control-plane foundation.
--
-- Product term: company. Technical term: tenant. Existing business tables keep
-- company_id, whose authoritative root is tenants.id.

CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    slug VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROVISIONING',
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Shanghai',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN',
    closed_at TIMESTAMPTZ,
    purge_due_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenants_tenant_code UNIQUE (tenant_code),
    CONSTRAINT uk_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_slug CHECK (
        slug ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'
        AND slug NOT IN ('www', 'app', 'api', 'platform', 'admin', 'support',
                         'mail', 'static', 'cdn', 'status', 'help')
    ),
    CONSTRAINT ck_tenants_status CHECK (
        status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'PURGE_PENDING', 'PURGED')
    ),
    CONSTRAINT ck_tenants_retention CHECK (
        (status NOT IN ('CLOSED', 'PURGE_PENDING', 'PURGED')
            AND closed_at IS NULL AND purge_due_at IS NULL)
        OR
        (status IN ('CLOSED', 'PURGE_PENDING', 'PURGED')
            AND closed_at IS NOT NULL AND purge_due_at IS NOT NULL
            AND purge_due_at = closed_at + INTERVAL '30 days')
    )
);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_purge_due_at ON tenants(purge_due_at);

-- Register the historical single-company dataset as the immutable legacy tenant.
INSERT INTO tenants (
    id, tenant_code, display_name, slug, status, timezone, locale
) VALUES (
    1, 'COMPANY-000001', '默认公司', 'legacy-company', 'ACTIVE', 'Asia/Shanghai', 'zh-CN'
) ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('tenants', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM tenants), 1),
    true
);

CREATE TABLE IF NOT EXISTS tenant_domains (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    hostname VARCHAR(253) NOT NULL,
    domain_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verification_token_hash VARCHAR(128),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_domains_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_tenant_domains_hostname UNIQUE (hostname),
    CONSTRAINT ck_tenant_domains_hostname_normalized CHECK (
        hostname = lower(btrim(hostname))
    ),
    CONSTRAINT ck_tenant_domains_type CHECK (domain_type IN ('PLATFORM_SUBDOMAIN', 'CUSTOM_DOMAIN')),
    CONSTRAINT ck_tenant_domains_status CHECK (status IN ('PENDING', 'VERIFIED', 'ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_domains_tenant ON tenant_domains(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_domains_status ON tenant_domains(status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_domains_primary
    ON tenant_domains(tenant_id) WHERE is_primary;

CREATE TABLE IF NOT EXISTS user_identities (
    id BIGSERIAL PRIMARY KEY,
    normalized_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at TIMESTAMPTZ NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_identities_email UNIQUE (normalized_email),
    CONSTRAINT ck_user_identities_security_version CHECK (security_version > 0),
    CONSTRAINT ck_user_identities_email_normalized CHECK (normalized_email = lower(btrim(normalized_email)))
);

-- A membership must point to a tenant user belonging to the same company.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_company_id
    ON users(company_id, id);

CREATE TABLE IF NOT EXISTS tenant_memberships (
    id BIGSERIAL PRIMARY KEY,
    identity_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    tenant_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_memberships_identity FOREIGN KEY (identity_id) REFERENCES user_identities(id),
    CONSTRAINT fk_tenant_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tenant_memberships_user FOREIGN KEY (tenant_id, tenant_user_id)
        REFERENCES users(company_id, id),
    CONSTRAINT uk_tenant_memberships_identity_tenant UNIQUE (identity_id, tenant_id),
    CONSTRAINT uk_tenant_memberships_tenant_user UNIQUE (tenant_id, tenant_user_id),
    CONSTRAINT ck_tenant_memberships_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_memberships_tenant ON tenant_memberships(tenant_id);
-- First-release policy: one email identity may belong to only one live company.
-- Dropping this policy index later enables multi-company membership without
-- replacing identities, passwords, MFA or tenant-bound JWTs.
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_memberships_single_live_company
    ON tenant_memberships(identity_id)
    WHERE status IN ('INVITED', 'ACTIVE', 'SUSPENDED');

CREATE TABLE IF NOT EXISTS platform_users (
    id BIGSERIAL PRIMARY KEY,
    normalized_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_platform_users_email UNIQUE (normalized_email),
    CONSTRAINT ck_platform_users_security_version CHECK (security_version > 0),
    CONSTRAINT ck_platform_users_email_normalized CHECK (normalized_email = lower(btrim(normalized_email)))
);

CREATE TABLE IF NOT EXISTS platform_roles (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_platform_roles_code UNIQUE (role_code),
    CONSTRAINT ck_platform_roles_code CHECK (
        role_code IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_TENANT_READ', 'PLATFORM_TENANT_EXPORT')
    )
);

CREATE TABLE IF NOT EXISTS platform_user_roles (
    id BIGSERIAL PRIMARY KEY,
    platform_user_id BIGINT NOT NULL,
    platform_role_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_user_roles_user FOREIGN KEY (platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_user_roles_role FOREIGN KEY (platform_role_id) REFERENCES platform_roles(id),
    CONSTRAINT uk_platform_user_roles_user_role UNIQUE (platform_user_id, platform_role_id)
);

INSERT INTO platform_roles (role_code, display_name)
VALUES
    ('PLATFORM_SUPER_ADMIN', '平台超级管理员'),
    ('PLATFORM_TENANT_READ', '公司数据读取'),
    ('PLATFORM_TENANT_EXPORT', '公司数据导出')
ON CONFLICT (role_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS platform_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    platform_user_id BIGINT NOT NULL,
    target_tenant_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    resource_type VARCHAR(80),
    resource_id VARCHAR(100),
    request_id VARCHAR(100),
    request_ip VARCHAR(64),
    user_agent VARCHAR(500),
    result VARCHAR(30) NOT NULL,
    detail_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_audit_user FOREIGN KEY (platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_platform_audit_action CHECK (action IN ('READ', 'EXPORT', 'WRITE', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_tenant_created
    ON platform_audit_logs(target_tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_platform_audit_actor_created
    ON platform_audit_logs(platform_user_id, created_at);

CREATE OR REPLACE FUNCTION _wms_reject_platform_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_platform_audit_append_only ON platform_audit_logs;
CREATE TRIGGER trg_platform_audit_append_only
BEFORE UPDATE OR DELETE ON platform_audit_logs
FOR EACH ROW EXECUTE FUNCTION _wms_reject_platform_audit_mutation();

CREATE TABLE IF NOT EXISTS platform_operation_authorizations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    resource_scope VARCHAR(100) NOT NULL,
    approved_by_tenant_user_id BIGINT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_operation_auth_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_platform_operation_auth_approver FOREIGN KEY (tenant_id, approved_by_tenant_user_id)
        REFERENCES users(company_id, id),
    CONSTRAINT ck_platform_operation_auth_type CHECK (operation_type IN ('WRITE', 'DELETE')),
    CONSTRAINT ck_platform_operation_auth_expiry CHECK (expires_at > approved_at),
    CONSTRAINT ck_platform_operation_auth_revoked CHECK (
        revoked_at IS NULL OR revoked_at >= approved_at
    )
);

CREATE INDEX IF NOT EXISTS idx_platform_operation_auth_tenant_expiry
    ON platform_operation_authorizations(tenant_id, expires_at);

CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(30) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    trial_days INTEGER,
    fallback_plan_code VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subscription_plans_code UNIQUE (plan_code),
    CONSTRAINT ck_subscription_plans_code CHECK (plan_code IN ('FREE', 'TRIAL')),
    CONSTRAINT ck_subscription_plans_trial_days CHECK (trial_days IS NULL OR trial_days > 0)
);

CREATE TABLE IF NOT EXISTS plan_limits (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    limit_key VARCHAR(80) NOT NULL,
    limit_kind VARCHAR(20) NOT NULL,
    enabled_value BOOLEAN,
    numeric_value BIGINT,
    CONSTRAINT fk_plan_limits_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT uk_plan_limits_plan_key UNIQUE (plan_id, limit_key),
    CONSTRAINT ck_plan_limits_kind CHECK (limit_kind IN ('ENTITLEMENT', 'QUOTA')),
    CONSTRAINT ck_plan_limits_value CHECK (
        (limit_kind = 'ENTITLEMENT' AND enabled_value IS NOT NULL AND numeric_value IS NULL)
        OR
        (limit_kind = 'QUOTA' AND enabled_value IS NULL AND numeric_value IS NOT NULL AND numeric_value >= 0)
    )
);

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    trial_started_at TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT uk_tenant_subscriptions_current UNIQUE (tenant_id),
    CONSTRAINT ck_tenant_subscriptions_status CHECK (
        status IN ('TRIALING', 'FREE', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')
    ),
    CONSTRAINT ck_tenant_subscriptions_trial CHECK (
        (status <> 'TRIALING')
        OR
        (trial_started_at IS NOT NULL AND trial_ends_at = trial_started_at + INTERVAL '30 days')
    )
);

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_status ON tenant_subscriptions(status);

INSERT INTO subscription_plans (plan_code, display_name, active, trial_days, fallback_plan_code)
VALUES
    ('FREE', '免费版', TRUE, NULL, NULL),
    ('TRIAL', '30 天全功能试用版', TRUE, 30, NULL)
ON CONFLICT (plan_code) DO NOTHING;

-- FREE capacity remains a product decision. Candidate values such as one user,
-- one warehouse and 100 SKUs must not become production limits before approval.

-- A trial exposes every service feature for 30 days. Capacity/anti-abuse limits
-- remain a different decision and are not represented by this entitlement.
INSERT INTO plan_limits (plan_id, limit_key, limit_kind, enabled_value, numeric_value)
SELECT plan.id, 'ALL_SERVICES', 'ENTITLEMENT', TRUE, NULL
FROM subscription_plans plan
WHERE plan.plan_code = 'TRIAL'
ON CONFLICT (plan_id, limit_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS signup_requests (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    requested_plan_code VARCHAR(30) NOT NULL,
    company_name VARCHAR(160),
    slug VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'EMAIL_PENDING',
    tenant_id BIGINT,
    last_error_code VARCHAR(80),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_signup_requests_public_id UNIQUE (public_id),
    CONSTRAINT uk_signup_requests_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_signup_requests_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_signup_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_signup_requests_email_normalized CHECK (
        normalized_email = lower(btrim(normalized_email))
    ),
    CONSTRAINT ck_signup_requests_slug CHECK (
        slug IS NULL OR (
            slug = lower(btrim(slug))
            AND slug ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'
            AND slug NOT IN ('www', 'app', 'api', 'platform', 'admin', 'support',
                             'mail', 'static', 'cdn', 'status', 'help')
        )
    ),
    CONSTRAINT ck_signup_requests_plan CHECK (requested_plan_code IN ('FREE', 'TRIAL')),
    CONSTRAINT ck_signup_requests_status CHECK (
        status IN ('EMAIL_PENDING', 'EMAIL_VERIFIED', 'DETAILS_COMPLETED', 'PROVISIONING',
                   'ACTIVE', 'PROVISIONING_FAILED', 'EXPIRED')
    )
);

CREATE INDEX IF NOT EXISTS idx_signup_requests_email ON signup_requests(normalized_email);
CREATE INDEX IF NOT EXISTS idx_signup_requests_status ON signup_requests(status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_signup_requests_active_email
    ON signup_requests(normalized_email)
    WHERE status IN ('EMAIL_PENDING', 'EMAIL_VERIFIED', 'DETAILS_COMPLETED', 'PROVISIONING', 'ACTIVE');

CREATE TABLE IF NOT EXISTS email_verification_challenges (
    id BIGSERIAL PRIMARY KEY,
    signup_request_id BIGINT NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_challenges_signup FOREIGN KEY (signup_request_id) REFERENCES signup_requests(id),
    CONSTRAINT ck_email_challenges_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    )
);

CREATE INDEX IF NOT EXISTS idx_email_challenges_signup
    ON email_verification_challenges(signup_request_id);

CREATE TABLE IF NOT EXISTS tenant_provisioning_jobs (
    id BIGSERIAL PRIMARY KEY,
    signup_request_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_step VARCHAR(80),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provisioning_jobs_signup_tenant FOREIGN KEY (signup_request_id, tenant_id)
        REFERENCES signup_requests(id, tenant_id),
    CONSTRAINT fk_provisioning_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_provisioning_jobs_signup UNIQUE (signup_request_id),
    CONSTRAINT ck_provisioning_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_provisioning_jobs_attempt_count CHECK (attempt_count >= 0)
);

COMMENT ON TABLE tenants IS 'SaaS company registry. tenants.id is the authoritative company_id.';
COMMENT ON TABLE plan_limits IS 'ENTITLEMENT controls features; QUOTA controls capacity.';
COMMENT ON TABLE signup_requests IS 'Global pre-tenant signup state; never inherits a default company_id.';
COMMENT ON TABLE tenant_provisioning_jobs IS 'Provisioning worker claims a job only after signup_requests.tenant_id is assigned.';
