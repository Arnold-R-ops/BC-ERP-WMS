-- P0-03C: receipt inspection, pending-inspection release and governed reinspection.
-- Accepted goods are always posted as physical on-hand. The SKU/tenant policy
-- decides whether the new batch is AVAILABLE or PENDING_INSPECTION.

ALTER TABLE public.product_skus
    ADD COLUMN IF NOT EXISTS inspection_requirement VARCHAR(20);

UPDATE public.product_skus
SET inspection_requirement = 'INHERIT'
WHERE inspection_requirement IS NULL;

ALTER TABLE public.product_skus
    ALTER COLUMN inspection_requirement SET DEFAULT 'INHERIT',
    ALTER COLUMN inspection_requirement SET NOT NULL,
    ADD CONSTRAINT ck_product_skus_inspection_requirement
        CHECK (inspection_requirement IN ('INHERIT', 'REQUIRED', 'EXEMPT'));

ALTER TABLE public.receipt_event_item
    ADD COLUMN IF NOT EXISTS inspection_required BOOLEAN,
    ADD COLUMN IF NOT EXISTS quality_status_at_receipt VARCHAR(30);

UPDATE public.receipt_event_item
SET inspection_required = FALSE,
    quality_status_at_receipt = 'AVAILABLE'
WHERE inspection_required IS NULL OR quality_status_at_receipt IS NULL;

ALTER TABLE public.receipt_event_item
    ALTER COLUMN inspection_required SET DEFAULT FALSE,
    ALTER COLUMN inspection_required SET NOT NULL,
    ALTER COLUMN quality_status_at_receipt SET DEFAULT 'AVAILABLE',
    ALTER COLUMN quality_status_at_receipt SET NOT NULL,
    ADD CONSTRAINT ck_receipt_event_item_quality_status
        CHECK (quality_status_at_receipt IN ('AVAILABLE', 'PENDING_INSPECTION'));

CREATE TABLE public.receiving_inspection_task (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    task_number VARCHAR(40) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    receipt_event_id BIGINT,
    receiving_task_id BIGINT,
    correction_of_result_id BIGINT,
    warehouse_id BIGINT NOT NULL,
    source_reference VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    review_mode_snapshot VARCHAR(30) NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_inspection_task_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_inspection_task_number UNIQUE (company_id, task_number),
    CONSTRAINT uk_receiving_inspection_task_receipt UNIQUE (company_id, receipt_event_id),
    CONSTRAINT fk_receiving_inspection_task_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_inspection_task_receipt FOREIGN KEY (company_id, receipt_event_id)
        REFERENCES public.receipt_event(company_id, id),
    CONSTRAINT fk_receiving_inspection_task_receiving FOREIGN KEY (company_id, receiving_task_id)
        REFERENCES public.receiving_task(company_id, id),
    CONSTRAINT fk_receiving_inspection_task_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_receiving_inspection_task_version CHECK (version >= 0),
    CONSTRAINT ck_receiving_inspection_task_source CHECK (source_type IN ('RECEIPT', 'REINSPECTION')),
    CONSTRAINT ck_receiving_inspection_task_status CHECK (
        status IN ('PENDING', 'IN_PROGRESS', 'PENDING_REVIEW', 'COMPLETED')
    ),
    CONSTRAINT ck_receiving_inspection_task_review_mode CHECK (
        review_mode_snapshot IN ('DIRECT', 'REVIEW_REQUIRED')
    ),
    CONSTRAINT ck_receiving_inspection_task_text CHECK (
        length(btrim(task_number)) > 0
        AND length(btrim(source_reference)) > 0
        AND length(btrim(created_by_name)) > 0
    )
);

CREATE TABLE public.receiving_inspection_task_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    inspection_task_id BIGINT NOT NULL,
    receipt_event_item_id BIGINT,
    product_sku_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    sku_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    batch_code VARCHAR(50) NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    planned_quantity INTEGER NOT NULL,
    pending_quantity INTEGER NOT NULL,
    pending_review_quantity INTEGER NOT NULL DEFAULT 0,
    passed_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_inspection_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_inspection_line_receipt_item
        UNIQUE (company_id, inspection_task_id, receipt_event_item_id),
    CONSTRAINT fk_receiving_inspection_line_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_inspection_line_task FOREIGN KEY (company_id, inspection_task_id)
        REFERENCES public.receiving_inspection_task(company_id, id),
    CONSTRAINT fk_receiving_inspection_line_receipt_item FOREIGN KEY (company_id, receipt_event_item_id)
        REFERENCES public.receipt_event_item(company_id, id),
    CONSTRAINT fk_receiving_inspection_line_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT fk_receiving_inspection_line_batch FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_receiving_inspection_line_location FOREIGN KEY (company_id, location_id)
        REFERENCES public.locations(company_id, id),
    CONSTRAINT ck_receiving_inspection_line_version CHECK (version >= 0),
    CONSTRAINT ck_receiving_inspection_line_quantities CHECK (
        planned_quantity > 0 AND pending_quantity >= 0
        AND pending_review_quantity >= 0 AND passed_quantity >= 0
        AND quarantined_quantity >= 0
        AND pending_quantity + pending_review_quantity + passed_quantity
            + quarantined_quantity = planned_quantity
    ),
    CONSTRAINT ck_receiving_inspection_line_text CHECK (
        length(btrim(sku_code)) > 0 AND length(btrim(product_name)) > 0
        AND length(btrim(batch_code)) > 0 AND length(btrim(location_code)) > 0
    )
);

