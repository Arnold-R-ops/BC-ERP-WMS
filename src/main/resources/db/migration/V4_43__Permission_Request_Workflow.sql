-- V4.43: auditable permission requests with atomic role grant and immediate revocation.

CREATE TABLE IF NOT EXISTS sys_permission_request (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    requested_role_id BIGINT NOT NULL,
    requested_role_code VARCHAR(50) NOT NULL,
    requested_role_name VARCHAR(100) NOT NULL,
    request_reason VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    high_risk_permission_count INTEGER NOT NULL DEFAULT 0,
    permission_codes VARCHAR(4000),
    snapshot_fingerprint VARCHAR(64) NOT NULL,
    submitted_by BIGINT NOT NULL,
    submitted_by_username VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by BIGINT,
    reviewed_by_username VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_comment VARCHAR(500),
    revoked_by BIGINT,
    revoked_by_username VARCHAR(100),
    revoked_at TIMESTAMP,
    revocation_comment VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_permission_request_status CHECK (
        status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'REVOKED')
    ),
    CONSTRAINT ck_permission_request_high_risk_count CHECK (
        high_risk_permission_count >= 0
    )
);

CREATE TABLE IF NOT EXISTS sys_permission_request_warehouse (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    permission_request_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,

    CONSTRAINT fk_permission_request_warehouse_request FOREIGN KEY (permission_request_id)
        REFERENCES sys_permission_request(id) ON DELETE CASCADE,
    CONSTRAINT uk_permission_request_warehouse
        UNIQUE (company_id, permission_request_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS sys_permission_request_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    permission_request_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_username VARCHAR(100) NOT NULL,
    operator_role_code VARCHAR(50) NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    requested_role_id BIGINT NOT NULL,
    requested_role_code VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    warehouse_ids VARCHAR(1000),
    high_risk_permission_count INTEGER NOT NULL DEFAULT 0,
    permission_codes VARCHAR(4000),
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_permission_request_audit_action CHECK (
        action IN ('CREATE', 'APPROVE', 'REJECT', 'REVOKE')
    ),
    CONSTRAINT ck_permission_request_audit_high_risk_count CHECK (
        high_risk_permission_count >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_permission_request_status
    ON sys_permission_request(company_id, status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_permission_request_target
    ON sys_permission_request(company_id, target_user_id, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_permission_request_role
    ON sys_permission_request(company_id, requested_role_id, submitted_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_permission_request_pending_target_role
    ON sys_permission_request(company_id, target_user_id, requested_role_id)
    WHERE status = 'PENDING_REVIEW';
CREATE INDEX IF NOT EXISTS idx_permission_request_warehouse_request
    ON sys_permission_request_warehouse(permission_request_id);
CREATE INDEX IF NOT EXISTS idx_permission_request_audit_request
    ON sys_permission_request_audit(company_id, permission_request_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_permission_request_audit_operator
    ON sys_permission_request_audit(company_id, operator_id, created_at DESC);

COMMENT ON TABLE sys_permission_request IS
    'Permission-package request state; role and user names are snapshots for durable review history.';
COMMENT ON TABLE sys_permission_request_audit IS
    'Append-only permission request audit; identifiers are snapshots without lifecycle-blocking foreign keys.';

CREATE OR REPLACE FUNCTION prevent_permission_request_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'sys_permission_request_audit is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_permission_request_audit_immutable
    ON sys_permission_request_audit;
CREATE TRIGGER trg_permission_request_audit_immutable
BEFORE UPDATE OR DELETE ON sys_permission_request_audit
FOR EACH ROW EXECUTE FUNCTION prevent_permission_request_audit_mutation();

