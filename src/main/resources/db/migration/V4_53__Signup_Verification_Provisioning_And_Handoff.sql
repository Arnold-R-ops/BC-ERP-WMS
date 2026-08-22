-- V4.53: executable public signup, verified provisioning and one-time handoff.

ALTER TABLE signup_requests
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(128),
    ADD COLUMN IF NOT EXISTS details_fingerprint VARCHAR(128),
    ADD COLUMN IF NOT EXISTS identity_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_signup_requests_identity'
    ) THEN
        ALTER TABLE signup_requests
            ADD CONSTRAINT fk_signup_requests_identity
            FOREIGN KEY (identity_id) REFERENCES user_identities(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS session_handoff_codes (
    id BIGSERIAL PRIMARY KEY,
    signup_request_id BIGINT NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    identity_id BIGINT NOT NULL,
    tenant_user_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_session_handoff_codes_hash UNIQUE (code_hash),
    CONSTRAINT uk_session_handoff_codes_signup UNIQUE (signup_request_id),
    CONSTRAINT fk_session_handoff_signup FOREIGN KEY (signup_request_id) REFERENCES signup_requests(id),
    CONSTRAINT fk_session_handoff_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_session_handoff_identity FOREIGN KEY (identity_id) REFERENCES user_identities(id),
    CONSTRAINT fk_session_handoff_user FOREIGN KEY (tenant_id, tenant_user_id)
        REFERENCES users(company_id, id),
    CONSTRAINT ck_session_handoff_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_session_handoff_consumed CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_session_handoff_expiry
    ON session_handoff_codes(expires_at) WHERE consumed_at IS NULL;

COMMENT ON TABLE session_handoff_codes IS
    'Opaque 60-second single-use browser handoff; never contains a JWT.';