CREATE TABLE public.receiving_inspection_result (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    result_number VARCHAR(40) NOT NULL,
    inspection_task_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    inspection_method VARCHAR(20) NOT NULL,
    sample_quantity INTEGER,
    defect_quantity INTEGER NOT NULL DEFAULT 0,
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(500),
    submitted_by BIGINT NOT NULL,
    submitted_by_name VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_inspection_result_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_inspection_result_number UNIQUE (company_id, result_number),
    CONSTRAINT uk_receiving_inspection_result_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_receiving_inspection_result_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_inspection_result_task FOREIGN KEY (company_id, inspection_task_id)
        REFERENCES public.receiving_inspection_task(company_id, id),
    CONSTRAINT ck_receiving_inspection_result_version CHECK (version >= 0),
    CONSTRAINT ck_receiving_inspection_result_status CHECK (
        status IN ('COMPLETED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_receiving_inspection_result_method CHECK (
        inspection_method IN ('FULL', 'SAMPLING')
    ),
    CONSTRAINT ck_receiving_inspection_result_sample CHECK (
        (inspection_method = 'FULL' AND sample_quantity IS NULL AND defect_quantity >= 0)
        OR
        (inspection_method = 'SAMPLING' AND sample_quantity > 0
            AND defect_quantity >= 0 AND defect_quantity <= sample_quantity)
    ),
    CONSTRAINT ck_receiving_inspection_result_text CHECK (
        length(btrim(result_number)) > 0 AND length(btrim(reason_code)) > 0
        AND length(btrim(submitted_by_name)) > 0 AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_receiving_inspection_result_review CHECK (
        (status IN ('COMPLETED', 'PENDING_REVIEW') AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL AND length(btrim(review_comment)) > 0)
    ),
    CONSTRAINT ck_receiving_inspection_result_separation CHECK (
        status IN ('COMPLETED', 'PENDING_REVIEW') OR reviewed_by <> submitted_by
    )
);

ALTER TABLE public.receiving_inspection_task
    ADD CONSTRAINT fk_receiving_inspection_task_correction
        FOREIGN KEY (company_id, correction_of_result_id)
        REFERENCES public.receiving_inspection_result(company_id, id),
    ADD CONSTRAINT ck_receiving_inspection_task_shape CHECK (
        (source_type = 'RECEIPT' AND receipt_event_id IS NOT NULL
            AND receiving_task_id IS NOT NULL AND correction_of_result_id IS NULL)
        OR
        (source_type = 'REINSPECTION' AND receipt_event_id IS NULL
            AND receiving_task_id IS NULL AND correction_of_result_id IS NOT NULL)
    );

CREATE TABLE public.receiving_inspection_result_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    inspection_result_id BIGINT NOT NULL,
    inspection_task_line_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    passed_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    passed_batch_id BIGINT,
    quarantined_batch_id BIGINT,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_inspection_result_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_inspection_result_line_task_line
        UNIQUE (company_id, inspection_result_id, inspection_task_line_id),
    CONSTRAINT fk_receiving_inspection_result_line_result FOREIGN KEY (company_id, inspection_result_id)
        REFERENCES public.receiving_inspection_result(company_id, id),
    CONSTRAINT fk_receiving_inspection_result_line_task_line FOREIGN KEY (company_id, inspection_task_line_id)
        REFERENCES public.receiving_inspection_task_line(company_id, id),
    CONSTRAINT fk_receiving_inspection_result_line_source FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_receiving_inspection_result_line_passed FOREIGN KEY (company_id, passed_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_receiving_inspection_result_line_quarantined FOREIGN KEY (company_id, quarantined_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_receiving_inspection_result_line_quantity CHECK (
        passed_quantity >= 0 AND quarantined_quantity >= 0
        AND passed_quantity + quarantined_quantity > 0
    )
);

CREATE TABLE public.receiving_arrival_exception (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    exception_number VARCHAR(40) NOT NULL,
    receiving_task_id BIGINT NOT NULL,
    product_sku_id BIGINT,
    exception_type VARCHAR(30) NOT NULL,
    disposition VARCHAR(40) NOT NULL,
    observed_quantity INTEGER NOT NULL,
    reason_detail VARCHAR(500),
    recorded_by BIGINT NOT NULL,
    recorded_by_name VARCHAR(100) NOT NULL,
    recorded_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_arrival_exception_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_arrival_exception_number UNIQUE (company_id, exception_number),
    CONSTRAINT uk_receiving_arrival_exception_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_receiving_arrival_exception_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_arrival_exception_task FOREIGN KEY (company_id, receiving_task_id)
        REFERENCES public.receiving_task(company_id, id),
    CONSTRAINT fk_receiving_arrival_exception_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT ck_receiving_arrival_exception_type CHECK (
        exception_type IN ('SHORT_RECEIPT', 'OVER_RECEIPT', 'WRONG_ITEM', 'DAMAGED', 'EXPIRY_FAILED', 'OTHER')
    ),
    CONSTRAINT ck_receiving_arrival_exception_disposition CHECK (
        disposition IN ('AWAITING_REDELIVERY', 'REJECTED_AT_DOCK', 'RECEIVED_FOR_INSPECTION', 'RECORDED_ONLY')
    ),
    CONSTRAINT ck_receiving_arrival_exception_quantity CHECK (observed_quantity > 0),
    CONSTRAINT ck_receiving_arrival_exception_text CHECK (
        length(btrim(exception_number)) > 0 AND length(btrim(recorded_by_name)) > 0
        AND length(btrim(idempotency_key)) > 0 AND request_fingerprint ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_receiving_inspection_task_warehouse_status
    ON public.receiving_inspection_task(company_id, warehouse_id, status, created_at DESC);
CREATE INDEX idx_receiving_inspection_line_task
    ON public.receiving_inspection_task_line(company_id, inspection_task_id, id);
CREATE INDEX idx_receiving_inspection_result_task
    ON public.receiving_inspection_result(company_id, inspection_task_id, created_at DESC);
CREATE INDEX idx_receiving_inspection_result_line_result
    ON public.receiving_inspection_result_line(company_id, inspection_result_id, id);
CREATE INDEX idx_receiving_arrival_exception_task
    ON public.receiving_arrival_exception(company_id, receiving_task_id, created_at DESC);

DO $receiving_inspection_rls$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'receiving_inspection_task', 'receiving_inspection_task_line',
        'receiving_inspection_result', 'receiving_inspection_result_line',
        'receiving_arrival_exception'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('CREATE POLICY company_isolation ON public.%I FOR ALL USING (company_id = public.bcwms_current_company_id()) WITH CHECK (company_id = public.bcwms_current_company_id())', table_name);
    END LOOP;
END
$receiving_inspection_rls$;

DO $receiving_inspection_permissions$
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
        (company_record.id, 'receiving_inspection:view', '查看收货质检任务', 'API', '/api/receiving-inspections/**', 'GET', 'ALL', 'NORMAL', TRUE, 'View warehouse-scoped receipt inspection tasks and safe receipt data', 'ACTIVE', 155, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'receiving_inspection:execute', '执行收货质检', 'API', '/api/receiving-inspections/*/results', 'POST', 'ALL', 'HIGH', TRUE, 'Submit full or sampling inspection decisions', 'ACTIVE', 156, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'receiving_inspection:review', '复核质检放行', 'API', '/api/receiving-inspections/results/*/approve;/api/receiving-inspections/results/*/reject', 'POST', 'ALL', 'CRITICAL', TRUE, 'Approve or reject another user release-to-saleable decision', 'ACTIVE', 157, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'receiving_inspection:correct', '发起质检更正复检', 'API', '/api/receiving-inspections/reinspections', 'POST', 'ALL', 'CRITICAL', TRUE, 'Create an immutable correction and reinspection chain', 'ACTIVE', 158, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'receiving_inspection:policy', '配置收货质检策略', 'API', '/api/receiving-inspections/policy', '*', 'ALL', 'CRITICAL', TRUE, 'Configure optional tenant inspection and release review policy', 'ACTIVE', 159, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
$receiving_inspection_permissions$;

COMMENT ON TABLE public.receiving_inspection_task IS
    'One warehouse-safe inspection task per receipt event, plus immutable correction/reinspection tasks.';
COMMENT ON TABLE public.receiving_inspection_result IS
    'Append-only inspection decision header. Approved release and immediate quarantine are applied atomically.';
COMMENT ON TABLE public.receiving_arrival_exception IS
    'Pre-receipt dock exception evidence. Rejected quantities never create inventory.';
