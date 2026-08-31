-- P0-04A: remove the legacy mixed sales status and establish independent
-- commercial, allocation, warehouse and logistics truth sources.

ALTER TABLE public.sales_orders
    RENAME COLUMN fulfillment_version TO status_version;

ALTER TABLE public.sales_orders
    ADD COLUMN allocation_status VARCHAR(40),
    ADD COLUMN warehouse_status VARCHAR(40),
    ADD COLUMN logistics_status VARCHAR(40),
    ADD COLUMN approval_mode_snapshot VARCHAR(30),
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_changed_by BIGINT,
    ADD COLUMN status_changed_by_name VARCHAR(100),
    ADD COLUMN status_changed_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE public.sales_orders sales_order
SET commercial_status = CASE sales_order.status
        WHEN 'DRAFT' THEN 'DRAFT'
        WHEN 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
        WHEN 'APPROVED_AWAITING_SHIPMENT' THEN 'APPROVED'
        WHEN 'SHIPPED' THEN 'COMPLETED'
        WHEN 'REJECTED' THEN 'REJECTED'
        WHEN 'CANCELLED' THEN 'CANCELLED'
        WHEN 'VOIDED' THEN 'VOIDED'
        ELSE commercial_status
    END,
    allocation_status = CASE
        WHEN sales_order.status = 'SHIPPED' THEN 'FULLY_ALLOCATED'
        WHEN sales_order.status IN ('DRAFT','PENDING_APPROVAL','REJECTED','CANCELLED','VOIDED')
            THEN 'UNALLOCATED'
        WHEN sales_order.fulfillment_status = 'RESERVED' THEN 'FULLY_ALLOCATED'
        WHEN sales_order.fulfillment_status = 'PARTIALLY_ALLOCATED' THEN 'PARTIALLY_ALLOCATED'
        WHEN sales_order.fulfillment_status IN ('WAITING_INBOUND','BACKORDERED') THEN 'BACKORDERED'
        WHEN sales_order.fulfillment_status IN ('PARTIALLY_SHIPPED','SHIPPED') THEN 'FULLY_ALLOCATED'
        ELSE 'UNALLOCATED'
    END,
    warehouse_status = CASE
        WHEN EXISTS (
            SELECT 1 FROM public.outbound_tasks task
            WHERE task.company_id = sales_order.company_id
              AND task.sales_order_id = sales_order.id
              AND task.status = 'QUALITY_EXCEPTION'
        ) THEN 'EXCEPTION'
        WHEN sales_order.status = 'SHIPPED' THEN 'SHIPPED'
        WHEN EXISTS (
            SELECT 1 FROM public.outbound_tasks task
            WHERE task.company_id = sales_order.company_id
              AND task.sales_order_id = sales_order.id
              AND task.status = 'COMPLETED'
        ) THEN 'PARTIALLY_SHIPPED'
        WHEN EXISTS (
            SELECT 1 FROM public.outbound_tasks task
            WHERE task.company_id = sales_order.company_id
              AND task.sales_order_id = sales_order.id
              AND task.status = 'PICKING'
        ) THEN 'PICKING'
        WHEN EXISTS (
            SELECT 1 FROM public.outbound_tasks task
            WHERE task.company_id = sales_order.company_id
              AND task.sales_order_id = sales_order.id
              AND task.status = 'PENDING'
        ) THEN 'PENDING_PICK'
        ELSE 'NOT_RELEASED'
    END,
    logistics_status = CASE
        WHEN EXISTS (
            SELECT 1 FROM public.sales_order_shipments shipment
            WHERE shipment.company_id = sales_order.company_id
              AND shipment.sales_order_id = sales_order.id
              AND shipment.status = 'ACTIVE'
              AND shipment.shipped_at IS NOT NULL
        ) THEN 'HANDED_TO_CARRIER'
        WHEN EXISTS (
            SELECT 1 FROM public.sales_order_shipments shipment
            WHERE shipment.company_id = sales_order.company_id
              AND shipment.sales_order_id = sales_order.id
              AND shipment.status = 'ACTIVE'
        ) THEN 'LABEL_CREATED'
        ELSE 'NOT_CREATED'
    END,
    approval_mode_snapshot = 'RISK_BASED',
    status_changed_at = COALESCE(sales_order.updated_at, CURRENT_TIMESTAMP);

