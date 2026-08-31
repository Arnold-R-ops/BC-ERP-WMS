-- P0-04B: linked sales-return authorisation, warehouse receipt, return
-- inspection and external financial-resolution evidence.

ALTER TABLE public.sales_orders
    ADD COLUMN order_purpose VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN source_rma_id BIGINT;
ALTER TABLE public.sales_orders
    ADD CONSTRAINT ck_sales_orders_purpose CHECK (order_purpose IN ('NORMAL','EXCHANGE_REPLACEMENT')),
    ADD CONSTRAINT ck_sales_orders_replacement_shape CHECK (
        (order_purpose = 'NORMAL' AND source_rma_id IS NULL)
        OR (order_purpose = 'EXCHANGE_REPLACEMENT' AND source_rma_id IS NOT NULL)
    );
CREATE INDEX idx_sales_orders_source_rma ON public.sales_orders(company_id, source_rma_id);

-- Tenant-safe composite reference targets used by the RMA aggregate.
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_sales_orders_company_id ON public.sales_orders(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_sales_order_items_company_id ON public.sales_order_items(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_outbound_tasks_company_id ON public.outbound_tasks(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_inventory_batch_company_id ON public.inventory_batch(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_product_skus_company_id ON public.product_skus(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_warehouses_company_id ON public.warehouses(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_v483_locations_company_id ON public.locations(company_id, id);

CREATE TABLE public.sales_return_rma (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    rma_number VARCHAR(40) NOT NULL,
    sales_order_id BIGINT NOT NULL,
    shipment_id BIGINT,
    warehouse_id BIGINT NOT NULL,
    rma_type VARCHAR(30) NOT NULL,
    business_status VARCHAR(30) NOT NULL,
    receipt_status VARCHAR(30) NOT NULL,
    inspection_status VARCHAR(30) NOT NULL,
    resolution_status VARCHAR(40) NOT NULL,
    approval_mode_snapshot VARCHAR(30) NOT NULL,
    customer_type_snapshot VARCHAR(20) NOT NULL,
    statutory_basis VARCHAR(40) NOT NULL,
    standard_return_days_snapshot INTEGER NOT NULL,
    outside_standard_window BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(1000),
    customer_shipped_at TIMESTAMP(6) WITHOUT TIME ZONE,
    return_carrier VARCHAR(80),
    return_tracking_no VARCHAR(120),
    requested_by BIGINT NOT NULL,
    requested_by_name VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    cancelled_or_closed_by BIGINT,
    cancelled_or_closed_by_name VARCHAR(100),
    cancelled_or_closed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    termination_reason VARCHAR(500),
    refund_amount NUMERIC(15,2),
    refund_currency VARCHAR(3),
    refund_reference VARCHAR(120),
    advance_resolution BOOLEAN NOT NULL DEFAULT FALSE,
    replacement_sales_order_id BIGINT,
    channel VARCHAR(50),
    external_case_id VARCHAR(120),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_rma_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_rma_company_number UNIQUE (company_id, rma_number),
    CONSTRAINT uk_sales_return_rma_company_channel_case UNIQUE (company_id, channel, external_case_id),
    CONSTRAINT fk_sales_return_rma_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_sales_return_rma_order FOREIGN KEY (company_id, sales_order_id)
        REFERENCES public.sales_orders(company_id, id),
    CONSTRAINT fk_sales_return_rma_shipment FOREIGN KEY (company_id, shipment_id)
        REFERENCES public.sales_order_shipments(company_id, id),
    CONSTRAINT fk_sales_return_rma_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT fk_sales_return_rma_replacement FOREIGN KEY (company_id, replacement_sales_order_id)
        REFERENCES public.sales_orders(company_id, id),
    CONSTRAINT ck_sales_return_rma_version CHECK (version >= 0),
    CONSTRAINT ck_sales_return_rma_type CHECK (rma_type IN ('RETURN_REFUND','EXCHANGE','REFUND_ONLY')),
    CONSTRAINT ck_sales_return_rma_business CHECK (business_status IN ('PENDING_APPROVAL','AUTHORIZED','REJECTED','CANCELLED','CLOSED')),
    CONSTRAINT ck_sales_return_rma_receipt CHECK (receipt_status IN ('NOT_EXPECTED','EXPECTED','PARTIAL','RECEIVED','CLOSED')),
    CONSTRAINT ck_sales_return_rma_inspection CHECK (inspection_status IN ('NOT_REQUIRED','PENDING','PARTIAL','COMPLETED')),
    CONSTRAINT ck_sales_return_rma_resolution CHECK (resolution_status IN ('PENDING','REFUND_REQUESTED','REFUND_CONFIRMED','EXCHANGE_CREATED','REJECTED_GOODS_PENDING','COMPLETED','EXCEPTION')),
    CONSTRAINT ck_sales_return_rma_approval CHECK (approval_mode_snapshot IN ('DIRECT','RISK_BASED','ALWAYS')),
    CONSTRAINT ck_sales_return_rma_customer CHECK (customer_type_snapshot IN ('CLIENT','CONSUMER')),
    CONSTRAINT ck_sales_return_rma_basis CHECK (statutory_basis IN ('VOLUNTARY_POLICY','DISTANCE_CANCELLATION','FAULTY_NOT_AS_DESCRIBED','OTHER_STATUTORY')),
    CONSTRAINT ck_sales_return_rma_window CHECK (standard_return_days_snapshot > 0),
    CONSTRAINT ck_sales_return_rma_text CHECK (length(btrim(rma_number)) > 0 AND length(btrim(reason_code)) > 0 AND length(btrim(requested_by_name)) > 0),
    CONSTRAINT ck_sales_return_rma_refund CHECK ((refund_amount IS NULL AND refund_currency IS NULL) OR (refund_amount >= 0 AND refund_currency ~ '^[A-Z]{3}$')),
    CONSTRAINT ck_sales_return_rma_channel_case CHECK (external_case_id IS NULL OR channel IS NOT NULL),
    CONSTRAINT ck_sales_return_rma_refund_only CHECK (
        (rma_type = 'REFUND_ONLY' AND receipt_status = 'NOT_EXPECTED' AND inspection_status = 'NOT_REQUIRED')
        OR rma_type <> 'REFUND_ONLY'
    )
);

CREATE TABLE public.sales_return_channel_case (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    store_identifier VARCHAR(255) NOT NULL,
    external_case_id VARCHAR(120) NOT NULL,
    external_order_id VARCHAR(120),
    raw_event_id BIGINT NOT NULL,
    sales_order_id BIGINT,
    status VARCHAR(30) NOT NULL,
    rma_id BIGINT,
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    matched_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_channel_case_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_channel_case_external UNIQUE (company_id, channel, store_identifier, external_case_id),
    CONSTRAINT fk_sales_return_channel_case_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_sales_return_channel_case_order FOREIGN KEY (company_id, sales_order_id)
        REFERENCES public.sales_orders(company_id, id),
    CONSTRAINT fk_sales_return_channel_case_rma FOREIGN KEY (company_id, rma_id)
        REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT ck_sales_return_channel_case_status CHECK (status IN ('UNMATCHED_PENDING','MATCHED_PENDING','RMA_CREATED','DISMISSED')),
    CONSTRAINT ck_sales_return_channel_case_shape CHECK (
        (status = 'UNMATCHED_PENDING' AND sales_order_id IS NULL AND rma_id IS NULL AND matched_at IS NULL)
        OR (status = 'MATCHED_PENDING' AND sales_order_id IS NOT NULL AND rma_id IS NULL AND matched_at IS NOT NULL)
        OR (status = 'RMA_CREATED' AND sales_order_id IS NOT NULL AND rma_id IS NOT NULL AND matched_at IS NOT NULL)
        OR status = 'DISMISSED'
    ),
    CONSTRAINT ck_sales_return_channel_case_text CHECK (
        length(btrim(channel)) > 0 AND length(btrim(store_identifier)) > 0 AND length(btrim(external_case_id)) > 0
    )
);

ALTER TABLE public.sales_orders
    ADD CONSTRAINT fk_sales_orders_source_rma FOREIGN KEY (company_id, source_rma_id)
        REFERENCES public.sales_return_rma(company_id, id);

CREATE TABLE public.sales_return_rma_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    rma_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    outbound_task_id BIGINT NOT NULL,
    original_batch_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    requested_quantity INTEGER NOT NULL,
    authorized_quantity INTEGER NOT NULL DEFAULT 0,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    inspected_quantity INTEGER NOT NULL DEFAULT 0,
    restock_quantity INTEGER NOT NULL DEFAULT 0,
    damaged_quantity INTEGER NOT NULL DEFAULT 0,
    quarantine_quantity INTEGER NOT NULL DEFAULT 0,
    rejected_quantity INTEGER NOT NULL DEFAULT 0,
    refund_eligible_quantity INTEGER NOT NULL DEFAULT 0,
    unit_price_snapshot NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_rma_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_rma_line_dispatch UNIQUE (company_id, rma_id, outbound_task_id),
    CONSTRAINT fk_sales_return_rma_line_rma FOREIGN KEY (company_id, rma_id) REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT fk_sales_return_rma_line_item FOREIGN KEY (company_id, sales_order_item_id) REFERENCES public.sales_order_items(company_id, id),
    CONSTRAINT fk_sales_return_rma_line_task FOREIGN KEY (company_id, outbound_task_id) REFERENCES public.outbound_tasks(company_id, id),
    CONSTRAINT fk_sales_return_rma_line_batch FOREIGN KEY (company_id, original_batch_id) REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_sales_return_rma_line_sku FOREIGN KEY (company_id, product_sku_id) REFERENCES public.product_skus(company_id, id),
    CONSTRAINT ck_sales_return_rma_line_qty CHECK (
        requested_quantity > 0 AND authorized_quantity BETWEEN 0 AND requested_quantity
        AND received_quantity BETWEEN 0 AND authorized_quantity
        AND inspected_quantity BETWEEN 0 AND received_quantity
        AND restock_quantity >= 0 AND damaged_quantity >= 0 AND quarantine_quantity >= 0
        AND rejected_quantity >= 0 AND refund_eligible_quantity >= 0
        AND restock_quantity + damaged_quantity + quarantine_quantity + rejected_quantity = inspected_quantity
        AND refund_eligible_quantity <= inspected_quantity
    ),
    CONSTRAINT ck_sales_return_rma_line_price CHECK (unit_price_snapshot >= 0)
);

CREATE TABLE public.sales_return_task (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    rma_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    task_number VARCHAR(40) NOT NULL,
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
    CONSTRAINT uk_sales_return_task_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_task_company_rma UNIQUE (company_id, rma_id),
    CONSTRAINT uk_sales_return_task_company_number UNIQUE (company_id, task_number),
    CONSTRAINT fk_sales_return_task_rma FOREIGN KEY (company_id, rma_id) REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT fk_sales_return_task_warehouse FOREIGN KEY (company_id, warehouse_id) REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_sales_return_task_status CHECK (status IN ('EXPECTED','PARTIAL','RECEIVED','CLOSED')),
    CONSTRAINT ck_sales_return_task_priority CHECK (priority BETWEEN 1 AND 100),
    CONSTRAINT ck_sales_return_task_window CHECK (expected_to IS NULL OR expected_from IS NULL OR expected_to >= expected_from)
);

CREATE TABLE public.sales_return_receipt (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    rma_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    received_by BIGINT NOT NULL,
    received_by_name VARCHAR(100) NOT NULL,
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_receipt_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_receipt_company_number UNIQUE (company_id, receipt_number),
    CONSTRAINT fk_sales_return_receipt_rma FOREIGN KEY (company_id, rma_id) REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT fk_sales_return_receipt_task FOREIGN KEY (company_id, task_id) REFERENCES public.sales_return_task(company_id, id),
    CONSTRAINT ck_sales_return_receipt_actor CHECK (length(btrim(received_by_name)) > 0)
);

CREATE TABLE public.sales_return_receipt_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL,
    rma_line_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    return_batch_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_receipt_line_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_receipt_line_batch UNIQUE (company_id, return_batch_id),
    CONSTRAINT fk_sales_return_receipt_line_receipt FOREIGN KEY (company_id, receipt_id) REFERENCES public.sales_return_receipt(company_id, id),
    CONSTRAINT fk_sales_return_receipt_line_rma_line FOREIGN KEY (company_id, rma_line_id) REFERENCES public.sales_return_rma_line(company_id, id),
    CONSTRAINT fk_sales_return_receipt_line_location FOREIGN KEY (company_id, location_id) REFERENCES public.locations(company_id, id),
    CONSTRAINT fk_sales_return_receipt_line_batch FOREIGN KEY (company_id, return_batch_id) REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_sales_return_receipt_line_qty CHECK (quantity > 0)
);

CREATE TABLE public.sales_return_inspection (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    rma_id BIGINT NOT NULL,
    inspection_number VARCHAR(40) NOT NULL,
    inspected_by BIGINT NOT NULL,
    inspected_by_name VARCHAR(100) NOT NULL,
    inspected_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reason_detail VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_inspection_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_sales_return_inspection_company_number UNIQUE (company_id, inspection_number),
    CONSTRAINT fk_sales_return_inspection_rma FOREIGN KEY (company_id, rma_id) REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT ck_sales_return_inspection_actor CHECK (length(btrim(inspected_by_name)) > 0)
);

CREATE TABLE public.sales_return_inspection_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    inspection_id BIGINT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
    disposition VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    refund_eligible_quantity INTEGER NOT NULL,
    result_batch_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_inspection_line_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_sales_return_inspection_line_inspection FOREIGN KEY (company_id, inspection_id) REFERENCES public.sales_return_inspection(company_id, id),
    CONSTRAINT fk_sales_return_inspection_line_receipt FOREIGN KEY (company_id, receipt_line_id) REFERENCES public.sales_return_receipt_line(company_id, id),
    CONSTRAINT fk_sales_return_inspection_line_batch FOREIGN KEY (company_id, result_batch_id) REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_sales_return_inspection_line_disposition CHECK (disposition IN ('RESTOCK_AVAILABLE','DAMAGED','QUARANTINE','REJECT_CUSTOMER')),
    CONSTRAINT ck_sales_return_inspection_line_qty CHECK (quantity > 0 AND refund_eligible_quantity BETWEEN 0 AND quantity)
);

CREATE TABLE public.sales_return_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    rma_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_return_event_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_sales_return_event_rma FOREIGN KEY (company_id, rma_id) REFERENCES public.sales_return_rma(company_id, id),
    CONSTRAINT ck_sales_return_event_text CHECK (length(btrim(event_type)) > 0 AND length(btrim(actor_name)) > 0)
);

CREATE INDEX idx_sales_return_rma_order ON public.sales_return_rma(company_id, sales_order_id);
CREATE INDEX idx_sales_return_channel_case_queue ON public.sales_return_channel_case(company_id, status, received_at, id);
CREATE INDEX idx_sales_return_rma_status ON public.sales_return_rma(company_id, business_status, receipt_status, inspection_status, resolution_status);
CREATE INDEX idx_sales_return_rma_line_task ON public.sales_return_rma_line(company_id, outbound_task_id);
CREATE INDEX idx_sales_return_task_queue ON public.sales_return_task(company_id, warehouse_id, status, priority, id DESC);
CREATE INDEX idx_sales_return_receipt_rma ON public.sales_return_receipt(company_id, rma_id, id);
CREATE INDEX idx_sales_return_inspection_rma ON public.sales_return_inspection(company_id, rma_id, id);
CREATE INDEX idx_sales_return_event_rma ON public.sales_return_event(company_id, rma_id, id);

DO $rma_rls$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'sales_return_rma','sales_return_channel_case','sales_return_rma_line','sales_return_task','sales_return_receipt',
        'sales_return_receipt_line','sales_return_inspection','sales_return_inspection_line','sales_return_event'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('CREATE POLICY company_isolation ON public.%I FOR ALL USING (company_id = public.bcwms_current_company_id()) WITH CHECK (company_id = public.bcwms_current_company_id())', table_name);
    END LOOP;
END
$rma_rls$;

CREATE OR REPLACE FUNCTION public.prevent_sales_return_evidence_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN RETURN OLD; END IF;
    RAISE EXCEPTION 'sales return evidence is append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_return_receipt_immutable BEFORE UPDATE OR DELETE ON public.sales_return_receipt
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_return_evidence_mutation();
CREATE TRIGGER trg_sales_return_receipt_line_immutable BEFORE UPDATE OR DELETE ON public.sales_return_receipt_line
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_return_evidence_mutation();
CREATE TRIGGER trg_sales_return_inspection_immutable BEFORE UPDATE OR DELETE ON public.sales_return_inspection
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_return_evidence_mutation();
CREATE TRIGGER trg_sales_return_inspection_line_immutable BEFORE UPDATE OR DELETE ON public.sales_return_inspection_line
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_return_evidence_mutation();
CREATE TRIGGER trg_sales_return_event_immutable BEFORE UPDATE OR DELETE ON public.sales_return_event
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_return_evidence_mutation();

DO $rma_policy_permissions$
DECLARE company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
        INSERT INTO public.system_config (company_id, config_key, config_value, description, config_type, created_at, updated_at)
        VALUES
        (company_record.id, 'sales.rma.approval.mode', 'DIRECT', 'Tenant-selectable RMA approval mode', 'STRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales.rma.standard_return_days', '5', 'Commercial direct-authorisation window; never a statutory hard limit', 'INTEGER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales.rma.refund_only.enabled', 'false', 'Allow linked refund without physical return', 'BOOLEAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales.rma.advance_resolution.enabled', 'false', 'Allow separately permissioned resolution before inspection', 'BOOLEAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales.rma.quarantine_staging.enabled', 'false', 'Use optional Q-zone staging instead of direct final-location receipt', 'BOOLEAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, config_key) DO NOTHING;

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type, resource_path, http_method,
            data_scope, risk_level, custom_assignable, description, status, sort_order, created_at, updated_at
        ) VALUES
        (company_record.id, 'rma:view', '查看销售退货', 'API', '/api/rmas/**', 'GET', 'ALL', 'NORMAL', TRUE, 'View tenant-side RMA business details', 'ACTIVE', 166, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:create', '创建和关闭销售退货', 'API', '/api/rmas;/api/rmas/*/cancel;/api/rmas/*/close-remainder', 'POST', 'ALL', 'HIGH', TRUE, 'Create linked RMA cases and close authorised remainders', 'ACTIVE', 167, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:approve', '审批销售退货', 'API', '/api/rmas/*/approve;/api/rmas/*/reject', 'POST', 'ALL', 'HIGH', TRUE, 'Approve or reject another user RMA', 'ACTIVE', 168, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:receive', '执行退货收货', 'API', '/api/rma-warehouse/tasks/**', '*', 'ALL', 'HIGH', TRUE, 'Receive authorised returns within assigned warehouse scope', 'ACTIVE', 169, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:inspect', '执行退货质检', 'API', '/api/rma-warehouse/tasks/*/inspections', 'POST', 'ALL', 'HIGH', TRUE, 'Inspect return stock and choose quantity dispositions', 'ACTIVE', 170, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:resolve', '处理退款或换货', 'API', '/api/rmas/*/refund-eligibility;/api/rmas/*/resolution;/api/rmas/*/refund-confirmation', 'POST', 'ALL', 'HIGH', TRUE, 'Set refund eligibility, request external refund or create replacement order', 'ACTIVE', 171, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:policy', '配置销售退货策略', 'API', '/api/rmas/policy', '*', 'ALL', 'HIGH', TRUE, 'Configure optional RMA approval and risk features', 'ACTIVE', 172, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'rma:advance-resolution', '提前退款或换货', 'API', '/api/rmas/*/resolution', 'POST', 'ALL', 'HIGH', TRUE, 'Permit resolution before inspection when the tenant switch is enabled', 'ACTIVE', 173, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, permission_code) DO UPDATE SET
            permission_name = EXCLUDED.permission_name, permission_type = EXCLUDED.permission_type,
            resource_path = EXCLUDED.resource_path, http_method = EXCLUDED.http_method,
            data_scope = EXCLUDED.data_scope, risk_level = EXCLUDED.risk_level,
            custom_assignable = EXCLUDED.custom_assignable, description = EXCLUDED.description,
            status = EXCLUDED.status, sort_order = EXCLUDED.sort_order, updated_at = CURRENT_TIMESTAMP;
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$rma_policy_permissions$;

COMMENT ON TABLE public.sales_return_rma IS 'Linked customer sales-return authorisation with independent business, receipt, inspection and resolution states.';
COMMENT ON TABLE public.sales_return_channel_case IS 'Signed channel refund review queue only; it never posts inventory or financial entries.';
COMMENT ON TABLE public.sales_return_receipt IS 'Append-only physical return receipt evidence; actor comes from the authenticated account.';
COMMENT ON TABLE public.sales_return_inspection IS 'Append-only individual/quantity return inspection evidence; no AQL decision.';
COMMENT ON COLUMN public.sales_return_receipt_line.return_batch_id IS 'New RETURN_INSPECTION batch; never the original dispatched inventory row.';
COMMENT ON COLUMN public.sales_orders.order_purpose IS 'EXCHANGE_REPLACEMENT follows normal fulfilment but is excluded from new-sales revenue reporting.';
