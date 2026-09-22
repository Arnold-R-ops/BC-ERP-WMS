CREATE TABLE tenant_security_requests (
    public_id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    incident_id VARCHAR(36) NOT NULL,
    tenant_session_version BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL REFERENCES platform_users(id),
    reviewer_id BIGINT REFERENCES platform_users(id),
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','APPLIED','REJECTED','WITHDRAWN')),
    reason TEXT NOT NULL,
    decision_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMPTZ,
    CHECK (reviewer_id IS NULL OR reviewer_id <> initiator_id)
);
CREATE UNIQUE INDEX uk_tenant_security_pending ON tenant_security_requests(tenant_id) WHERE status='PENDING';

CREATE TABLE tenant_security_challenges (
    token_hash VARCHAR(64) PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    actor_id BIGINT NOT NULL REFERENCES platform_users(id),
    actor_security_version BIGINT NOT NULL,
    tenant_session_version BIGINT NOT NULL,
    context_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count BETWEEN 0 AND 5)
);
CREATE INDEX idx_tenant_security_challenge_expiry ON tenant_security_challenges(expires_at);
CREATE TABLE tenant_security_commands (
    actor_id BIGINT NOT NULL REFERENCES platform_users(id),
    idempotency_key VARCHAR(100) NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    context_hash VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(actor_id,idempotency_key)
);

CREATE TABLE tenant_security_connections (
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    config_id BIGINT NOT NULL,
    incident_id VARCHAR(36) NOT NULL,
    was_enabled BOOLEAN NOT NULL,
    suspected_compromise BOOLEAN NOT NULL,
    state VARCHAR(30) NOT NULL CHECK(state IN ('RISK_PENDING','LOCAL_BLOCKED','CHECK_PASSED','REAUTH_REQUIRED')),
    remote_status VARCHAR(30) NOT NULL DEFAULT 'NOT_VERIFIED'
        CHECK(remote_status IN ('NOT_VERIFIED','NOT_REQUIRED','REVOCATION_VERIFIED','ROTATION_VERIFIED','FAILED')),
    check_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CHECKED'
        CHECK(check_status IN ('NOT_CHECKED','PASSED','FAILED')),
    gap_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK(gap_status IN ('PENDING','REVIEW_REQUIRED','RECONCILED')),
    credential_version BIGINT NOT NULL DEFAULT 1,
    evidence TEXT,
    checked_by BIGINT REFERENCES platform_users(id),
    checked_at TIMESTAMPTZ,
    PRIMARY KEY(tenant_id,config_id)
);
ALTER TABLE integration_configs ALTER COLUMN client_secret TYPE TEXT;
ALTER TABLE integration_configs ALTER COLUMN access_token TYPE TEXT;
ALTER TABLE integration_configs ALTER COLUMN api_key TYPE TEXT;
ALTER TABLE channel_webhook_routes ALTER COLUMN signing_secret TYPE TEXT;
ALTER TABLE platform_export_jobs ADD COLUMN access_reason TEXT;

CREATE TABLE tenant_webhook_inbox (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    config_id BIGINT NOT NULL,
    webhook_id VARCHAR(200) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    body_hash VARCHAR(64) NOT NULL,
    payload TEXT,
    payload_bytes BIGINT NOT NULL,
    first_received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','PROCESSED','REVIEW_REQUIRED','EXPIRED')),
    last_error VARCHAR(100),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(tenant_id,config_id,webhook_id)
);
CREATE INDEX idx_tenant_webhook_work ON tenant_webhook_inbox(state,next_attempt_at,id);
CREATE INDEX idx_tenant_webhook_expiry ON tenant_webhook_inbox(expires_at) WHERE payload IS NOT NULL;
-- Admission serialization makes both tenant and global capacity limits exact under concurrency.
CREATE TABLE tenant_webhook_budget (
    budget_key VARCHAR(60) PRIMARY KEY,
    minute_start TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    minute_count INTEGER NOT NULL DEFAULT 0
);
INSERT INTO tenant_webhook_budget(budget_key) VALUES('GLOBAL');

CREATE TABLE tenant_security_alerts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    issue_key VARCHAR(160) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_mail_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_mail_at TIMESTAMPTZ,
    first_mail_failure_at TIMESTAMPTZ,
    mail_attempts INTEGER NOT NULL DEFAULT 0,
    mail_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    resolved_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_tenant_security_open_issue ON tenant_security_alerts(tenant_id,issue_key) WHERE status='OPEN';

CREATE FUNCTION bcwms_guard_security_request() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Security requests are retained'; END IF;
    IF OLD.status <> 'PENDING' OR NEW.public_id <> OLD.public_id OR NEW.tenant_id <> OLD.tenant_id
        OR NEW.incident_id <> OLD.incident_id OR NEW.tenant_session_version <> OLD.tenant_session_version
        OR NEW.initiator_id <> OLD.initiator_id OR NEW.reason <> OLD.reason OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Immutable security request';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_security_request_immutable BEFORE UPDATE OR DELETE ON tenant_security_requests
    FOR EACH ROW EXECUTE FUNCTION bcwms_guard_security_request();
