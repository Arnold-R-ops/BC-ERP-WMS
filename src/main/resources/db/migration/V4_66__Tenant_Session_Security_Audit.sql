CREATE TABLE IF NOT EXISTS tenant_session_security_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_username VARCHAR(100) NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    result VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_session_audit_company FOREIGN KEY (company_id) REFERENCES tenants(id),
    CONSTRAINT ck_tenant_session_audit_action CHECK (
        action IN ('SELF_REVOKE_ALL_SESSIONS', 'ADMIN_REVOKE_ALL_SESSIONS')
    ),
    CONSTRAINT ck_tenant_session_audit_result CHECK (result IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_session_security_audit_target
    ON tenant_session_security_audit(company_id, target_user_id, created_at DESC);

ALTER TABLE tenant_session_security_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_session_security_audit FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS company_isolation ON tenant_session_security_audit;
CREATE POLICY company_isolation ON tenant_session_security_audit
    FOR ALL
    USING (company_id = bcwms_current_company_id())
    WITH CHECK (company_id = bcwms_current_company_id());