ALTER TABLE public.sales_orders
    ALTER COLUMN allocation_status SET DEFAULT 'UNALLOCATED',
    ALTER COLUMN allocation_status SET NOT NULL,
    ALTER COLUMN warehouse_status SET DEFAULT 'NOT_RELEASED',
    ALTER COLUMN warehouse_status SET NOT NULL,
    ALTER COLUMN logistics_status SET DEFAULT 'NOT_CREATED',
    ALTER COLUMN logistics_status SET NOT NULL,
    ALTER COLUMN approval_mode_snapshot SET DEFAULT 'DIRECT',
    ALTER COLUMN approval_mode_snapshot SET NOT NULL;

ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS sales_orders_status_check;
ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS ck_sales_orders_commercial_status;
ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS ck_sales_orders_allocation_status;
ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS ck_sales_orders_warehouse_status;
ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS ck_sales_orders_logistics_status;
ALTER TABLE public.sales_orders DROP CONSTRAINT IF EXISTS ck_sales_orders_approval_mode;
ALTER TABLE public.sales_orders
    ADD CONSTRAINT ck_sales_orders_commercial_status CHECK (
        commercial_status IN ('DRAFT','PENDING_APPROVAL','APPROVED','COMPLETED','CLOSED',
            'REJECTED','CANCELLED','VOIDED')
    ),
    ADD CONSTRAINT ck_sales_orders_allocation_status CHECK (
        allocation_status IN ('UNALLOCATED','FULLY_ALLOCATED','PARTIALLY_ALLOCATED',
            'BACKORDERED','WAITING_INBOUND')
    ),
    ADD CONSTRAINT ck_sales_orders_warehouse_status CHECK (
        warehouse_status IN ('NOT_RELEASED','PENDING_PICK','PICKING','PICKED',
            'PENDING_CHECK','PACKING','READY_TO_HANDOVER','PARTIALLY_SHIPPED','SHIPPED','EXCEPTION')
    ),
    ADD CONSTRAINT ck_sales_orders_logistics_status CHECK (
        logistics_status IN ('NOT_CREATED','LABEL_CREATED','HANDED_TO_CARRIER',
            'IN_TRANSIT','DELIVERED','EXCEPTION','RETURNED')
    ),
    ADD CONSTRAINT ck_sales_orders_approval_mode CHECK (
        approval_mode_snapshot IN ('DIRECT','RISK_BASED','ALWAYS')
    ),
    ADD CONSTRAINT ck_sales_orders_status_version CHECK (status_version >= 0);

DROP INDEX IF EXISTS public.idx_sales_status;
DROP INDEX IF EXISTS public.idx_sales_orders_company_status;
DROP INDEX IF EXISTS public.idx_sales_orders_fulfillment_status;
DROP INDEX IF EXISTS public.idx_sales_order_items_fulfillment_status;
CREATE INDEX idx_sales_orders_company_commercial
    ON public.sales_orders(company_id, commercial_status);
CREATE INDEX idx_sales_orders_company_allocation
    ON public.sales_orders(company_id, allocation_status);
CREATE INDEX idx_sales_orders_company_warehouse
    ON public.sales_orders(company_id, warehouse_status);

ALTER TABLE public.sales_orders DROP COLUMN status;
ALTER TABLE public.sales_orders DROP COLUMN fulfillment_status;
ALTER TABLE public.sales_order_items DROP COLUMN fulfillment_status;

ALTER TABLE public.sales_order_shipments
    ADD COLUMN logistics_status VARCHAR(30),
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_changed_by BIGINT,
    ADD COLUMN status_changed_by_name VARCHAR(100),
    ADD COLUMN status_changed_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE public.sales_order_shipments
SET logistics_status = CASE WHEN shipped_at IS NULL THEN 'LABEL_CREATED' ELSE 'HANDED_TO_CARRIER' END,
    status_changed_by = created_by,
    status_changed_by_name = created_by_name,
    status_changed_at = COALESCE(shipped_at, created_at);

ALTER TABLE public.sales_order_shipments
    ALTER COLUMN logistics_status SET DEFAULT 'LABEL_CREATED',
    ALTER COLUMN logistics_status SET NOT NULL,
    ADD CONSTRAINT uk_sales_order_shipments_company_id UNIQUE (company_id, id),
    ADD CONSTRAINT ck_sales_order_shipments_logistics_status CHECK (
        logistics_status IN ('LABEL_CREATED','HANDED_TO_CARRIER','IN_TRANSIT',
            'DELIVERED','EXCEPTION','RETURNED')
    );

CREATE TABLE public.sales_shipment_logistics_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL,
    shipment_id BIGINT NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    changed_by BIGINT,
    changed_by_name VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_logistics_events_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_sales_logistics_events_shipment FOREIGN KEY (company_id, shipment_id)
        REFERENCES public.sales_order_shipments(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_sales_logistics_events_from CHECK (
        from_status IS NULL OR from_status IN ('NOT_CREATED','LABEL_CREATED','HANDED_TO_CARRIER',
            'IN_TRANSIT','DELIVERED','EXCEPTION','RETURNED')
    ),
    CONSTRAINT ck_sales_logistics_events_to CHECK (
        to_status IN ('LABEL_CREATED','HANDED_TO_CARRIER','IN_TRANSIT',
            'DELIVERED','EXCEPTION','RETURNED')
    ),
    CONSTRAINT ck_sales_logistics_events_source CHECK (source IN ('BASIC','MANUAL','MIGRATION'))
);

