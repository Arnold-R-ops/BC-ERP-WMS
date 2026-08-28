-- V4.74: planned purchase allocation, ASN and unified warehouse receiving.
-- Inventory is never replayed here. Physical stock continues to be changed only
-- by InventoryPostingService after a ReceiptEvent is accepted.

ALTER TABLE public.purchase_order DROP CONSTRAINT IF EXISTS purchase_order_status_check;
DO $purchase_status_migration$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        -- The retired flow created full-quantity inventory batches before the
        -- goods physically arrived. Any batch without entry_date is therefore
        -- a pre-receipt placeholder, not stock. Archive it before an order can
        -- re-enter the new ASN flow so it can never be counted or received a
        -- second time. Physically received legacy batches remain untouched.
        UPDATE public.inventory_batch batch
        SET active = FALSE,
            remark = left(concat_ws(
                ' | ',
                NULLIF(batch.remark, ''),
                'Archived by P0-02: legacy pre-receipt batch; no stock replay'
            ), 500),
            updated_at = CURRENT_TIMESTAMP
        FROM public.purchase_order_item item
        JOIN public.purchase_order purchase
          ON purchase.company_id = item.company_id
         AND purchase.id = item.purchase_order_id
        WHERE batch.company_id = company_record.id
          AND item.company_id = company_record.id
          AND batch.purchase_order_item_id = item.id
          AND batch.entry_date IS NULL
          AND batch.active = TRUE;

        UPDATE public.purchase_order
        SET status = CASE
                WHEN status IN ('ORDERING', 'IN_TRANSIT') THEN 'DRAFT'
                WHEN status = 'PARTIALLY_RECEIVED' THEN 'CLOSED'
                ELSE status
            END,
            audit_log = concat_ws(
                E'\n',
                NULLIF(audit_log, ''),
                CASE
                    WHEN status = 'IN_TRANSIT'
                        THEN '[P0-02 migration] Legacy unreceived shipment returned to draft; staged batches archived'
                    WHEN status = 'PARTIALLY_RECEIVED'
                        THEN '[P0-02 migration] Legacy partial receipt preserved and unreceived remainder closed'
                    ELSE '[P0-02 migration] Legacy ordering draft normalized'
                END
            ),
            updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND status IN ('ORDERING', 'IN_TRANSIT', 'PARTIALLY_RECEIVED');
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$purchase_status_migration$;
ALTER TABLE public.purchase_order
    ADD CONSTRAINT purchase_order_status_check CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'AWAITING_SHIPMENT',
        'PARTIALLY_SHIPPED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'COMPLETED',
        'CLOSED', 'CANCELLED', 'VOIDED'
    ));

