-- V4.73: unified inventory-posting metadata and governed emergency corrections.
--
-- Existing stock ledgers and historical corrections deliberately retain NULL
-- values for metadata that did not exist when they were written. New writers
-- populate these snapshots; lifecycle foreign keys are intentionally omitted
-- so an immutable audit row remains intelligible after master-data retirement.

-- ---------------------------------------------------------------------------
-- Unified stock-posting traceability
-- ---------------------------------------------------------------------------

ALTER TABLE public.stock_transactions
    ADD COLUMN IF NOT EXISTS warehouse_id BIGINT,
    ADD COLUMN IF NOT EXISTS inventory_batch_id BIGINT,
    ADD COLUMN IF NOT EXISTS batch_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS posting_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_stock_tx_warehouse_id
    ON public.stock_transactions(company_id, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_inventory_batch_id
    ON public.stock_transactions(company_id, inventory_batch_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_posting_id
    ON public.stock_transactions(company_id, posting_id);
CREATE INDEX IF NOT EXISTS idx_stock_tx_idempotency_key
    ON public.stock_transactions(company_id, idempotency_key);

COMMENT ON COLUMN public.stock_transactions.warehouse_id IS
    'Warehouse identity captured at posting time; NULL identifies a legacy ledger row.';
COMMENT ON COLUMN public.stock_transactions.inventory_batch_id IS
    'Inventory-batch identity captured at posting time; NULL identifies a legacy or non-batch row.';
COMMENT ON COLUMN public.stock_transactions.batch_code IS
    'Batch-code snapshot captured at posting time.';
COMMENT ON COLUMN public.stock_transactions.posting_id IS
    'Application-assigned identifier of the logical inventory posting.';
COMMENT ON COLUMN public.stock_transactions.idempotency_key IS
    'Company-scoped request key that caused the posting, when available.';

-- ---------------------------------------------------------------------------
-- Lease-safe idempotency claims
-- ---------------------------------------------------------------------------

ALTER TABLE public.idempotency_request
    ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_idempotency_company_status_lease
    ON public.idempotency_request(company_id, status, locked_until);

COMMENT ON COLUMN public.idempotency_request.lease_token IS
    'Opaque owner token; only the current PROCESSING lease owner may complete the request.';

-- ---------------------------------------------------------------------------
-- Emergency-correction policy snapshot and posting evidence
-- ---------------------------------------------------------------------------

ALTER TABLE public.emergency_stock_correction
    ADD COLUMN IF NOT EXISTS warehouse_id BIGINT,
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS approval_mode_snapshot VARCHAR(20),
    ADD COLUMN IF NOT EXISTS applied_quantity_before INTEGER,
    ADD COLUMN IF NOT EXISTS applied_quantity_after INTEGER,
    ADD COLUMN IF NOT EXISTS posting_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS rejected_by BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rejection_comment VARCHAR(500),
    ADD COLUMN IF NOT EXISTS submission_comment VARCHAR(500),
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- Tenant tables already use FORCE ROW LEVEL SECURITY. Perform the backfill in
-- one explicit company context at a time so the migration cannot cross a
-- company boundary accidentally.
DO $emergency_backfill$
DECLARE
    company_record RECORD;
    unresolved_ids TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        UPDATE public.emergency_stock_correction correction
        SET warehouse_id = location.warehouse_id
        FROM public.locations location
        WHERE correction.company_id = company_record.id
          AND location.company_id = company_record.id
          AND location.id = correction.location_id
          AND correction.warehouse_id IS NULL;

        -- The previous implementation stored the maker in submitted_by even
        -- while the correction was DRAFT. Later actors are safe fallbacks for
        -- unusually old rows; zero is an explicit legacy-unknown snapshot and
        -- is not protected by a lifecycle foreign key.
        UPDATE public.emergency_stock_correction
        SET created_by = COALESCE(
                created_by,
                submitted_by,
                reviewed_by,
                approved_by,
                applied_by,
                0
            ),
            approval_mode_snapshot = CASE
                WHEN approval_mode_snapshot IS NOT NULL THEN approval_mode_snapshot
                WHEN status <> 'DRAFT' THEN 'THREE_PERSON'
                ELSE NULL
            END,
            rejected_by = CASE
                WHEN status = 'REJECTED' THEN COALESCE(rejected_by, reviewed_by)
                ELSE rejected_by
            END,
            rejected_at = CASE
                WHEN status = 'REJECTED' THEN COALESCE(rejected_at, reviewed_at)
                ELSE rejected_at
            END,
            rejection_comment = CASE
                WHEN status = 'REJECTED' THEN COALESCE(rejection_comment, review_comment)
                ELSE rejection_comment
            END,
            submission_comment = CASE
                WHEN status = 'PENDING_REVIEW' THEN COALESCE(submission_comment, review_comment)
                ELSE submission_comment
            END,
            version = COALESCE(version, 0)
        WHERE company_id = company_record.id;

        SELECT string_agg(id::TEXT, ', ' ORDER BY id)
        INTO unresolved_ids
        FROM public.emergency_stock_correction
        WHERE company_id = company_record.id
          AND warehouse_id IS NULL;

        IF unresolved_ids IS NOT NULL THEN
            RAISE EXCEPTION
                'V4.73 cannot derive warehouse_id for emergency_stock_correction row(s) % in company %; repair their location ownership before retrying',
                unresolved_ids,
                company_record.id;
        END IF;
    END LOOP;

    PERFORM set_config('app.company_id', '', TRUE);
END
$emergency_backfill$;

ALTER TABLE public.emergency_stock_correction
    ALTER COLUMN warehouse_id SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

DO $emergency_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.emergency_stock_correction'::regclass
          AND conname = 'ck_emergency_correction_approval_mode'
    ) THEN
        ALTER TABLE public.emergency_stock_correction
            ADD CONSTRAINT ck_emergency_correction_approval_mode
            CHECK (
                approval_mode_snapshot IS NULL
                OR approval_mode_snapshot IN ('NONE', 'TWO_PERSON', 'THREE_PERSON')
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.emergency_stock_correction'::regclass
          AND conname = 'ck_emergency_correction_applied_quantities'
    ) THEN
        ALTER TABLE public.emergency_stock_correction
            ADD CONSTRAINT ck_emergency_correction_applied_quantities
            CHECK (
                (applied_quantity_before IS NULL AND applied_quantity_after IS NULL)
                OR (
                    applied_quantity_before IS NOT NULL
                    AND applied_quantity_after IS NOT NULL
                    AND applied_quantity_before >= 0
                    AND applied_quantity_after >= 0
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.emergency_stock_correction'::regclass
          AND conname = 'ck_emergency_correction_version_non_negative'
    ) THEN
        ALTER TABLE public.emergency_stock_correction
            ADD CONSTRAINT ck_emergency_correction_version_non_negative
            CHECK (version >= 0);
    END IF;
END
$emergency_constraints$;

CREATE INDEX IF NOT EXISTS idx_emergency_correction_company_warehouse_status
    ON public.emergency_stock_correction(company_id, warehouse_id, status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_emergency_correction_company_posting
    ON public.emergency_stock_correction(company_id, posting_id)
    WHERE posting_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_emergency_correction_company_idempotency
    ON public.emergency_stock_correction(company_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN public.emergency_stock_correction.warehouse_id IS
    'Immutable warehouse scope resolved from the correction location at creation time.';
COMMENT ON COLUMN public.emergency_stock_correction.created_by IS
    'Maker identity snapshot; zero denotes an unknown pre-V4.73 actor.';
COMMENT ON COLUMN public.emergency_stock_correction.approval_mode_snapshot IS
    'Workflow mode frozen when the correction is submitted for approval or directly confirmed; legacy submitted rows used THREE_PERSON.';
COMMENT ON COLUMN public.emergency_stock_correction.applied_quantity_before IS
    'Actual batch quantity immediately before application; NULL on pre-V4.73 history.';
COMMENT ON COLUMN public.emergency_stock_correction.applied_quantity_after IS
    'Actual batch quantity immediately after application; NULL on pre-V4.73 history.';

-- Direct adjustment authority is retired. Governed correction execution and
-- backorder wake-up receive distinct least-privilege permissions. Warehouse
-- administrators may make/submit/apply corrections, but they do not receive
-- review, approval, or rejection rights.
DO $correction_permissions$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES (
            company_record.id,
            'inventory:correction:view',
            '查看库存校正',
            'API',
            '/api/emergency-stock-corrections/**',
            'GET',
            'ALL',
            'NORMAL',
            TRUE,
            'View governed emergency stock corrections and their impact previews',
            'ACTIVE',
            121,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (company_id, permission_code) DO UPDATE
        SET permission_name = EXCLUDED.permission_name,
            permission_type = EXCLUDED.permission_type,
            resource_path = EXCLUDED.resource_path,
            http_method = EXCLUDED.http_method,
            data_scope = EXCLUDED.data_scope,
            risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable,
            description = EXCLUDED.description,
            status = EXCLUDED.status,
            sort_order = EXCLUDED.sort_order,
            updated_at = CURRENT_TIMESTAMP;

        UPDATE public.sys_permission
        SET status = 'INACTIVE',
            resource_path = NULL,
            http_method = NULL,
            custom_assignable = FALSE,
            description = 'Retired with the public direct stock-write endpoints in V4.73',
            updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND permission_code = 'inventory:adjust';

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES (
            company_record.id,
            'inventory:correction:execute',
            '发起并执行库存校正',
            'API',
            '/api/emergency-stock-corrections/**',
            '*',
            'ALL',
            'HIGH',
            TRUE,
            'Create, submit and apply governed corrections after impact preview',
            'ACTIVE',
            125,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (company_id, permission_code) DO UPDATE
        SET permission_name = EXCLUDED.permission_name,
            permission_type = EXCLUDED.permission_type,
            resource_path = EXCLUDED.resource_path,
            http_method = EXCLUDED.http_method,
            data_scope = EXCLUDED.data_scope,
            risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable,
            description = EXCLUDED.description,
            status = EXCLUDED.status,
            sort_order = EXCLUDED.sort_order,
            updated_at = CURRENT_TIMESTAMP;

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES (
            company_record.id,
            'backorder:wake',
            '重新分配欠货库存',
            'API',
            '/api/backorders/wake/**',
            'POST',
            'ALL',
            'HIGH',
            TRUE,
            'Retry allocation for open backorders without direct stock adjustment authority',
            'ACTIVE',
            126,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (company_id, permission_code) DO UPDATE
        SET permission_name = EXCLUDED.permission_name,
            permission_type = EXCLUDED.permission_type,
            resource_path = EXCLUDED.resource_path,
            http_method = EXCLUDED.http_method,
            data_scope = EXCLUDED.data_scope,
            risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable,
            description = EXCLUDED.description,
            status = EXCLUDED.status,
            sort_order = EXCLUDED.sort_order,
            updated_at = CURRENT_TIMESTAMP;

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            menu_url, menu_icon, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES (
            company_record.id,
            'menu:inventory-correction',
            '库存校正',
            'MENU',
            '/inventory-governance/corrections',
            'ThunderboltOutlined',
            'ALL',
            'NORMAL',
            TRUE,
            'Governed emergency stock-correction workspace',
            'ACTIVE',
            39,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (company_id, permission_code) DO UPDATE
        SET permission_name = EXCLUDED.permission_name,
            permission_type = EXCLUDED.permission_type,
            resource_path = NULL,
            http_method = NULL,
            menu_url = EXCLUDED.menu_url,
            menu_icon = EXCLUDED.menu_icon,
            data_scope = EXCLUDED.data_scope,
            risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable,
            description = EXCLUDED.description,
            status = EXCLUDED.status,
            sort_order = EXCLUDED.sort_order,
            updated_at = CURRENT_TIMESTAMP;

        INSERT INTO public.sys_role_permission (
            company_id, role_id, permission_id, granted_by, granted_at
        )
        SELECT
            company_record.id,
            role.id,
            permission.id,
            NULL,
            CURRENT_TIMESTAMP
        FROM public.sys_role role
        JOIN public.sys_permission permission
          ON permission.company_id = company_record.id
         AND permission.permission_code IN (
             'menu:inventory-correction',
             'inventory:correction:view',
             'inventory:correction:execute',
             'backorder:wake'
         )
        WHERE role.company_id = company_record.id
          AND (
              role.role_code = 'WAREHOUSE_ADMIN'
              OR (
                  role.role_code = 'GENERAL_MANAGER'
                  AND permission.permission_code IN (
                      'menu:inventory-correction',
                      'inventory:correction:view'
                  )
              )
          )
          AND role.status = 'ACTIVE'
        ON CONFLICT (company_id, role_id, permission_id) DO NOTHING;

        -- Permission metadata and role mappings are cached by user security
        -- version. Invalidate TENANT_ADMIN (which receives every active company
        -- permission), every role already carrying a changed permission, and
        -- descendants that inherit from those roles. This also closes the
        -- retirement window for cached inventory:adjust authority.
        WITH RECURSIVE affected_roles AS (
            SELECT role.id
            FROM public.sys_role role
            WHERE role.company_id = company_record.id
              AND (
                  role.role_code IN (
                      'TENANT_ADMIN',
                      'WAREHOUSE_ADMIN',
                      'GENERAL_MANAGER'
                  )
                  OR EXISTS (
                      SELECT 1
                      FROM public.sys_role_permission assignment
                      JOIN public.sys_permission permission
                        ON permission.company_id = assignment.company_id
                       AND permission.id = assignment.permission_id
                      WHERE assignment.company_id = company_record.id
                        AND assignment.role_id = role.id
                        AND permission.permission_code IN (
                            'menu:inventory-correction',
                            'inventory:correction:view',
                            'inventory:correction:execute',
                            'inventory:adjust',
                            'backorder:wake'
                        )
                  )
              )
            UNION
            SELECT inheritance.child_role_id
            FROM public.sys_role_inherit inheritance
            JOIN affected_roles parent
              ON parent.id = inheritance.parent_role_id
            WHERE inheritance.company_id = company_record.id
        )
        UPDATE public.users account
        SET security_version = COALESCE(account.security_version, 0) + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE account.company_id = company_record.id
          AND EXISTS (
              SELECT 1
              FROM public.sys_user_role user_role
              JOIN affected_roles affected ON affected.id = user_role.role_id
              WHERE user_role.company_id = company_record.id
                AND user_role.user_id = account.id
          );
    END LOOP;

    PERFORM set_config('app.company_id', '', TRUE);
END
$correction_permissions$;

-- ---------------------------------------------------------------------------
-- Append-only policy-change evidence
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.inventory_correction_policy_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    old_mode VARCHAR(20) NOT NULL,
    new_mode VARCHAR(20) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_username VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    approval_entitled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $policy_audit_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.inventory_correction_policy_audit'::regclass
          AND conname = 'fk_inventory_correction_policy_audit_company'
    ) THEN
        ALTER TABLE public.inventory_correction_policy_audit
            ADD CONSTRAINT fk_inventory_correction_policy_audit_company
            FOREIGN KEY (company_id) REFERENCES public.tenants(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.inventory_correction_policy_audit'::regclass
          AND conname = 'ck_inventory_correction_policy_audit_modes'
    ) THEN
        ALTER TABLE public.inventory_correction_policy_audit
            ADD CONSTRAINT ck_inventory_correction_policy_audit_modes
            CHECK (
                old_mode IN ('NONE', 'TWO_PERSON', 'THREE_PERSON')
                AND new_mode IN ('NONE', 'TWO_PERSON', 'THREE_PERSON')
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.inventory_correction_policy_audit'::regclass
          AND conname = 'ck_inventory_correction_policy_audit_text'
    ) THEN
        ALTER TABLE public.inventory_correction_policy_audit
            ADD CONSTRAINT ck_inventory_correction_policy_audit_text
            CHECK (
                length(btrim(operator_username)) > 0
                AND length(btrim(reason)) > 0
            );
    END IF;
END
$policy_audit_constraints$;

CREATE INDEX IF NOT EXISTS idx_inventory_correction_policy_audit_created
    ON public.inventory_correction_policy_audit(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_correction_policy_audit_operator
    ON public.inventory_correction_policy_audit(company_id, operator_id, created_at DESC);

COMMENT ON TABLE public.inventory_correction_policy_audit IS
    'Append-only evidence of company inventory-correction approval-mode changes.';

ALTER TABLE public.inventory_correction_policy_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_correction_policy_audit FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS company_isolation ON public.inventory_correction_policy_audit;
CREATE POLICY company_isolation ON public.inventory_correction_policy_audit
    FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

CREATE OR REPLACE FUNCTION public.prevent_inventory_correction_policy_audit_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'inventory_correction_policy_audit is append-only';
END;
$function$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_inventory_correction_policy_audit_immutable
    ON public.inventory_correction_policy_audit;
CREATE TRIGGER trg_inventory_correction_policy_audit_immutable
BEFORE UPDATE OR DELETE ON public.inventory_correction_policy_audit
FOR EACH ROW
EXECUTE FUNCTION public.prevent_inventory_correction_policy_audit_mutation();

-- Stock ledgers are source-of-truth facts. Corrections are represented by a
-- compensating posting, never by mutating or deleting an existing ledger row.
CREATE OR REPLACE FUNCTION public.prevent_stock_transaction_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'stock_transactions is append-only';
END;
$function$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_transactions_immutable
    ON public.stock_transactions;
CREATE TRIGGER trg_stock_transactions_immutable
BEFORE UPDATE OR DELETE ON public.stock_transactions
FOR EACH ROW
EXECUTE FUNCTION public.prevent_stock_transaction_mutation();

-- The constraint is deliberately NOT VALID: PostgreSQL enforces it for all
-- future inserts/updates without a blocking historical-table validation scan.
-- V4.8 may already provide a broader validated guard; this explicit constraint
-- preserves the rollout property on every supported upgrade path.
DO $inventory_batch_guard$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.inventory_batch'::regclass
          AND conname = 'chk_inventory_batch_quantity_gte_reserved'
    ) THEN
        ALTER TABLE public.inventory_batch
            ADD CONSTRAINT chk_inventory_batch_quantity_gte_reserved
            CHECK (quantity >= reserved_quantity) NOT VALID;
    END IF;
END
$inventory_batch_guard$;
