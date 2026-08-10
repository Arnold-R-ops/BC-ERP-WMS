-- Immutable lifecycle audit for custom permission-package editing and approval.

CREATE TABLE IF NOT EXISTS sys_role_governance_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    role_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    action VARCHAR(30) NOT NULL,
    operator_id BIGINT,
    operator_username VARCHAR(100) NOT NULL,
    from_review_status VARCHAR(30),
    to_review_status VARCHAR(30),
    from_runtime_status VARCHAR(20),
    to_runtime_status VARCHAR(20),
    permission_count INTEGER NOT NULL DEFAULT 0,
    high_risk_count INTEGER NOT NULL DEFAULT 0,
    permission_codes VARCHAR(4000),
    added_permission_codes VARCHAR(2000),
    removed_permission_codes VARCHAR(2000),
    reason VARCHAR(500),
    snapshot_fingerprint VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_governance_audit_action CHECK (
        action IN ('UPDATE_DRAFT', 'SUBMIT_REVIEW', 'APPROVE', 'REJECT', 'ACTIVATE', 'DEACTIVATE')
    ),
    CONSTRAINT ck_role_governance_audit_counts CHECK (
        permission_count >= 0 AND high_risk_count >= 0 AND high_risk_count <= permission_count
    )
);

CREATE INDEX IF NOT EXISTS idx_role_governance_audit_role
    ON sys_role_governance_audit(company_id, role_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_role_governance_audit_operator
    ON sys_role_governance_audit(company_id, operator_id, created_at DESC);

COMMENT ON TABLE sys_role_governance_audit IS
    'Immutable role-package lifecycle audit; role identifiers are snapshots without lifecycle-blocking foreign keys.';