INSERT INTO public.sales_shipment_logistics_events (
    company_id, sales_order_id, shipment_id, from_status, to_status, source,
    reason, changed_by, changed_by_name, changed_at, created_at, updated_at
)
SELECT company_id, sales_order_id, id, 'NOT_CREATED', logistics_status, 'MIGRATION',
       'V4.82 baseline from existing shipment evidence', created_by,
       COALESCE(NULLIF(btrim(created_by_name), ''), 'MIGRATION_V4_82'),
       status_changed_at, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.sales_order_shipments;

CREATE INDEX idx_sales_logistics_events_shipment
    ON public.sales_shipment_logistics_events(company_id, shipment_id, id);
ALTER TABLE public.sales_shipment_logistics_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales_shipment_logistics_events FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.sales_shipment_logistics_events
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

CREATE OR REPLACE FUNCTION public.prevent_sales_logistics_event_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'sales shipment logistics events are append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_logistics_events_immutable
BEFORE UPDATE OR DELETE ON public.sales_shipment_logistics_events
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_logistics_event_mutation();

CREATE TABLE public.sales_command_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    response_body TEXT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_command_events_company_key UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_sales_command_events_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT ck_sales_command_events_text CHECK (
        length(btrim(idempotency_key)) > 0
        AND length(btrim(operation)) > 0
        AND length(btrim(actor_name)) > 0
    )
);

CREATE INDEX idx_sales_command_events_company_operation
    ON public.sales_command_events(company_id, operation, created_at DESC);

ALTER TABLE public.sales_command_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales_command_events FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.sales_command_events
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

CREATE OR REPLACE FUNCTION public.prevent_sales_command_event_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'sales command events are append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_command_events_immutable
BEFORE UPDATE OR DELETE ON public.sales_command_events
FOR EACH ROW EXECUTE FUNCTION public.prevent_sales_command_event_mutation();

DO $sales_policy_and_permissions$
DECLARE company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
        INSERT INTO public.system_config (
            company_id, config_key, config_value, description, config_type, created_at, updated_at
        ) VALUES
        (company_record.id, 'sales.order.approval.mode', 'DIRECT',
            'Tenant-selectable sales approval mode; ordinary tenants default to direct submission',
            'STRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales.logistics.detailed.enabled', 'false',
            'Optional detailed manual logistics lifecycle; basic carrier and tracking data remain available',
            'BOOLEAN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (company_id, config_key) DO NOTHING;

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES
        (company_record.id, 'sales:close', '关闭销售订单剩余数量', 'API', '/api/sales-orders/*/close', 'POST', 'ALL', 'HIGH', TRUE, 'Close only the unshipped remainder of a partially dispatched order', 'ACTIVE', 138, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales:logistics:update', '更新销售物流状态', 'API', '/api/sales-orders/*/shipments/*/logistics-status', 'POST', 'ALL', 'HIGH', TRUE, 'Record audited manual logistics events when the tenant feature is enabled', 'ACTIVE', 139, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'sales:policy:manage', '配置销售订单策略', 'API', '/api/sales-orders/policy', 'PUT', 'ALL', 'HIGH', TRUE, 'Configure optional sales review and detailed logistics behavior', 'ACTIVE', 140, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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

        UPDATE public.sys_permission
        SET permission_name = '确认销售出库发货',
            resource_path = '/api/outbound-tasks/*/dispatch;/api/outbound-tasks/batch-dispatch',
            description = 'Confirm physical dispatch, consume reservations and deduct shipped inventory atomically',
            updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND permission_code = 'outbound:pick';
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$sales_policy_and_permissions$;

COMMENT ON COLUMN public.sales_orders.commercial_status IS 'Business acceptance only; never warehouse or carrier progress.';
COMMENT ON COLUMN public.sales_orders.allocation_status IS 'Derived inventory allocation and shortage summary.';
COMMENT ON COLUMN public.sales_orders.warehouse_status IS 'Derived outbound warehouse execution summary.';
COMMENT ON COLUMN public.sales_orders.logistics_status IS 'Derived active-shipment carrier progress.';
COMMENT ON TABLE public.sales_command_events IS 'Append-only atomic replay evidence for company-scoped sales commands.';
COMMENT ON TABLE public.sales_shipment_logistics_events IS 'Append-only shipment logistics timeline retained even when detailed logistics is disabled.';
