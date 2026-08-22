CREATE TABLE platform_export_jobs (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    platform_user_id BIGINT NOT NULL,
    target_tenant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_resource VARCHAR(80) NOT NULL,
    file_path VARCHAR(1000),
    file_sha256 VARCHAR(64),
    record_count BIGINT,
    error_code VARCHAR(80),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_platform_export_jobs_public UNIQUE (public_id),
    CONSTRAINT fk_platform_export_jobs_user FOREIGN KEY (platform_user_id) REFERENCES platform_users(id),
    CONSTRAINT fk_platform_export_jobs_tenant FOREIGN KEY (target_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_platform_export_jobs_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','EXPIRED'))
);

CREATE INDEX idx_platform_export_jobs_actor_created
    ON platform_export_jobs(platform_user_id, created_at DESC);
CREATE INDEX idx_platform_export_jobs_expiry
    ON platform_export_jobs(expires_at) WHERE status = 'COMPLETED';
