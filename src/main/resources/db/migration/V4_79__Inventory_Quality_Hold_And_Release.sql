-- P0-03B: governed manual quality holds, recall holds and release-to-saleable.
-- Physical on-hand totals do not change: partial actions split an inventory
-- batch into linked quality segments; full actions only change quality state.

ALTER TABLE public.inventory_batch
    ADD COLUMN IF NOT EXISTS quality_parent_batch_id BIGINT,
    ADD COLUMN IF NOT EXISTS quality_segment_no VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uk_inventory_batch_company_quality_segment
    ON public.inventory_batch(company_id, quality_segment_no)
    WHERE quality_segment_no IS NOT NULL;

ALTER TABLE public.inventory_batch
    ADD CONSTRAINT fk_inventory_batch_quality_parent
        FOREIGN KEY (company_id, quality_parent_batch_id)
        REFERENCES public.inventory_batch(company_id, id);

CREATE TABLE public.inventory_quality_action (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    action_number VARCHAR(40) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    hold_type VARCHAR(20),
    status VARCHAR(30) NOT NULL,
    release_mode_snapshot VARCHAR(30),
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(500),
    recall_notice_number VARCHAR(120),
    external_reference VARCHAR(120),
    requested_by BIGINT NOT NULL,
    requested_by_name VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_quality_action_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_inventory_quality_action_company_number UNIQUE (company_id, action_number),
    CONSTRAINT uk_inventory_quality_action_company_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_inventory_quality_action_company FOREIGN KEY (company_id) REFERENCES public.tenants(id),
    CONSTRAINT ck_inventory_quality_action_version CHECK (version >= 0),
    CONSTRAINT ck_inventory_quality_action_type CHECK (action_type IN ('HOLD', 'RELEASE')),
    CONSTRAINT ck_inventory_quality_action_hold_type CHECK (hold_type IS NULL OR hold_type IN ('QUARANTINE', 'RECALL')),
    CONSTRAINT ck_inventory_quality_action_status CHECK (status IN ('COMPLETED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_inventory_quality_action_release_mode CHECK (release_mode_snapshot IS NULL OR release_mode_snapshot IN ('DIRECT_WITH_REASON', 'APPROVAL_REQUIRED')),
    CONSTRAINT ck_inventory_quality_action_shape CHECK (
        (action_type = 'HOLD' AND hold_type IS NOT NULL AND release_mode_snapshot IS NULL AND status = 'COMPLETED')
        OR
        (action_type = 'RELEASE' AND hold_type IS NULL AND release_mode_snapshot IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_quality_action_text CHECK (
        length(btrim(action_number)) > 0
        AND length(btrim(reason_code)) > 0
        AND length(btrim(requested_by_name)) > 0
        AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_inventory_quality_action_review CHECK (
        (status IN ('COMPLETED', 'PENDING_REVIEW') AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED', 'WITHDRAWN') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND length(btrim(review_comment)) > 0)
    ),
    CONSTRAINT ck_inventory_quality_action_separation CHECK (
        status IN ('COMPLETED', 'PENDING_REVIEW', 'WITHDRAWN') OR reviewed_by <> requested_by
    )
);

CREATE TABLE public.inventory_quality_action_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    action_id BIGINT NOT NULL,
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
    location_code VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    reallocated_reserved_quantity INTEGER NOT NULL DEFAULT 0,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_quality_action_line_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_inventory_quality_action_line_action FOREIGN KEY (company_id, action_id) REFERENCES public.inventory_quality_action(company_id, id),
    CONSTRAINT fk_inventory_quality_action_line_source FOREIGN KEY (company_id, source_batch_id) REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT fk_inventory_quality_action_line_result FOREIGN KEY (company_id, result_batch_id) REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_inventory_quality_action_line_quantity CHECK (quantity > 0 AND reallocated_reserved_quantity >= 0 AND reallocated_reserved_quantity <= quantity),
    CONSTRAINT ck_inventory_quality_action_line_status CHECK (
        from_status IN ('AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED','EXPIRED','RECALL_HOLD','RETURN_INSPECTION')
        AND to_status IN ('AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED','EXPIRED','RECALL_HOLD','RETURN_INSPECTION')
        AND from_status <> to_status
    ),
    CONSTRAINT ck_inventory_quality_action_line_text CHECK (
        length(btrim(product_sku_code)) > 0 AND length(btrim(product_name)) > 0
        AND length(btrim(source_batch_code)) > 0 AND length(btrim(warehouse_name)) > 0
        AND length(btrim(location_code)) > 0
    )
);

CREATE TABLE public.inventory_quality_reservation_move (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    action_line_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    outbound_task_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    from_batch_id BIGINT NOT NULL,
    to_batch_id BIGINT NOT NULL,
    from_location_id BIGINT NOT NULL,
    to_location_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_quality_reservation_move_line FOREIGN KEY (company_id, action_line_id) REFERENCES public.inventory_quality_action_line(company_id, id),
    CONSTRAINT ck_inventory_quality_reservation_move_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_quality_action_status_created
    ON public.inventory_quality_action(company_id, status, created_at DESC);
CREATE INDEX idx_inventory_quality_action_type_created
    ON public.inventory_quality_action(company_id, action_type, created_at DESC);
CREATE INDEX idx_inventory_quality_line_source
    ON public.inventory_quality_action_line(company_id, source_batch_id);
CREATE INDEX idx_inventory_quality_line_result
    ON public.inventory_quality_action_line(company_id, result_batch_id);
CREATE INDEX idx_inventory_quality_move_order
    ON public.inventory_quality_reservation_move(company_id, sales_order_id);

ALTER TABLE public.inventory_quality_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_quality_action FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.inventory_quality_action
    FOR ALL USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

ALTER TABLE public.inventory_quality_action_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_quality_action_line FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.inventory_quality_action_line
    FOR ALL USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

ALTER TABLE public.inventory_quality_reservation_move ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_quality_reservation_move FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.inventory_quality_reservation_move
    FOR ALL USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

DO $inventory_quality_permissions$
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
        (company_record.id, 'inventory_quality:view', '查看质量处理记录', 'API', '/api/inventory-quality/**', 'GET', 'ALL', 'NORMAL', TRUE, 'View warehouse-scoped quality actions and safe batch data', 'ACTIVE', 150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_quality:hold', '冻结库存', 'API', '/api/inventory-quality/hold-preview;/api/inventory-quality/holds', 'POST', 'ALL', 'HIGH', TRUE, 'Immediately quarantine or recall-hold authorised stock', 'ACTIVE', 151, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_quality:release', '申请或执行恢复可售', 'API', '/api/inventory-quality/releases;/api/inventory-quality/actions/*/withdraw', 'POST', 'ALL', 'HIGH', TRUE, 'Release held stock directly or request approval according to tenant policy', 'ACTIVE', 152, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_quality:review', '审批恢复可售', 'API', '/api/inventory-quality/actions/*/approve;/api/inventory-quality/actions/*/reject', 'POST', 'ALL', 'CRITICAL', TRUE, 'Approve or reject another user release request', 'ACTIVE', 153, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (company_record.id, 'inventory_quality:policy', '配置恢复可售策略', 'API', '/api/inventory-quality/policy', '*', 'ALL', 'CRITICAL', TRUE, 'Configure the tenant release-to-saleable policy when explicitly assigned', 'ACTIVE', 154, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
$inventory_quality_permissions$;

COMMENT ON TABLE public.inventory_quality_action IS
    'Tenant quality hold/release business document. Completed holds are immutable; corrections use a separate release action.';
COMMENT ON TABLE public.inventory_quality_reservation_move IS
    'Append-only evidence of sales reservation and pending outbound-task reallocation caused by a quality hold.';
