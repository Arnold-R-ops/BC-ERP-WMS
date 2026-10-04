-- P3-M1 A. Validate in a dedicated disposable database only; deployment is a separate approval.
ALTER TABLE tenants ADD COLUMN session_version BIGINT NOT NULL DEFAULT 1 CHECK (session_version > 0);
ALTER TABLE tenants ADD COLUMN suspension_kind VARCHAR(30);
ALTER TABLE tenants ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN security_incident_id VARCHAR(36);
ALTER TABLE tenants ADD COLUMN suspension_reason TEXT;
-- Historical suspensions must never silently become eligible for security restoration.
UPDATE tenants SET suspension_kind = 'LEGACY_UNCLASSIFIED' WHERE status = 'SUSPENDED';
ALTER TABLE session_handoff_codes ADD COLUMN tenant_session_version BIGINT NOT NULL DEFAULT 1
    CHECK (tenant_session_version > 0);

-- A short business write transaction and a lifecycle command serialize on the same tenant row.
-- The application pins the version at admission, not afresh for each batch after restoration.
CREATE FUNCTION bcwms_check_tenant_activity(p_tenant BIGINT, p_version BIGINT, p_purpose TEXT,
                                           p_write BOOLEAN) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE v_state TEXT; v_version BIGINT;
BEGIN
    IF p_write THEN
        SELECT status, session_version INTO v_state, v_version FROM tenants WHERE id = p_tenant FOR SHARE;
    ELSE
        SELECT status, session_version INTO v_state, v_version FROM tenants WHERE id = p_tenant;
    END IF;
    IF NOT FOUND THEN RAISE EXCEPTION 'TENANT_SERVICE_UNAVAILABLE' USING ERRCODE = '55000'; END IF;
    IF p_version IS NOT NULL AND p_version <> v_version THEN
        RAISE EXCEPTION 'AUTH_SESSION_INVALIDATED' USING ERRCODE = '55000';
    END IF;
    IF NOT ((p_purpose = 'BUSINESS' AND v_state = 'ACTIVE')
        OR (p_purpose IN ('AUTHENTICATION','PLATFORM_READ','WEBHOOK_INBOX','SECURITY_CONTROL')
            AND v_state IN ('ACTIVE','SUSPENDED'))
        OR (p_purpose = 'PROVISIONING' AND v_state = 'PROVISIONING')) THEN
        RAISE EXCEPTION 'TENANT_SERVICE_UNAVAILABLE' USING ERRCODE = '55000';
    END IF;
    RETURN v_version;
END $$;
