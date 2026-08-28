-- V4.76: tenant-admin gated, non-purchase inbound applications.
-- No row in these tables posts inventory directly. Confirmed applications are
-- converted to the unified receiving-task model introduced by V4.74.

CREATE UNIQUE INDEX IF NOT EXISTS uk_other_inbound_parent_company_warehouse
    ON public.warehouses(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_other_inbound_parent_company_sku
    ON public.product_skus(company_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_other_inbound_parent_company_legacy
    ON public.inbound_orders(company_id, id);

CREATE TABLE IF NOT EXISTS public.other_inbound_application (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    application_number VARCHAR(40) NOT NULL,
    business_type VARCHAR(40) NOT NULL,
    source_reference VARCHAR(120) NOT NULL,
    business_reason VARCHAR(500) NOT NULL,
    warehouse_id BIGINT,
    legacy_inbound_order_id BIGINT,
    status VARCHAR(30) NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    confirmed_by BIGINT,
    confirmed_by_name VARCHAR(100),
    confirmed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    terminated_by BIGINT,
    terminated_by_name VARCHAR(100),
    terminated_at TIMESTAMP(6) WITHOUT TIME ZONE,
    termination_reason VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_other_inbound_company_number UNIQUE (company_id, application_number),
    CONSTRAINT uk_other_inbound_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_other_inbound_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_other_inbound_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES public.warehouses(company_id, id),
    CONSTRAINT fk_other_inbound_legacy FOREIGN KEY (company_id, legacy_inbound_order_id)
        REFERENCES public.inbound_orders(company_id, id),
    CONSTRAINT ck_other_inbound_business_type CHECK (business_type IN (
        'FREE_SAMPLE', 'CUSTOMER_SUPPLIED', 'PRODUCTION_COMPLETION',
        'SUBCONTRACT_RETURN', 'AUTHORIZED_SPECIAL'
    )),
    CONSTRAINT ck_other_inbound_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'COMPLETED',
        'CLOSED', 'CANCELLED', 'VOIDED'
    )),
    CONSTRAINT ck_other_inbound_source CHECK (
        length(btrim(source_reference)) > 0
        AND length(btrim(business_reason)) > 0
        AND length(btrim(created_by_name)) > 0
        AND (warehouse_id IS NOT NULL OR legacy_inbound_order_id IS NOT NULL)
    ),
    CONSTRAINT ck_other_inbound_version CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS public.other_inbound_application_item (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    product_sku_id BIGINT NOT NULL,
    planned_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    closed_quantity INTEGER NOT NULL DEFAULT 0,
    production_date DATE,
    expiry_date DATE,
    external_batch_code VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_other_inbound_item_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_other_inbound_item_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_other_inbound_item_application FOREIGN KEY (company_id, application_id)
        REFERENCES public.other_inbound_application(company_id, id),
    CONSTRAINT fk_other_inbound_item_sku FOREIGN KEY (company_id, product_sku_id)
        REFERENCES public.product_skus(company_id, id),
    CONSTRAINT ck_other_inbound_item_quantities CHECK (
        planned_quantity > 0
        AND received_quantity >= 0
        AND closed_quantity >= 0
        AND received_quantity + closed_quantity <= planned_quantity
    ),
    CONSTRAINT ck_other_inbound_item_dates CHECK (
        production_date IS NULL OR expiry_date IS NULL OR expiry_date >= production_date
    )
);

CREATE INDEX IF NOT EXISTS idx_other_inbound_company_status
    ON public.other_inbound_application(company_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_other_inbound_company_warehouse
    ON public.other_inbound_application(company_id, warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_other_inbound_item_sku
    ON public.other_inbound_application_item(company_id, product_sku_id);

ALTER TABLE public.receiving_task
    ADD COLUMN IF NOT EXISTS other_inbound_application_id BIGINT;
ALTER TABLE public.receiving_task_item
    ADD COLUMN IF NOT EXISTS other_inbound_application_item_id BIGINT;

ALTER TABLE public.receiving_task
    ADD CONSTRAINT fk_receiving_task_other_inbound
        FOREIGN KEY (company_id, other_inbound_application_id)
        REFERENCES public.other_inbound_application(company_id, id);
ALTER TABLE public.receiving_task_item
    ADD CONSTRAINT fk_receiving_task_item_other_inbound
        FOREIGN KEY (company_id, other_inbound_application_item_id)
        REFERENCES public.other_inbound_application_item(company_id, id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_receiving_task_company_other_inbound
    ON public.receiving_task(company_id, other_inbound_application_id)
    WHERE other_inbound_application_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_receiving_task_item_other_inbound_item
    ON public.receiving_task_item(
        company_id, receiving_task_id, other_inbound_application_item_id
    )
    WHERE other_inbound_application_item_id IS NOT NULL;

ALTER TABLE public.receiving_task DROP CONSTRAINT IF EXISTS ck_receiving_task_source;
ALTER TABLE public.receiving_task
    ADD CONSTRAINT ck_receiving_task_source CHECK (
        (source_type = 'PURCHASE_ASN'
            AND purchase_asn_id IS NOT NULL
            AND receiving_plan_id IS NOT NULL
            AND other_inbound_application_id IS NULL)
        OR
        (source_type = 'OTHER_INBOUND'
            AND purchase_asn_id IS NULL
            AND receiving_plan_id IS NULL
            AND other_inbound_application_id IS NOT NULL)
    );

ALTER TABLE public.receiving_task_item
    ADD CONSTRAINT ck_receiving_task_item_source CHECK (
        (purchase_asn_item_id IS NOT NULL
            AND purchase_order_item_id IS NOT NULL
            AND other_inbound_application_item_id IS NULL)
        OR
        (purchase_asn_item_id IS NULL
            AND purchase_order_item_id IS NULL
            AND other_inbound_application_item_id IS NOT NULL)
    );

ALTER TABLE public.other_inbound_application ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.other_inbound_application FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS company_isolation ON public.other_inbound_application;
CREATE POLICY company_isolation ON public.other_inbound_application
    FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

ALTER TABLE public.other_inbound_application_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.other_inbound_application_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS company_isolation ON public.other_inbound_application_item;
CREATE POLICY company_isolation ON public.other_inbound_application_item
    FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

-- Preserve legacy inbound history without replaying inventory. Open legacy
-- documents are archived as CLOSED; completed facts retain their actual count.
DO $legacy_other_inbound$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        INSERT INTO public.other_inbound_application (
            company_id, version, application_number, business_type,
            source_reference, business_reason, warehouse_id,
            legacy_inbound_order_id, status, created_by, created_by_name,
            confirmed_by, confirmed_at, terminated_by, terminated_at,
            termination_reason, remark, created_at, updated_at
        )
        SELECT
            source.company_id,
            0,
            'LEG-' || source.id || '-' || COALESCE(source.warehouse_id::TEXT, 'NA'),
            'AUTHORIZED_SPECIAL',
            'LEGACY-INBOUND:' || source.order_no,
            'Migrated read-only legacy inbound history',
            source.warehouse_id,
            source.id,
            CASE source.status
                WHEN 'COMPLETED' THEN 'COMPLETED'
                WHEN 'REJECTED' THEN 'CANCELLED'
                WHEN 'CANCELLED' THEN 'CANCELLED'
                WHEN 'VOIDED' THEN 'VOIDED'
                ELSE 'CLOSED'
            END,
            source.applicant_id,
            COALESCE(NULLIF(btrim(source.applicant_name), ''), 'legacy-unknown'),
            source.confirmed_by,
            source.confirmed_at,
            CASE WHEN source.status = 'COMPLETED' THEN NULL
                 ELSE source.applicant_id END,
            CASE WHEN source.status = 'COMPLETED' THEN NULL
                 ELSE source.updated_at END,
            CASE WHEN source.status = 'COMPLETED' THEN NULL
                 ELSE 'Archived during P0-02 legacy migration; no inventory was replayed' END,
            source.remark,
            source.created_at,
            source.updated_at
        FROM (
            SELECT DISTINCT
                inbound.id, inbound.company_id, inbound.order_no, inbound.status,
                inbound.applicant_id, inbound.applicant_name, inbound.confirmed_by,
                inbound.confirmed_at, inbound.remark, inbound.created_at, inbound.updated_at,
                COALESCE(item.target_warehouse_id, location.warehouse_id) AS warehouse_id
            FROM public.inbound_orders inbound
            LEFT JOIN public.inbound_order_items item
                ON item.company_id = inbound.company_id
               AND item.inbound_order_id = inbound.id
            LEFT JOIN public.locations location
                ON location.company_id = item.company_id
               AND location.id = item.target_location_id
            WHERE inbound.company_id = company_record.id
        ) source
        ON CONFLICT (company_id, application_number) DO NOTHING;

        INSERT INTO public.other_inbound_application_item (
            company_id, application_id, product_sku_id,
            planned_quantity, received_quantity, closed_quantity,
            production_date, expiry_date, external_batch_code, remark,
            created_at, updated_at
        )
        SELECT
            item.company_id,
            application.id,
            item.product_sku_id,
            GREATEST(item.plan_qty, item.actual_qty, 1),
            item.actual_qty,
            GREATEST(item.plan_qty - item.actual_qty, 0),
            item.production_date,
            item.expiry_date,
            item.external_batch_code,
            item.remark,
            item.created_at,
            item.updated_at
        FROM public.inbound_order_items item
        LEFT JOIN public.locations location
            ON location.company_id = item.company_id
           AND location.id = item.target_location_id
        JOIN public.other_inbound_application application
            ON application.company_id = item.company_id
           AND application.legacy_inbound_order_id = item.inbound_order_id
           AND application.warehouse_id IS NOT DISTINCT FROM
               COALESCE(item.target_warehouse_id, location.warehouse_id)
        WHERE item.company_id = company_record.id
          AND NOT EXISTS (
              SELECT 1
              FROM public.other_inbound_application_item migrated
              WHERE migrated.company_id = item.company_id
                AND migrated.application_id = application.id
                AND migrated.product_sku_id = item.product_sku_id
                AND migrated.created_at = item.created_at
          );
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$legacy_other_inbound$;

-- Retire the broad legacy inbound authorities and introduce one explicit,
-- administrator-assigned application authority. Warehouse execution remains
-- governed by receiving:execute and is intentionally not bundled here.
DO $other_inbound_permissions$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        DELETE FROM public.sys_role_permission role_permission
        USING public.sys_permission permission
        WHERE role_permission.company_id = company_record.id
          AND permission.company_id = company_record.id
          AND role_permission.permission_id = permission.id
          AND permission.permission_code IN (
              'inbound:create', 'inbound:approve_plan', 'inbound:reject',
              'inbound:confirm_order', 'inbound:receive_goods'
          );

        UPDATE public.sys_permission
        SET status = 'INACTIVE', custom_assignable = FALSE,
            description = 'Retired by P0-02 unified receiving'
        WHERE company_id = company_record.id
          AND permission_code IN (
              'inbound:create', 'inbound:approve_plan', 'inbound:reject',
              'inbound:confirm_order', 'inbound:receive_goods'
          );

        INSERT INTO public.sys_permission (
            company_id, permission_code, permission_name, permission_type,
            resource_path, http_method, data_scope, risk_level,
            custom_assignable, description, status, sort_order,
            created_at, updated_at
        ) VALUES (
            company_record.id,
            'other_inbound:manage',
            '管理其他入库申请',
            'API',
            '/api/other-inbound-applications/**;/api/other-inbound-policy',
            '*',
            'ALL',
            'HIGH',
            TRUE,
            'Create and govern tenant-admin-enabled other-inbound applications; does not grant warehouse receipt execution',
            'ACTIVE',
            132,
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
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$other_inbound_permissions$;

COMMENT ON TABLE public.other_inbound_application IS
    'Controlled non-purchase inbound application; confirmation creates a unified receiving task and never posts stock directly.';
COMMENT ON COLUMN public.other_inbound_application.legacy_inbound_order_id IS
    'Read-only trace to migrated legacy data; NULL for all new applications.';
