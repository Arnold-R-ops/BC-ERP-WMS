-- P0-03D: damage/expiry marking and governed physical scrap/destruction.
-- Marking changes saleability only. Physical on-hand is reduced only by an
-- immutable execution record posted through the unified inventory ledger.

ALTER TABLE public.outbound_tasks
    DROP CONSTRAINT IF EXISTS outbound_tasks_status_check;
ALTER TABLE public.outbound_tasks
    ADD CONSTRAINT outbound_tasks_status_check CHECK (
        status IN ('PENDING', 'PICKING', 'COMPLETED', 'VOIDED', 'QUALITY_EXCEPTION')
    );
COMMENT ON COLUMN public.outbound_tasks.status IS
    'Task status: PENDING, PICKING, COMPLETED, VOIDED, QUALITY_EXCEPTION';

CREATE TABLE public.inventory_disposition_order (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    disposition_number VARCHAR(40) NOT NULL,
    disposition_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    review_mode_snapshot VARCHAR(30),
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(500),
    external_reference VARCHAR(120),
    recall_notice_number VARCHAR(120),
    requested_by BIGINT NOT NULL,
    requested_by_name VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    cancelled_by BIGINT,
    cancelled_by_name VARCHAR(100),
    cancelled_at TIMESTAMP(6) WITHOUT TIME ZONE,
    cancel_reason VARCHAR(500),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_disposition_order_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_inventory_disposition_company_number UNIQUE (company_id, disposition_number),
    CONSTRAINT uk_inventory_disposition_company_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_inventory_disposition_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT ck_inventory_disposition_version CHECK (version >= 0),
    CONSTRAINT ck_inventory_disposition_type CHECK (
        disposition_type IN ('DAMAGE_REPORT', 'EXPIRY_MARK', 'SCRAP_DESTROY')
    ),
    CONSTRAINT ck_inventory_disposition_status CHECK (
        status IN ('DRAFT', 'COMPLETED', 'PENDING_REVIEW', 'READY_TO_EXECUTE',
            'PARTIALLY_EXECUTED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_inventory_disposition_review_mode CHECK (
        review_mode_snapshot IS NULL
        OR review_mode_snapshot IN ('DIRECT_WITH_REASON', 'APPROVAL_REQUIRED')
    ),
    CONSTRAINT ck_inventory_disposition_shape CHECK (
        (disposition_type IN ('DAMAGE_REPORT', 'EXPIRY_MARK')
            AND status = 'COMPLETED' AND review_mode_snapshot IS NULL)
        OR
        (disposition_type = 'SCRAP_DESTROY' AND review_mode_snapshot IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_disposition_text CHECK (
        length(btrim(disposition_number)) > 0
        AND length(btrim(reason_code)) > 0
        AND length(btrim(requested_by_name)) > 0
        AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_inventory_disposition_cancel CHECK (
        (status = 'CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
            AND length(btrim(cancel_reason)) > 0)
        OR
        (status <> 'CANCELLED' AND cancelled_by IS NULL AND cancelled_at IS NULL
            AND cancel_reason IS NULL)
    ),
    CONSTRAINT ck_inventory_disposition_review CHECK (
        (status IN ('PENDING_REVIEW', 'COMPLETED', 'PARTIALLY_EXECUTED')
            AND (reviewed_by IS NULL OR review_mode_snapshot = 'APPROVAL_REQUIRED'))
        OR status IN ('DRAFT', 'READY_TO_EXECUTE', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_inventory_disposition_separation CHECK (
        reviewed_by IS NULL OR reviewed_by <> requested_by
    )
);

CREATE TABLE public.inventory_disposition_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    disposition_order_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    result_batch_id BIGINT,
    product_sku_id BIGINT NOT NULL,
    product_sku_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    source_batch_code VARCHAR(50) NOT NULL,
    result_batch_code VARCHAR(50),
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(200) NOT NULL,
    location_id BIGINT NOT NULL,
    location_code VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    executed_quantity INTEGER NOT NULL DEFAULT 0,
    reallocated_reserved_quantity INTEGER NOT NULL DEFAULT 0,
    backordered_quantity INTEGER NOT NULL DEFAULT 0,
    quality_exception_quantity INTEGER NOT NULL DEFAULT 0,
    from_quality_status VARCHAR(30) NOT NULL,
    to_quality_status VARCHAR(30) NOT NULL,
    expiry_date DATE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_disposition_line_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_inventory_disposition_line_order FOREIGN KEY (company_id, disposition_order_id)
        REFERENCES public.inventory_disposition_order(company_id, id),
    CONSTRAINT fk_inventory_disposition_line_source FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_inventory_disposition_line_result FOREIGN KEY (company_id, result_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_inventory_disposition_line_quantity CHECK (
        quantity > 0 AND executed_quantity >= 0 AND executed_quantity <= quantity
        AND reallocated_reserved_quantity >= 0 AND backordered_quantity >= 0
        AND quality_exception_quantity >= 0
    ),
    CONSTRAINT ck_inventory_disposition_line_quality CHECK (
        from_quality_status IN ('AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED',
            'EXPIRED','RECALL_HOLD','RETURN_INSPECTION')
        AND to_quality_status IN ('AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED',
            'EXPIRED','RECALL_HOLD','RETURN_INSPECTION')
    ),
    CONSTRAINT ck_inventory_disposition_line_text CHECK (
        length(btrim(product_sku_code)) > 0 AND length(btrim(product_name)) > 0
        AND length(btrim(source_batch_code)) > 0 AND length(btrim(warehouse_name)) > 0
        AND length(btrim(location_code)) > 0
    )
);

CREATE TABLE public.inventory_disposition_execution (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    disposition_order_id BIGINT NOT NULL,
    execution_number VARCHAR(50) NOT NULL,
    executed_by BIGINT NOT NULL,
    executed_by_name VARCHAR(100) NOT NULL,
    executed_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    execution_note VARCHAR(500),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_disposition_execution_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_inventory_disposition_execution_company_number UNIQUE (company_id, execution_number),
    CONSTRAINT uk_inventory_disposition_execution_company_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_inventory_disposition_execution_order FOREIGN KEY (company_id, disposition_order_id)
        REFERENCES public.inventory_disposition_order(company_id, id),
    CONSTRAINT ck_inventory_disposition_execution_text CHECK (
        length(btrim(execution_number)) > 0 AND length(btrim(executed_by_name)) > 0
        AND length(btrim(idempotency_key)) > 0 AND request_fingerprint ~ '^[0-9a-f]{64}$'
    )
);

CREATE TABLE public.inventory_disposition_execution_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    disposition_line_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    stock_transaction_id BIGINT NOT NULL,
    posting_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_disposition_execution_line_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_inventory_disposition_execution_line_execution FOREIGN KEY (company_id, execution_id)
        REFERENCES public.inventory_disposition_execution(company_id, id),
    CONSTRAINT fk_inventory_disposition_execution_line_disposition FOREIGN KEY (company_id, disposition_line_id)
        REFERENCES public.inventory_disposition_line(company_id, id),
    CONSTRAINT fk_inventory_disposition_execution_line_batch FOREIGN KEY (company_id, inventory_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_inventory_disposition_execution_line_quantity CHECK (
        quantity > 0 AND quantity_before >= quantity AND quantity_after = quantity_before - quantity
    ),
    CONSTRAINT ck_inventory_disposition_execution_line_text CHECK (length(btrim(posting_id)) > 0)
);

CREATE TABLE public.inventory_disposition_reservation_impact (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    disposition_line_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    outbound_task_id BIGINT,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    impact_type VARCHAR(30) NOT NULL,
    from_batch_id BIGINT NOT NULL,
    to_batch_id BIGINT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_disposition_impact_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_inventory_disposition_impact_line FOREIGN KEY (company_id, disposition_line_id)
        REFERENCES public.inventory_disposition_line(company_id, id),
    CONSTRAINT ck_inventory_disposition_impact_type CHECK (
        impact_type IN ('REALLOCATED', 'BACKORDERED', 'QUALITY_EXCEPTION')
    ),
    CONSTRAINT ck_inventory_disposition_impact_shape CHECK (
        quantity > 0
        AND ((impact_type = 'REALLOCATED' AND to_batch_id IS NOT NULL)
            OR (impact_type <> 'REALLOCATED' AND to_batch_id IS NULL))
    )
);

CREATE INDEX idx_inventory_disposition_order_status
    ON public.inventory_disposition_order(company_id, status, created_at DESC);
CREATE INDEX idx_inventory_disposition_order_type
    ON public.inventory_disposition_order(company_id, disposition_type, created_at DESC);
CREATE INDEX idx_inventory_disposition_line_batch
    ON public.inventory_disposition_line(company_id, source_batch_id, disposition_order_id);
CREATE INDEX idx_inventory_disposition_execution_order
    ON public.inventory_disposition_execution(company_id, disposition_order_id, executed_at DESC);
CREATE INDEX idx_inventory_disposition_impact_order
    ON public.inventory_disposition_reservation_impact(company_id, sales_order_id, created_at DESC);

DO $inventory_disposition_rls$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'inventory_disposition_order', 'inventory_disposition_line',
        'inventory_disposition_execution', 'inventory_disposition_execution_line',
        'inventory_disposition_reservation_impact'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('CREATE POLICY company_isolation ON public.%I FOR ALL USING (company_id = public.bcwms_current_company_id()) WITH CHECK (company_id = public.bcwms_current_company_id())', table_name);
    END LOOP;
END
$inventory_disposition_rls$;

CREATE OR REPLACE FUNCTION public.prevent_inventory_disposition_evidence_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'inventory disposition evidence is append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_disposition_execution_immutable
BEFORE UPDATE OR DELETE ON public.inventory_disposition_execution
FOR EACH ROW EXECUTE FUNCTION public.prevent_inventory_disposition_evidence_mutation();
CREATE TRIGGER trg_inventory_disposition_execution_line_immutable
BEFORE UPDATE OR DELETE ON public.inventory_disposition_execution_line
FOR EACH ROW EXECUTE FUNCTION public.prevent_inventory_disposition_evidence_mutation();
CREATE TRIGGER trg_inventory_disposition_impact_immutable
BEFORE UPDATE OR DELETE ON public.inventory_disposition_reservation_impact
FOR EACH ROW EXECUTE FUNCTION public.prevent_inventory_disposition_evidence_mutation();

DO $inventory_disposition_permissions$
DECLARE company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES
        (company_record.id, 'inventory_disposition:view', '查看库存处置', 'API', '/api/inventory-dispositions/**', 'GET', 'ALL', 'NORMAL', TRUE, 'View warehouse-scoped damage, expiry and disposal records', 'ACTIVE', 160, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_disposition:report', '登记破损库存', 'API', '/api/inventory-dispositions/damage-preview;/api/inventory-dispositions/damage-reports', 'POST', 'ALL', 'HIGH', TRUE, 'Immediately make damaged stock unavailable with governed sales impact', 'ACTIVE', 161, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_disposition:create', '创建库存处置单', 'API', '/api/inventory-dispositions/scrap-orders;/api/inventory-dispositions/orders/*/submit;/api/inventory-dispositions/orders/*/cancel', 'POST', 'ALL', 'HIGH', TRUE, 'Create, submit or cancel scrap and destruction documents', 'ACTIVE', 162, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_disposition:execute', '执行库存处置', 'API', '/api/inventory-dispositions/orders/*/executions', 'POST', 'ALL', 'CRITICAL', TRUE, 'Confirm physical destruction and atomically post inventory loss', 'ACTIVE', 163, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_disposition:review', '审批库存处置', 'API', '/api/inventory-dispositions/orders/*/approve;/api/inventory-dispositions/orders/*/reject', 'POST', 'ALL', 'CRITICAL', TRUE, 'Approve or reject another user inventory disposition', 'ACTIVE', 164, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_disposition:policy', '配置库存处置策略', 'API', '/api/inventory-dispositions/policy', '*', 'ALL', 'CRITICAL', TRUE, 'Configure optional review, automatic expiry and business timezone', 'ACTIVE', 165, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, permission_code) DO UPDATE SET
            permission_name = EXCLUDED.permission_name,
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
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$inventory_disposition_permissions$;

COMMENT ON TABLE public.inventory_disposition_order IS
    'Governed damage, automatic expiry and physical scrap/destruction document.';
COMMENT ON TABLE public.inventory_disposition_execution IS
    'Append-only physical execution evidence; each execution is posted atomically.';
COMMENT ON TABLE public.inventory_disposition_reservation_impact IS
    'Append-only sales reservation impact caused by damage or automatic expiry.';
