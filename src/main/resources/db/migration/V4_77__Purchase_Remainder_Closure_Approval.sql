-- V4.77: optional enterprise approval for closing an unreceived ASN remainder.
-- Ordinary tenants continue to close directly with a required reason. This
-- table is only used when the independently configurable approval mode is on.

CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_remainder_parent_company_asn
    ON public.purchase_asn(company_id, id);

CREATE TABLE IF NOT EXISTS public.purchase_remainder_closure_request (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    purchase_asn_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_by_name VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reviewed_by BIGINT,
    reviewed_by_name VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    review_comment VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_remainder_closure_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_purchase_remainder_closure_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_purchase_remainder_closure_asn FOREIGN KEY (company_id, purchase_asn_id)
        REFERENCES public.purchase_asn(company_id, id),
    CONSTRAINT ck_purchase_remainder_closure_version CHECK (version >= 0),
    CONSTRAINT ck_purchase_remainder_closure_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_purchase_remainder_closure_reason CHECK (
        length(btrim(reason)) > 0
        AND length(btrim(requested_by_name)) > 0
    ),
    CONSTRAINT ck_purchase_remainder_closure_review CHECK (
        (status = 'PENDING'
            AND reviewed_by IS NULL
            AND reviewed_at IS NULL
            AND review_comment IS NULL)
        OR
        (status <> 'PENDING'
            AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND length(btrim(review_comment)) > 0)
    ),
    CONSTRAINT ck_purchase_remainder_closure_separation CHECK (
        status = 'CANCELLED' OR reviewed_by IS NULL OR reviewed_by <> requested_by
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_remainder_closure_pending
    ON public.purchase_remainder_closure_request(company_id, purchase_asn_id)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_purchase_remainder_closure_asn_status
    ON public.purchase_remainder_closure_request(company_id, purchase_asn_id, status);
CREATE INDEX IF NOT EXISTS idx_purchase_remainder_closure_status_created
    ON public.purchase_remainder_closure_request(company_id, status, created_at DESC);

ALTER TABLE public.purchase_remainder_closure_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_remainder_closure_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS company_isolation ON public.purchase_remainder_closure_request;
CREATE POLICY company_isolation ON public.purchase_remainder_closure_request
    FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

-- These two permissions are deliberately not auto-assigned to the same role.
-- A tenant administrator may grant request authority to purchasing staff and
-- approval authority to a manager only after enabling enterprise governance.
DO $purchase_remainder_permissions$
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
        ) VALUES
        (
            company_record.id,
            'purchase:request_close_remaining',
            '申请关闭采购未收数量',
            'API',
            '/api/purchase-asns/*/remainder-closure-requests;/api/purchase-remainder-closure-requests;/api/purchase-remainder-closure-requests/*;/api/purchase-remainder-closure-requests/*/cancel',
            '*',
            'ALL',
            'HIGH',
            TRUE,
            'Request closure of an unreceived ASN remainder when optional approval is enabled',
            'ACTIVE',
            148,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        ),
        (
            company_record.id,
            'purchase:approve_close_remaining',
            '审批关闭采购未收数量',
            'API',
            '/api/purchase-remainder-closure-requests;/api/purchase-remainder-closure-requests/**',
            '*',
            'ALL',
            'CRITICAL',
            TRUE,
            'Approve or reject another user''s request to close an unreceived ASN remainder',
            'ACTIVE',
            149,
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
$purchase_remainder_permissions$;

COMMENT ON TABLE public.purchase_remainder_closure_request IS
    'Optional two-person approval record for closing the unreceived remainder of a partially received purchase ASN.';
