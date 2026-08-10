-- Durable audit trail for permission-package copy operations.

CREATE TABLE IF NOT EXISTS sys_role_copy_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    operator_id BIGINT,
    operator_username VARCHAR(100) NOT NULL,
    source_role_id BIGINT NOT NULL,
    source_role_code VARCHAR(50) NOT NULL,
    target_role_id BIGINT NOT NULL,
    target_role_code VARCHAR(50) NOT NULL,
    permission_count INTEGER NOT NULL,
    high_risk_count INTEGER NOT NULL,
    high_risk_permission_codes VARCHAR(2000),
    operation_reason VARCHAR(500),
    snapshot_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_role_copy_audit_source_role
        FOREIGN KEY (source_role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_role_copy_audit_target_role
        FOREIGN KEY (target_role_id) REFERENCES sys_role(id),
    CONSTRAINT chk_role_copy_audit_counts
        CHECK (permission_count >= 0 AND high_risk_count >= 0 AND high_risk_count <= permission_count)
);

CREATE INDEX IF NOT EXISTS idx_role_copy_audit_source
    ON sys_role_copy_audit(company_id, source_role_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_role_copy_audit_target
    ON sys_role_copy_audit(company_id, target_role_id);

CREATE INDEX IF NOT EXISTS idx_role_copy_audit_operator
    ON sys_role_copy_audit(company_id, operator_id, created_at DESC);

COMMENT ON TABLE sys_role_copy_audit IS
    'Immutable audit trail for effective-permission snapshot copies';

