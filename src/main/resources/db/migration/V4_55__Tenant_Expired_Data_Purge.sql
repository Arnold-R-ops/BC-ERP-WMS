-- Destructive company cleanup after the CLOSED retention deadline.
-- The tenants row and platform_audit_logs remain as permanent platform evidence.

CREATE TABLE tenant_purge_jobs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_purge_jobs_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_purge_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_tenant_purge_jobs_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_tenant_purge_jobs_attempt CHECK (attempt_count >= 0)
);

CREATE INDEX idx_tenant_purge_jobs_status_lease
    ON tenant_purge_jobs(status, lease_expires_at);

CREATE TABLE tenant_purge_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    deleted_row_count BIGINT,
    error_detail VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_purge_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_tenant_purge_audit_event CHECK (event_type IN ('STARTED','COMPLETED','FAILED')),
    CONSTRAINT ck_tenant_purge_audit_result CHECK (result IN ('RUNNING','SUCCESS','FAILED'))
);
CREATE INDEX idx_tenant_purge_audit_tenant_created
    ON tenant_purge_audit_logs(tenant_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_tenant_purge_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'tenant_purge_audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_tenant_purge_audit_immutable
BEFORE UPDATE OR DELETE ON tenant_purge_audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_tenant_purge_audit_mutation();

-- Tenant-side append-only evidence is deleted only by the lifecycle purge.
CREATE OR REPLACE FUNCTION prevent_permission_request_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'sys_permission_request_audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prevent_historical_archive_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'historical_test_data_archive_audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prevent_historical_test_registry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'historical_test_data_registry is migration-managed and immutable';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION bcwms_purge_company_data(p_company_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    tenant_state VARCHAR(30);
    tenant_purge_due_at TIMESTAMPTZ;
    tenant_table RECORD;
    deleted_rows BIGINT;
    total_deleted BIGINT := 0;
    remaining_rows BIGINT;
BEGIN
    IF p_company_id IS NULL OR p_company_id <= 0 THEN
        RAISE EXCEPTION 'A positive company identifier is required';
    END IF;
    IF bcwms_current_company_id() IS DISTINCT FROM p_company_id THEN
        RAISE EXCEPTION 'Company purge context mismatch';
    END IF;

    SELECT status, purge_due_at INTO tenant_state, tenant_purge_due_at
      FROM tenants WHERE id = p_company_id FOR UPDATE;
    IF tenant_state IS DISTINCT FROM 'PURGE_PENDING' THEN
        RAISE EXCEPTION 'Company % is not PURGE_PENDING', p_company_id;
    END IF;
    IF tenant_purge_due_at IS NULL OR tenant_purge_due_at > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'Company % retention deadline has not expired', p_company_id;
    END IF;
    PERFORM set_config('app.tenant_purge', 'on', true);

    CREATE TEMP TABLE IF NOT EXISTS bcwms_purge_identity_candidates (
        identity_id BIGINT PRIMARY KEY
    ) ON COMMIT DROP;
    TRUNCATE bcwms_purge_identity_candidates;
    INSERT INTO bcwms_purge_identity_candidates(identity_id)
    SELECT identity_id FROM tenant_memberships WHERE tenant_id = p_company_id
    UNION
    SELECT identity_id FROM signup_requests
     WHERE tenant_id = p_company_id AND identity_id IS NOT NULL;

    -- Break nullable self-references (for example category trees) before set deletes.
    FOR tenant_table IN
        SELECT c.relname, a.attname
        FROM pg_constraint con
        JOIN pg_class c ON c.oid = con.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN unnest(con.conkey) key(attnum) ON TRUE
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = key.attnum
        JOIN pg_attribute discriminator ON discriminator.attrelid = c.oid
            AND discriminator.attname = 'company_id' AND NOT discriminator.attisdropped
        WHERE n.nspname = 'public' AND con.contype = 'f'
          AND con.conrelid = con.confrelid AND NOT a.attnotnull
    LOOP
        EXECUTE format('UPDATE public.%I SET %I = NULL WHERE company_id = $1',
            tenant_table.relname, tenant_table.attname) USING p_company_id;
    END LOOP;

    -- Control-plane rows that reference business users/configuration must go first.
    DELETE FROM channel_webhook_routes WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM session_handoff_codes WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM platform_operation_authorizations WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;
    DELETE FROM tenant_memberships WHERE tenant_id = p_company_id;
    GET DIAGNOSTICS deleted_rows = ROW_COUNT; total_deleted := total_deleted + deleted_rows;

    -- Delete every tenant-owned table in foreign-key dependency order. This discovers
    -- future company_id tables too, and the final assertion prevents silent omissions.
    FOR tenant_table IN
        WITH RECURSIVE owned AS (
            SELECT c.oid, c.relname
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid
            WHERE n.nspname = 'public' AND c.relkind = 'r'
              AND a.attname = 'company_id' AND NOT a.attisdropped
        ), dependency(child_oid, parent_oid, depth) AS (
            SELECT con.conrelid, con.confrelid, 1
            FROM pg_constraint con
            JOIN owned child ON child.oid = con.conrelid
            JOIN owned parent ON parent.oid = con.confrelid
            WHERE con.contype = 'f' AND con.conrelid <> con.confrelid
            UNION ALL
            SELECT d.child_oid, con.confrelid, d.depth + 1
            FROM dependency d
            JOIN pg_constraint con ON con.conrelid = d.parent_oid AND con.contype = 'f'
            JOIN owned parent ON parent.oid = con.confrelid
            WHERE con.confrelid <> d.child_oid AND d.depth < 100
        )
        SELECT o.relname, COALESCE(MAX(d.depth), 0) AS depth
        FROM owned o LEFT JOIN dependency d ON d.child_oid = o.oid
        GROUP BY o.oid, o.relname
        ORDER BY depth DESC, o.relname
    LOOP
        EXECUTE format('DELETE FROM public.%I WHERE company_id = $1', tenant_table.relname)
            USING p_company_id;
        GET DIAGNOSTICS deleted_rows = ROW_COUNT;
        total_deleted := total_deleted + deleted_rows;
    END LOOP;

    DELETE FROM email_verification_challenges
     WHERE signup_request_id IN (SELECT id FROM signup_requests WHERE tenant_id = p_company_id);
    DELETE FROM tenant_provisioning_jobs WHERE tenant_id = p_company_id;
    DELETE FROM tenant_subscriptions WHERE tenant_id = p_company_id;
    DELETE FROM signup_requests WHERE tenant_id = p_company_id;
    DELETE FROM tenant_domains WHERE tenant_id = p_company_id;
    DELETE FROM platform_export_jobs WHERE target_tenant_id = p_company_id;
    DELETE FROM user_identities identity
     WHERE identity.id IN (SELECT identity_id FROM bcwms_purge_identity_candidates)
       AND NOT EXISTS (SELECT 1 FROM tenant_memberships membership WHERE membership.identity_id = identity.id)
       AND NOT EXISTS (SELECT 1 FROM signup_requests signup WHERE signup.identity_id = identity.id);

    FOR tenant_table IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE n.nspname = 'public' AND c.relkind = 'r'
          AND a.attname = 'company_id' AND NOT a.attisdropped
    LOOP
        EXECUTE format('SELECT count(*) FROM public.%I WHERE company_id = $1', tenant_table.relname)
            INTO remaining_rows USING p_company_id;
        IF remaining_rows <> 0 THEN
            RAISE EXCEPTION 'Company purge left % rows in public.%', remaining_rows, tenant_table.relname;
        END IF;
    END LOOP;
    RETURN total_deleted;
END;
$$;

COMMENT ON FUNCTION bcwms_purge_company_data(BIGINT) IS
    'Irreversibly deletes one PURGE_PENDING company business/control data under matching RLS context';
COMMENT ON TABLE tenant_purge_jobs IS
    'Retryable lifecycle cleanup state; tenant tombstone and platform audit are retained permanently';
COMMENT ON TABLE tenant_purge_audit_logs IS
    'Platform-only append-only evidence for irreversible company lifecycle cleanup';