-- Composite tenant keys make cross-company references impossible even when a
-- future maintenance script bypasses Hibernate's tenant discriminator.
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_purchase_order_company_id
    ON public.purchase_order(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_purchase_order_item_company_id
    ON public.purchase_order_item(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_warehouse_company_id
    ON public.warehouses(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_sku_company_id
    ON public.product_skus(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_location_company_id
    ON public.locations(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_p002_batch_company_id
    ON public.inventory_batch(company_id, id);

CREATE TABLE public.purchase_order_allocation (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    purchase_order_item_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    planned_quantity INTEGER NOT NULL,
    shipped_quantity INTEGER NOT NULL DEFAULT 0,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    closed_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_allocation_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_purchase_allocation_item_warehouse
        UNIQUE (company_id, purchase_order_item_id, warehouse_id),
    CONSTRAINT fk_purchase_allocation_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_purchase_allocation_item FOREIGN KEY (company_id, purchase_order_item_id)
        REFERENCES public.purchase_order_item(company_id, id),
    CONSTRAINT fk_purchase_allocation_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_purchase_allocation_status CHECK (status IN ('ACTIVE','RECEIVED','CLOSED','RELEASED')),
    CONSTRAINT ck_purchase_allocation_quantities CHECK (
        planned_quantity > 0 AND shipped_quantity >= 0 AND received_quantity >= 0
        AND closed_quantity >= 0 AND shipped_quantity <= planned_quantity
        AND received_quantity <= shipped_quantity
        AND received_quantity + closed_quantity <= planned_quantity
    ),
    CONSTRAINT ck_purchase_allocation_version CHECK (version >= 0)
);

CREATE TABLE public.purchase_asn (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    asn_number VARCHAR(40) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    correction_of_id BIGINT,
    supplier_shipment_no VARCHAR(100),
    carrier VARCHAR(100),
    tracking_no VARCHAR(100),
    expected_arrival_date DATE,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    confirmed_by BIGINT,
    confirmed_by_name VARCHAR(100),
    confirmed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    closed_by BIGINT,
    closed_by_name VARCHAR(100),
    closed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    close_reason VARCHAR(500),
    cancelled_by BIGINT,
    cancelled_by_name VARCHAR(100),
    cancelled_at TIMESTAMP(6) WITHOUT TIME ZONE,
    cancel_reason VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_asn_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_purchase_asn_company_number UNIQUE (company_id, asn_number),
    CONSTRAINT fk_purchase_asn_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_purchase_asn_order FOREIGN KEY (company_id, purchase_order_id)
        REFERENCES public.purchase_order(company_id, id),
    CONSTRAINT fk_purchase_asn_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT fk_purchase_asn_correction FOREIGN KEY (company_id, correction_of_id)
        REFERENCES public.purchase_asn(company_id, id),
    CONSTRAINT ck_purchase_asn_status CHECK (status IN (
        'DRAFT','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED','CORRECTED'
    )),
    CONSTRAINT ck_purchase_asn_actor CHECK (length(btrim(created_by_name)) > 0),
    CONSTRAINT ck_purchase_asn_version CHECK (version >= 0)
);

CREATE TABLE public.purchase_asn_item (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    purchase_asn_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    purchase_order_allocation_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    shipped_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    closed_quantity INTEGER NOT NULL DEFAULT 0,
    production_date DATE,
    expiry_date DATE,
    external_batch_code VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_asn_item_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_purchase_asn_item_order_item
        UNIQUE (company_id, purchase_asn_id, purchase_order_item_id),
    CONSTRAINT fk_purchase_asn_item_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_purchase_asn_item_asn FOREIGN KEY (company_id, purchase_asn_id)
        REFERENCES public.purchase_asn(company_id, id),
    CONSTRAINT fk_purchase_asn_item_order_item FOREIGN KEY (company_id, purchase_order_item_id)
        REFERENCES public.purchase_order_item(company_id, id),
    CONSTRAINT fk_purchase_asn_item_allocation FOREIGN KEY (company_id, purchase_order_allocation_id)
        REFERENCES public.purchase_order_allocation(company_id, id),
    CONSTRAINT fk_purchase_asn_item_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT ck_purchase_asn_item_quantities CHECK (
        shipped_quantity > 0 AND received_quantity >= 0 AND closed_quantity >= 0
        AND received_quantity + closed_quantity <= shipped_quantity
    ),
    CONSTRAINT ck_purchase_asn_item_dates CHECK (
        production_date IS NULL OR expiry_date IS NULL OR expiry_date >= production_date
    )
);

CREATE TABLE public.receiving_plan (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    plan_number VARCHAR(40) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    expected_window_start TIMESTAMP(6) WITHOUT TIME ZONE,
    expected_window_end TIMESTAMP(6) WITHOUT TIME ZONE,
    assigned_receiver_id BIGINT,
    assigned_receiver_name VARCHAR(100),
    assigned_team VARCHAR(100),
    dock_area VARCHAR(100),
    work_area VARCHAR(100),
    warehouse_notes VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_plan_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_plan_company_number UNIQUE (company_id, plan_number),
    CONSTRAINT uk_receiving_plan_company_order_warehouse
        UNIQUE (company_id, purchase_order_id, warehouse_id),
    CONSTRAINT fk_receiving_plan_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_plan_order FOREIGN KEY (company_id, purchase_order_id)
        REFERENCES public.purchase_order(company_id, id),
    CONSTRAINT fk_receiving_plan_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_receiving_plan_status CHECK (status IN (
        'WAITING_SHIPMENT','IN_TRANSIT','PARTIALLY_RECEIVED','COMPLETED','CLOSED'
    )),
    CONSTRAINT ck_receiving_plan_window CHECK (
        expected_window_start IS NULL OR expected_window_end IS NULL
        OR expected_window_end >= expected_window_start
    ),
    CONSTRAINT ck_receiving_plan_priority CHECK (priority >= 0),
    CONSTRAINT ck_receiving_plan_version CHECK (version >= 0)
);

CREATE TABLE public.receiving_task (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    task_number VARCHAR(40) NOT NULL,
    receiving_plan_id BIGINT,
    source_type VARCHAR(30) NOT NULL,
    purchase_asn_id BIGINT,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    closed_by BIGINT,
    closed_by_name VARCHAR(100),
    closed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    close_reason VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_task_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_task_company_number UNIQUE (company_id, task_number),
    CONSTRAINT uk_receiving_task_company_asn UNIQUE (company_id, purchase_asn_id),
    CONSTRAINT fk_receiving_task_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_task_plan FOREIGN KEY (company_id, receiving_plan_id)
        REFERENCES public.receiving_plan(company_id, id),
    CONSTRAINT fk_receiving_task_asn FOREIGN KEY (company_id, purchase_asn_id)
        REFERENCES public.purchase_asn(company_id, id),
    CONSTRAINT fk_receiving_task_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT ck_receiving_task_source CHECK (
        (source_type = 'PURCHASE_ASN' AND purchase_asn_id IS NOT NULL AND receiving_plan_id IS NOT NULL)
        OR (source_type = 'OTHER_INBOUND' AND purchase_asn_id IS NULL AND receiving_plan_id IS NULL)
    ),
    CONSTRAINT ck_receiving_task_status CHECK (status IN (
        'PENDING','PARTIALLY_RECEIVED','COMPLETED','CLOSED','CANCELLED'
    )),
    CONSTRAINT ck_receiving_task_version CHECK (version >= 0)
);

CREATE TABLE public.receiving_task_item (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    receiving_task_id BIGINT NOT NULL,
    purchase_asn_item_id BIGINT,
    purchase_order_item_id BIGINT,
    product_sku_id BIGINT NOT NULL,
    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    closed_quantity INTEGER NOT NULL DEFAULT 0,
    production_date DATE,
    expiry_date DATE,
    external_batch_code VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_task_item_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receiving_task_item_asn_item
        UNIQUE (company_id, receiving_task_id, purchase_asn_item_id),
    CONSTRAINT fk_receiving_task_item_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_receiving_task_item_task FOREIGN KEY (company_id, receiving_task_id)
        REFERENCES public.receiving_task(company_id, id),
    CONSTRAINT fk_receiving_task_item_asn_item FOREIGN KEY (company_id, purchase_asn_item_id)
        REFERENCES public.purchase_asn_item(company_id, id),
    CONSTRAINT fk_receiving_task_item_order_item FOREIGN KEY (company_id, purchase_order_item_id)
        REFERENCES public.purchase_order_item(company_id, id),
    CONSTRAINT fk_receiving_task_item_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT ck_receiving_task_item_quantities CHECK (
        expected_quantity > 0 AND received_quantity >= 0 AND closed_quantity >= 0
        AND received_quantity + closed_quantity <= expected_quantity
    )
);

CREATE TABLE public.receipt_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    event_number VARCHAR(50) NOT NULL,
    receiving_task_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    received_by BIGINT NOT NULL,
    received_by_name VARCHAR(100) NOT NULL,
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receipt_event_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receipt_event_company_number UNIQUE (company_id, event_number),
    CONSTRAINT uk_receipt_event_company_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_receipt_event_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_receipt_event_task FOREIGN KEY (company_id, receiving_task_id)
        REFERENCES public.receiving_task(company_id, id),
    CONSTRAINT ck_receipt_event_status CHECK (status = 'APPLIED'),
    CONSTRAINT ck_receipt_event_actor CHECK (length(btrim(received_by_name)) > 0)
);

CREATE TABLE public.receipt_event_item (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    receipt_event_id BIGINT NOT NULL,
    receiving_task_item_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    batch_code VARCHAR(50) NOT NULL,
    production_date DATE,
    expiry_date DATE,
    external_batch_code VARCHAR(100),
    posting_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receipt_event_item_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_receipt_event_item_posting UNIQUE (company_id, posting_id),
    CONSTRAINT fk_receipt_event_item_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_receipt_event_item_event FOREIGN KEY (company_id, receipt_event_id)
        REFERENCES public.receipt_event(company_id, id),
    CONSTRAINT fk_receipt_event_item_task_item FOREIGN KEY (company_id, receiving_task_item_id)
        REFERENCES public.receiving_task_item(company_id, id),
    CONSTRAINT fk_receipt_event_item_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT fk_receipt_event_item_batch FOREIGN KEY (company_id, inventory_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_receipt_event_item_location FOREIGN KEY (company_id, location_id)
        REFERENCES public.locations(company_id, id),
    CONSTRAINT ck_receipt_event_item_quantity CHECK (quantity > 0)
);

CREATE TABLE public.receiving_legacy_map (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    legacy_type VARCHAR(40) NOT NULL,
    legacy_id BIGINT NOT NULL,
    target_type VARCHAR(40),
    target_id BIGINT,
    migration_status VARCHAR(30) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receiving_legacy_map_source UNIQUE (company_id, legacy_type, legacy_id),
    CONSTRAINT fk_receiving_legacy_map_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT ck_receiving_legacy_map_status CHECK (migration_status IN (
        'HISTORY_ONLY','MAPPED','REVIEW_REQUIRED'
    ))
);

DO $purchase_legacy_history$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
        INSERT INTO public.receiving_legacy_map (
            company_id, legacy_type, legacy_id, migration_status, detail
        )
        SELECT batch.company_id, 'LEGACY_PURCHASE_BATCH', batch.id, 'HISTORY_ONLY',
               'Pre-V4.74 inventory fact retained; no ReceiptEvent or stock posting was replayed'
        FROM public.inventory_batch batch
        WHERE batch.company_id = company_record.id
          AND batch.purchase_order_item_id IS NOT NULL
        ON CONFLICT (company_id, legacy_type, legacy_id) DO NOTHING;
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$purchase_legacy_history$;

CREATE INDEX idx_purchase_allocation_order_item_status
    ON public.purchase_order_allocation(company_id, purchase_order_item_id, status);
CREATE INDEX idx_purchase_asn_order_status
    ON public.purchase_asn(company_id, purchase_order_id, status);
CREATE INDEX idx_purchase_asn_warehouse_status
    ON public.purchase_asn(company_id, warehouse_id, status);
CREATE INDEX idx_purchase_asn_item_sku
    ON public.purchase_asn_item(company_id, product_sku_id);
CREATE INDEX idx_receiving_plan_status
    ON public.receiving_plan(company_id, status);
CREATE INDEX idx_receiving_task_warehouse_status
    ON public.receiving_task(company_id, warehouse_id, status);
CREATE INDEX idx_receiving_task_plan
    ON public.receiving_task(company_id, receiving_plan_id);
CREATE INDEX idx_receiving_task_item_sku
    ON public.receiving_task_item(company_id, product_sku_id);
CREATE INDEX idx_receipt_event_task_time
    ON public.receipt_event(company_id, receiving_task_id, received_at);
CREATE INDEX idx_receipt_event_item_event
    ON public.receipt_event_item(company_id, receipt_event_id);
CREATE INDEX idx_receipt_event_item_task_item
    ON public.receipt_event_item(company_id, receiving_task_item_id);

DO $rls$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'purchase_order_allocation','purchase_asn','purchase_asn_item','receiving_plan',
        'receiving_task','receiving_task_item','receipt_event','receipt_event_item',
        'receiving_legacy_map'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS company_isolation ON public.%I', table_name);
        EXECUTE format(
            'CREATE POLICY company_isolation ON public.%I FOR ALL USING (company_id = public.bcwms_current_company_id()) WITH CHECK (company_id = public.bcwms_current_company_id())',
            table_name
        );
    END LOOP;
END
$rls$;

DO $receiving_permissions$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level, custom_assignable,
            description, status, sort_order, created_at, updated_at
        )
        SELECT company_record.id, seed.code, seed.name, 'API', seed.path, seed.method,
               'ALL', seed.risk, TRUE, seed.description, 'ACTIVE', seed.sort_order,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM (VALUES
            ('purchase:submit','提交采购订单','/api/purchase-orders/*/submit;/api/purchase-orders/*/withdraw','POST','NORMAL',143,'Submit or withdraw a fully allocated draft under the optional approval policy'),
            ('purchase:approve','审批采购订单','/api/purchase-orders/*/approve;/api/purchase-orders/*/reject','POST','HIGH',144,'Approve or reject a purchase order only when optional approval is enabled'),
            ('purchase:cancel','取消采购订单','/api/purchase-orders/*/cancel','POST','HIGH',145,'Business-cancel a purchase order that has no physical receipt'),
            ('purchase:void','作废采购订单','/api/purchase-orders/*/void','POST','CRITICAL',146,'Void erroneous purchase data only when no downstream record exists'),
            ('purchase:allocation:view','查看采购分仓','/api/purchase-orders/*/allocations','GET','NORMAL',146,'View pre-submission warehouse allocation'),
            ('purchase:allocation:edit','维护采购分仓','/api/purchase-orders/*/allocations','PUT','HIGH',147,'Maintain complete per-line warehouse allocation'),
            ('purchase:asn:view','查看采购ASN','/api/purchase-asns/**;/api/purchase-orders/*/asns','GET','NORMAL',148,'View procurement ASN details and order ASN list'),
            ('purchase:asn:create','创建采购ASN','/api/purchase-orders/*/asns;/api/purchase-asns/*/correct','POST','NORMAL',149,'Create or correct a purchase ASN'),
            ('purchase:asn:update','修改采购ASN草稿','/api/purchase-asns/*','PUT','NORMAL',149,'Replace an unreceived ASN draft with optimistic locking'),
            ('purchase:asn:confirm','确认供应商发货','/api/purchase-asns/*/confirm;/api/purchase-orders/*/asns/quick-confirm','POST','HIGH',150,'Confirm immutable ASN or use the transactional quick-confirm flow'),
            ('purchase:asn:cancel','取消采购ASN','/api/purchase-asns/*/cancel','POST','HIGH',151,'Cancel an unreceived ASN'),
            ('purchase:asn:close','关闭ASN余量','/api/purchase-asns/*/close','POST','HIGH',152,'Close an approved or directly governed remainder'),
            ('receiving:view','查看收货任务','/api/receiving-tasks/**;/api/receiving-plans;/api/purchase-orders/*/receiving-plans','GET','NORMAL',153,'View warehouse-safe receiving tasks and receiving plans'),
            ('receiving:execute','执行收货','/api/receiving-tasks/*/receipts','POST','HIGH',154,'Post an idempotent physical receipt'),
            ('receiving:plan:edit','维护收货计划','/api/receiving-plans/*/operations','PUT','NORMAL',156,'Maintain warehouse receiving operations fields')
        ) AS seed(code,name,path,method,risk,sort_order,description)
        ON CONFLICT (company_id, permission_code) DO UPDATE
        SET permission_name = EXCLUDED.permission_name,
            resource_path = EXCLUDED.resource_path,
            http_method = EXCLUDED.http_method,
            risk_level = EXCLUDED.risk_level,
            description = EXCLUDED.description,
            status = 'ACTIVE',
            updated_at = CURRENT_TIMESTAMP;

        UPDATE public.sys_permission
        SET resource_path = '/api/purchase-orders/**;/api/purchase-governance-policy',
            http_method = 'GET',
            updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND permission_code = 'purchase:view';

        UPDATE public.sys_permission
        SET status = 'INACTIVE', resource_path = NULL, http_method = NULL,
            custom_assignable = FALSE,
            description = 'Retired by unified ASN and receiving workflow in V4.74',
            updated_at = CURRENT_TIMESTAMP
        WHERE company_id = company_record.id
          AND permission_code IN ('purchase:confirm','purchase:receive','purchase:rollback');

        INSERT INTO public.sys_role_permission (
            company_id, role_id, permission_id, granted_by, granted_at
        )
        SELECT company_record.id, role.id, permission.id, NULL, CURRENT_TIMESTAMP
        FROM public.sys_role role
        JOIN public.sys_permission permission
          ON permission.company_id = company_record.id
        WHERE role.company_id = company_record.id
          AND (
            (role.role_code IN ('TENANT_ADMIN','WAREHOUSE_ADMIN')
             AND permission.permission_code LIKE 'receiving:%')
            OR (role.role_code = 'WAREHOUSE_STAFF'
                AND permission.permission_code IN ('receiving:view','receiving:execute'))
            OR (role.role_code IN ('TENANT_ADMIN','GENERAL_MANAGER')
                AND permission.permission_code LIKE 'purchase:%')
            OR (role.role_code = 'BUYER'
                AND permission.permission_code IN (
                    'purchase:submit','purchase:cancel',
                    'purchase:allocation:view','purchase:allocation:edit',
                    'purchase:asn:view','purchase:asn:create','purchase:asn:update','purchase:asn:confirm',
                    'purchase:asn:cancel','purchase:asn:close','receiving:view'
                ))
          )
        ON CONFLICT (company_id, role_id, permission_id) DO NOTHING;

        UPDATE public.users account
        SET security_version = COALESCE(account.security_version, 0) + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE account.company_id = company_record.id
          AND EXISTS (
              SELECT 1
              FROM public.sys_user_role user_role
              JOIN public.sys_role role
                ON role.company_id = user_role.company_id
               AND role.id = user_role.role_id
              WHERE user_role.company_id = company_record.id
                AND user_role.user_id = account.id
                AND role.role_code IN (
                    'TENANT_ADMIN','GENERAL_MANAGER','BUYER',
                    'WAREHOUSE_ADMIN','WAREHOUSE_STAFF'
                )
          );
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$receiving_permissions$;

COMMENT ON TABLE public.receiving_legacy_map IS
    'Audit-only bridge for legacy inbound facts. A mapping row never posts inventory.';
