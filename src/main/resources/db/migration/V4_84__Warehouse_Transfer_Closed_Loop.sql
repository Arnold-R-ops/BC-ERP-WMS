-- P0-04C: governed same-tenant, cross-warehouse transfer with dedicated
-- allocation, append-only dispatch/receipt evidence and real in-transit truth.

CREATE UNIQUE INDEX IF NOT EXISTS uk_v484_backorder_line_company_id
    ON public.backorder_line(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v484_stock_transactions_company_id
    ON public.stock_transactions(company_id, id);

CREATE TABLE public.transfer_order (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    transfer_number VARCHAR(40) NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    destination_warehouse_id BIGINT NOT NULL,
    business_status VARCHAR(30) NOT NULL,
    outbound_status VARCHAR(30) NOT NULL,
    inbound_status VARCHAR(30) NOT NULL,
    approval_mode_snapshot VARCHAR(20) NOT NULL,
    receiving_inspection_snapshot BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500) NOT NULL,
    estimated_arrival_at TIMESTAMP(6) WITHOUT TIME ZONE,
    requested_by BIGINT NOT NULL,
    requested_by_name VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    terminated_by BIGINT,
    terminated_by_name VARCHAR(100),
    terminated_at TIMESTAMP(6) WITHOUT TIME ZONE,
    termination_reason VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_order_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_order_number UNIQUE (company_id, transfer_number),
    CONSTRAINT fk_transfer_order_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_transfer_order_source FOREIGN KEY (company_id, source_warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT fk_transfer_order_destination FOREIGN KEY (company_id, destination_warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_transfer_order_version CHECK (version >= 0),
    CONSTRAINT ck_transfer_order_warehouses CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT ck_transfer_order_business CHECK (
        business_status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED','CLOSED','CANCELLED','VOIDED')
    ),
    CONSTRAINT ck_transfer_order_outbound CHECK (
        outbound_status IN ('NOT_RELEASED','PENDING','PARTIAL','DISPATCHED','CLOSED')
    ),
    CONSTRAINT ck_transfer_order_inbound CHECK (
        inbound_status IN ('NOT_IN_TRANSIT','IN_TRANSIT','PARTIAL_RECEIPT','RECEIVED','EXCEPTION','CLOSED')
    ),
    CONSTRAINT ck_transfer_order_approval CHECK (approval_mode_snapshot IN ('DIRECT','ALWAYS')),
    CONSTRAINT ck_transfer_order_text CHECK (
        length(btrim(transfer_number)) > 0 AND length(btrim(reason)) > 0
        AND length(btrim(requested_by_name)) > 0
    ),
    CONSTRAINT ck_transfer_order_review CHECK (
        (reviewed_by IS NULL AND reviewed_by_name IS NULL AND reviewed_at IS NULL)
        OR (reviewed_by IS NOT NULL AND reviewed_by_name IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_transfer_order_termination CHECK (
        (terminated_by IS NULL AND terminated_by_name IS NULL AND terminated_at IS NULL)
        OR (terminated_by IS NOT NULL AND terminated_by_name IS NOT NULL AND terminated_at IS NOT NULL)
    )
);

CREATE TABLE public.transfer_order_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    transfer_order_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    preferred_batch_id BIGINT,
    backorder_line_id BIGINT,
    requested_quantity INTEGER NOT NULL,
    allocated_quantity INTEGER NOT NULL DEFAULT 0,
    dispatched_quantity INTEGER NOT NULL DEFAULT 0,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    damaged_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    closed_unshipped_quantity INTEGER NOT NULL DEFAULT 0,
    shortage_closed_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_order_line_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_transfer_order_line_order FOREIGN KEY (company_id, transfer_order_id)
        REFERENCES public.transfer_order(company_id, id),
    CONSTRAINT fk_transfer_order_line_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT fk_transfer_order_line_preferred FOREIGN KEY (company_id, preferred_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_transfer_order_line_backorder FOREIGN KEY (company_id, backorder_line_id)
        REFERENCES public.backorder_line(company_id, id),
    CONSTRAINT ck_transfer_order_line_version CHECK (version >= 0),
    CONSTRAINT ck_transfer_order_line_quantities CHECK (
        requested_quantity > 0 AND allocated_quantity >= 0 AND dispatched_quantity >= 0
        AND received_quantity >= 0 AND damaged_quantity >= 0 AND quarantined_quantity >= 0
        AND closed_unshipped_quantity >= 0 AND shortage_closed_quantity >= 0
        AND allocated_quantity + dispatched_quantity + closed_unshipped_quantity <= requested_quantity
        AND received_quantity + shortage_closed_quantity <= dispatched_quantity
        AND damaged_quantity + quarantined_quantity <= received_quantity
    )
);

CREATE TABLE public.transfer_allocation (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    transfer_line_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    allocated_quantity INTEGER NOT NULL,
    consumed_quantity INTEGER NOT NULL DEFAULT 0,
    released_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_allocation_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_transfer_allocation_line FOREIGN KEY (company_id, transfer_line_id)
        REFERENCES public.transfer_order_line(company_id, id),
    CONSTRAINT fk_transfer_allocation_batch FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_transfer_allocation_version CHECK (version >= 0),
    CONSTRAINT ck_transfer_allocation_status CHECK (
        status IN ('ACTIVE','PARTIALLY_CONSUMED','CONSUMED','PARTIALLY_RELEASED','RELEASED')
    ),
    CONSTRAINT ck_transfer_allocation_quantities CHECK (
        allocated_quantity > 0 AND consumed_quantity >= 0 AND released_quantity >= 0
        AND consumed_quantity + released_quantity <= allocated_quantity
    )
);

CREATE TABLE public.transfer_warehouse_task (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    transfer_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    task_number VARCHAR(40) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 50,
    expected_from TIMESTAMP(6) WITHOUT TIME ZONE,
    expected_to TIMESTAMP(6) WITHOUT TIME ZONE,
    assignee VARCHAR(100),
    team_name VARCHAR(100),
    operation_area VARCHAR(100),
    warehouse_remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_warehouse_task_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_warehouse_task_number UNIQUE (company_id, task_number),
    CONSTRAINT uk_transfer_warehouse_task_direction UNIQUE (company_id, transfer_order_id, direction),
    CONSTRAINT fk_transfer_warehouse_task_order FOREIGN KEY (company_id, transfer_order_id)
        REFERENCES public.transfer_order(company_id, id),
    CONSTRAINT fk_transfer_warehouse_task_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_transfer_warehouse_task_version CHECK (version >= 0),
    CONSTRAINT ck_transfer_warehouse_task_direction CHECK (direction IN ('OUTBOUND','INBOUND')),
    CONSTRAINT ck_transfer_warehouse_task_status CHECK (
        status IN ('EXPECTED','PENDING','PARTIAL','COMPLETED','EXCEPTION','CLOSED')
    ),
    CONSTRAINT ck_transfer_warehouse_task_priority CHECK (priority BETWEEN 1 AND 100),
    CONSTRAINT ck_transfer_warehouse_task_window CHECK (
        expected_from IS NULL OR expected_to IS NULL OR expected_to >= expected_from
    )
);

CREATE TABLE public.transfer_dispatch (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    transfer_order_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    dispatch_number VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    handover_reference VARCHAR(120),
    estimated_arrival_at TIMESTAMP(6) WITHOUT TIME ZONE,
    warehouse_remark VARCHAR(500),
    dispatched_by BIGINT NOT NULL,
    dispatched_by_name VARCHAR(100) NOT NULL,
    dispatched_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_dispatch_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_dispatch_number UNIQUE (company_id, dispatch_number),
    CONSTRAINT uk_transfer_dispatch_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_transfer_dispatch_order FOREIGN KEY (company_id, transfer_order_id)
        REFERENCES public.transfer_order(company_id, id),
    CONSTRAINT fk_transfer_dispatch_task FOREIGN KEY (company_id, task_id)
        REFERENCES public.transfer_warehouse_task(company_id, id),
    CONSTRAINT ck_transfer_dispatch_text CHECK (
        length(btrim(dispatch_number)) > 0 AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$' AND length(btrim(dispatched_by_name)) > 0
    )
);

CREATE TABLE public.transfer_dispatch_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    dispatch_id BIGINT NOT NULL,
    transfer_line_id BIGINT NOT NULL,
    allocation_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    source_batch_code VARCHAR(50) NOT NULL,
    external_batch_code VARCHAR(100),
    production_date DATE,
    expiry_date DATE,
    quantity INTEGER NOT NULL,
    posting_id VARCHAR(120) NOT NULL,
    stock_transaction_id BIGINT NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    damaged_quantity INTEGER NOT NULL DEFAULT 0,
    quarantined_quantity INTEGER NOT NULL DEFAULT 0,
    shortage_closed_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_dispatch_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_dispatch_line_posting UNIQUE (company_id, posting_id),
    CONSTRAINT uk_transfer_dispatch_line_tx UNIQUE (company_id, stock_transaction_id),
    CONSTRAINT fk_transfer_dispatch_line_dispatch FOREIGN KEY (company_id, dispatch_id)
        REFERENCES public.transfer_dispatch(company_id, id),
    CONSTRAINT fk_transfer_dispatch_line_line FOREIGN KEY (company_id, transfer_line_id)
        REFERENCES public.transfer_order_line(company_id, id),
    CONSTRAINT fk_transfer_dispatch_line_allocation FOREIGN KEY (company_id, allocation_id)
        REFERENCES public.transfer_allocation(company_id, id),
    CONSTRAINT fk_transfer_dispatch_line_batch FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_transfer_dispatch_line_tx FOREIGN KEY (company_id, stock_transaction_id)
        REFERENCES public.stock_transactions(company_id, id),
    CONSTRAINT ck_transfer_dispatch_line_version CHECK (version >= 0),
    CONSTRAINT ck_transfer_dispatch_line_quantities CHECK (
        quantity > 0 AND received_quantity >= 0 AND damaged_quantity >= 0
        AND quarantined_quantity >= 0 AND shortage_closed_quantity >= 0
        AND received_quantity + shortage_closed_quantity <= quantity
        AND damaged_quantity + quarantined_quantity <= received_quantity
    ),
    CONSTRAINT ck_transfer_dispatch_line_text CHECK (
        length(btrim(source_batch_code)) > 0 AND length(btrim(posting_id)) > 0
    )
);

CREATE TABLE public.transfer_receipt (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    transfer_order_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    received_by BIGINT NOT NULL,
    received_by_name VARCHAR(100) NOT NULL,
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_receipt_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_receipt_number UNIQUE (company_id, receipt_number),
    CONSTRAINT uk_transfer_receipt_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_transfer_receipt_order FOREIGN KEY (company_id, transfer_order_id)
        REFERENCES public.transfer_order(company_id, id),
    CONSTRAINT fk_transfer_receipt_task FOREIGN KEY (company_id, task_id)
        REFERENCES public.transfer_warehouse_task(company_id, id),
    CONSTRAINT ck_transfer_receipt_text CHECK (
        length(btrim(receipt_number)) > 0 AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$' AND length(btrim(received_by_name)) > 0
    )
);

CREATE TABLE public.transfer_receipt_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL,
    dispatch_line_id BIGINT NOT NULL,
    transfer_line_id BIGINT NOT NULL,
    source_batch_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    destination_batch_id BIGINT NOT NULL,
    disposition VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    posting_id VARCHAR(120) NOT NULL,
    stock_transaction_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_receipt_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_transfer_receipt_line_batch UNIQUE (company_id, destination_batch_id),
    CONSTRAINT uk_transfer_receipt_line_posting UNIQUE (company_id, posting_id),
    CONSTRAINT uk_transfer_receipt_line_tx UNIQUE (company_id, stock_transaction_id),
    CONSTRAINT fk_transfer_receipt_line_receipt FOREIGN KEY (company_id, receipt_id)
        REFERENCES public.transfer_receipt(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_dispatch FOREIGN KEY (company_id, dispatch_line_id)
        REFERENCES public.transfer_dispatch_line(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_line FOREIGN KEY (company_id, transfer_line_id)
        REFERENCES public.transfer_order_line(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_source FOREIGN KEY (company_id, source_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_location FOREIGN KEY (company_id, location_id)
        REFERENCES public.locations(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_destination FOREIGN KEY (company_id, destination_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_transfer_receipt_line_tx FOREIGN KEY (company_id, stock_transaction_id)
        REFERENCES public.stock_transactions(company_id, id),
    CONSTRAINT ck_transfer_receipt_line_disposition CHECK (
        disposition IN ('AVAILABLE','DAMAGED','QUARANTINED')
    ),
    CONSTRAINT ck_transfer_receipt_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_transfer_receipt_line_batches CHECK (source_batch_id <> destination_batch_id),
    CONSTRAINT ck_transfer_receipt_line_text CHECK (length(btrim(posting_id)) > 0)
);

CREATE TABLE public.transfer_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    transfer_order_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transfer_event_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_transfer_event_order FOREIGN KEY (company_id, transfer_order_id)
        REFERENCES public.transfer_order(company_id, id),
    CONSTRAINT ck_transfer_event_text CHECK (
        length(btrim(event_type)) > 0 AND length(btrim(actor_name)) > 0
    )
);

CREATE INDEX idx_transfer_order_status
    ON public.transfer_order(company_id, business_status, outbound_status, inbound_status, id DESC);
CREATE INDEX idx_transfer_order_warehouses
    ON public.transfer_order(company_id, source_warehouse_id, destination_warehouse_id, id DESC);
CREATE INDEX idx_transfer_order_line_order ON public.transfer_order_line(company_id, transfer_order_id, id);
CREATE INDEX idx_transfer_order_line_backorder ON public.transfer_order_line(company_id, backorder_line_id)
    WHERE backorder_line_id IS NOT NULL;
CREATE INDEX idx_transfer_allocation_batch ON public.transfer_allocation(company_id, source_batch_id, status, id);
CREATE INDEX idx_transfer_task_queue
    ON public.transfer_warehouse_task(company_id, warehouse_id, direction, status, priority, id DESC);
CREATE INDEX idx_transfer_dispatch_order ON public.transfer_dispatch(company_id, transfer_order_id, id);
CREATE INDEX idx_transfer_dispatch_line_order ON public.transfer_dispatch_line(company_id, transfer_line_id, id);
CREATE INDEX idx_transfer_receipt_order ON public.transfer_receipt(company_id, transfer_order_id, id);
CREATE INDEX idx_transfer_event_order ON public.transfer_event(company_id, transfer_order_id, id);

DO $transfer_rls$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'transfer_order','transfer_order_line','transfer_allocation','transfer_warehouse_task',
        'transfer_dispatch','transfer_dispatch_line','transfer_receipt','transfer_receipt_line','transfer_event'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('CREATE POLICY company_isolation ON public.%I FOR ALL USING (company_id = public.bcwms_current_company_id()) WITH CHECK (company_id = public.bcwms_current_company_id())', table_name);
    END LOOP;
END
$transfer_rls$;

CREATE OR REPLACE FUNCTION public.prevent_transfer_evidence_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN RETURN OLD; END IF;
    RAISE EXCEPTION 'transfer evidence is append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transfer_dispatch_immutable BEFORE UPDATE OR DELETE ON public.transfer_dispatch
FOR EACH ROW EXECUTE FUNCTION public.prevent_transfer_evidence_mutation();
CREATE TRIGGER trg_transfer_receipt_immutable BEFORE UPDATE OR DELETE ON public.transfer_receipt
FOR EACH ROW EXECUTE FUNCTION public.prevent_transfer_evidence_mutation();
CREATE TRIGGER trg_transfer_receipt_line_immutable BEFORE UPDATE OR DELETE ON public.transfer_receipt_line
FOR EACH ROW EXECUTE FUNCTION public.prevent_transfer_evidence_mutation();
CREATE TRIGGER trg_transfer_event_immutable BEFORE UPDATE OR DELETE ON public.transfer_event
FOR EACH ROW EXECUTE FUNCTION public.prevent_transfer_evidence_mutation();

-- Reuse the existing receiving-inspection state machine for optional transfer receipt inspection.
ALTER TABLE public.receiving_inspection_task
    ADD COLUMN transfer_receipt_id BIGINT,
    DROP CONSTRAINT ck_receiving_inspection_task_source,
    DROP CONSTRAINT ck_receiving_inspection_task_shape,
    ADD CONSTRAINT uk_receiving_inspection_task_transfer_receipt
        UNIQUE (company_id, transfer_receipt_id),
    ADD CONSTRAINT fk_receiving_inspection_task_transfer_receipt
        FOREIGN KEY (company_id, transfer_receipt_id)
        REFERENCES public.transfer_receipt(company_id, id),
    ADD CONSTRAINT ck_receiving_inspection_task_source
        CHECK (source_type IN ('RECEIPT','TRANSFER_RECEIPT','REINSPECTION')),
    ADD CONSTRAINT ck_receiving_inspection_task_shape CHECK (
        (source_type = 'RECEIPT' AND receipt_event_id IS NOT NULL
            AND receiving_task_id IS NOT NULL AND transfer_receipt_id IS NULL
            AND correction_of_result_id IS NULL)
        OR
        (source_type = 'TRANSFER_RECEIPT' AND receipt_event_id IS NULL
            AND receiving_task_id IS NULL AND transfer_receipt_id IS NOT NULL
            AND correction_of_result_id IS NULL)
        OR
        (source_type = 'REINSPECTION' AND receipt_event_id IS NULL
            AND receiving_task_id IS NULL AND transfer_receipt_id IS NULL
            AND correction_of_result_id IS NOT NULL)
    );

ALTER TABLE public.receiving_inspection_task_line
    ADD COLUMN transfer_receipt_line_id BIGINT,
    ADD CONSTRAINT uk_receiving_inspection_line_transfer_receipt
        UNIQUE (company_id, inspection_task_id, transfer_receipt_line_id),
    ADD CONSTRAINT fk_receiving_inspection_line_transfer_receipt
        FOREIGN KEY (company_id, transfer_receipt_line_id)
        REFERENCES public.transfer_receipt_line(company_id, id),
    ADD CONSTRAINT ck_receiving_inspection_line_source_shape CHECK (
        receipt_event_item_id IS NULL OR transfer_receipt_line_id IS NULL
    );

DO $transfer_policy_permissions$
DECLARE company_record RECORD;
DECLARE obsolete_permission_id BIGINT;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        -- Existing development-only permission migrations used CRITICAL + assignable,
        -- which is prohibited by the database. Keep tenant-controlled permissions HIGH.
        UPDATE public.sys_permission
        SET risk_level = 'HIGH', updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND risk_level = 'CRITICAL' AND custom_assignable = TRUE
          AND permission_code IN (
              'inventory_quality:review','inventory_quality:policy',
              'receiving_inspection:review','receiving_inspection:correct','receiving_inspection:policy',
              'inventory_disposition:execute','inventory_disposition:review','inventory_disposition:policy'
          );

        SELECT id INTO obsolete_permission_id
        FROM public.sys_permission
        WHERE company_id = company_record.id AND permission_code = 'inventory:transfer';
        IF obsolete_permission_id IS NOT NULL THEN
            DELETE FROM public.sys_role_permission
            WHERE company_id = company_record.id AND permission_id = obsolete_permission_id;
            DELETE FROM public.sys_permission
            WHERE company_id = company_record.id AND id = obsolete_permission_id;
            UPDATE public.users SET security_version = security_version + 1
            WHERE company_id = company_record.id;
        END IF;
        obsolete_permission_id := NULL;

        INSERT INTO public.system_config (
            company_id, config_key, config_value, description, config_type, created_at, updated_at
        ) VALUES
        (company_record.id, 'transfer.approval.mode', 'DIRECT',
            'Tenant-selectable transfer approval mode', 'STRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer.receiving_inspection.enabled', 'false',
            'Route good transfer receipts through receiving inspection', 'BOOLEAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, config_key) DO NOTHING;

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type, resource_path, http_method,
            data_scope, risk_level, custom_assignable, description, status, sort_order, created_at, updated_at
        ) VALUES
        (company_record.id, 'transfer:view', '查看仓间调拨', 'API', '/api/transfers/**', 'GET', 'ALL', 'NORMAL', TRUE,
            'View transfer business details and three-layer progress', 'ACTIVE', 174, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:create', '创建和维护调拨', 'API', '/api/transfers/**', '*', 'ALL', 'HIGH', TRUE,
            'Create, submit, edit, cancel, void and reallocate transfers', 'ACTIVE', 175, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:approve', '审批仓间调拨', 'API', '/api/transfers/**', '*', 'ALL', 'HIGH', TRUE,
            'Approve or reject another user transfer', 'ACTIVE', 176, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:dispatch', '执行调拨出库', 'API', '/api/transfer-warehouse/tasks/**', '*', 'ALL', 'HIGH', TRUE,
            'Dispatch transfer allocations within the assigned source warehouse', 'ACTIVE', 177, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:receive', '执行调拨入库', 'API', '/api/transfer-warehouse/tasks/**', '*', 'ALL', 'HIGH', TRUE,
            'Receive dispatched transfer stock within the assigned destination warehouse', 'ACTIVE', 178, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:close', '关闭调拨差异', 'API', '/api/transfers/**', '*', 'ALL', 'HIGH', TRUE,
            'Close unshipped or in-transit transfer differences with a reason and confirmation', 'ACTIVE', 179, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'transfer:policy', '配置调拨策略', 'API', '/api/transfers/**', '*', 'ALL', 'HIGH', TRUE,
            'Configure transfer approval and optional receiving inspection', 'ACTIVE', 180, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, permission_code) DO UPDATE SET
            permission_name = EXCLUDED.permission_name, permission_type = EXCLUDED.permission_type,
            resource_path = EXCLUDED.resource_path, http_method = EXCLUDED.http_method,
            data_scope = EXCLUDED.data_scope, risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable, description = EXCLUDED.description,
            status = EXCLUDED.status, sort_order = EXCLUDED.sort_order, updated_at = CURRENT_TIMESTAMP;
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$transfer_policy_permissions$;

COMMENT ON TABLE public.transfer_order IS
    'Same-tenant transfer truth with independent business, outbound and inbound status layers.';
COMMENT ON TABLE public.transfer_allocation IS
    'Dedicated transfer reservation ledger mirrored into inventory_batch.reserved_quantity.';
COMMENT ON TABLE public.transfer_dispatch IS
    'Append-only authenticated source-warehouse dispatch evidence; no carrier data.';
COMMENT ON TABLE public.transfer_receipt IS
    'Append-only authenticated destination-warehouse receipt evidence.';
COMMENT ON COLUMN public.transfer_receipt_line.source_batch_id IS
    'Immutable source lineage; destination_batch_id is always a newly created batch row.';
COMMENT ON COLUMN public.transfer_order_line.allocated_quantity IS
    'Current open transfer reservation; dispatched allocation is removed from this projection.';
