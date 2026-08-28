-- V4.78: physical-inventory quality truth and append-only state lineage.
--
-- Existing active and historical batches are normalized to AVAILABLE without
-- replaying stock, creating synthetic events, or changing any quantity.

ALTER TABLE public.inventory_batch
    ADD COLUMN IF NOT EXISTS quality_status VARCHAR(30);

-- inventory_batch uses FORCE ROW LEVEL SECURITY; backfill one company at a
-- time so the migration never crosses the current company boundary.
DO $inventory_quality_backfill$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);
        UPDATE public.inventory_batch
        SET quality_status = 'AVAILABLE'
        WHERE company_id = company_record.id
          AND quality_status IS NULL;
    END LOOP;
    PERFORM set_config('app.company_id', '', TRUE);
END
$inventory_quality_backfill$;

ALTER TABLE public.inventory_batch
    ALTER COLUMN quality_status SET DEFAULT 'AVAILABLE',
    ALTER COLUMN quality_status SET NOT NULL;

DO $inventory_quality_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.inventory_batch'::regclass
          AND conname = 'ck_inventory_batch_quality_status'
    ) THEN
        ALTER TABLE public.inventory_batch
            ADD CONSTRAINT ck_inventory_batch_quality_status CHECK (
                quality_status IN (
                    'AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED',
                    'EXPIRED','RECALL_HOLD','RETURN_INSPECTION'
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.inventory_batch'::regclass
          AND conname = 'ck_inventory_batch_non_saleable_unreserved'
    ) THEN
        ALTER TABLE public.inventory_batch
            ADD CONSTRAINT ck_inventory_batch_non_saleable_unreserved CHECK (
                quality_status = 'AVAILABLE' OR reserved_quantity = 0
            );
    END IF;
END
$inventory_quality_constraints$;

CREATE INDEX IF NOT EXISTS idx_inventory_batch_company_quality_sku_active
    ON public.inventory_batch(company_id, quality_status, product_sku_id, active)
    WHERE quantity > 0;

COMMENT ON COLUMN public.inventory_batch.quality_status IS
    'Authoritative physical-stock quality state. Only AVAILABLE and date-valid stock is saleable; active remains the row lifecycle flag.';

CREATE TABLE public.inventory_status_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    batch_code VARCHAR(50) NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    quantity_snapshot INTEGER NOT NULL,
    source_type VARCHAR(60) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    reason_detail VARCHAR(500),
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_status_event_company_id UNIQUE (company_id, id),
    CONSTRAINT uk_inventory_status_event_company_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT fk_inventory_status_event_company FOREIGN KEY (company_id)
        REFERENCES public.tenants(id),
    CONSTRAINT fk_inventory_status_event_batch FOREIGN KEY (company_id, inventory_batch_id)
        REFERENCES public.inventory_batch(company_id, id),
    CONSTRAINT ck_inventory_status_event_from CHECK (from_status IN (
        'AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED',
        'EXPIRED','RECALL_HOLD','RETURN_INSPECTION'
    )),
    CONSTRAINT ck_inventory_status_event_to CHECK (to_status IN (
        'AVAILABLE','PENDING_INSPECTION','QUARANTINED','DAMAGED',
        'EXPIRED','RECALL_HOLD','RETURN_INSPECTION'
    )),
    CONSTRAINT ck_inventory_status_event_changed CHECK (from_status <> to_status),
    CONSTRAINT ck_inventory_status_event_quantity CHECK (quantity_snapshot > 0),
    CONSTRAINT ck_inventory_status_event_required_text CHECK (
        length(btrim(batch_code)) > 0
        AND length(btrim(source_type)) > 0
        AND length(btrim(source_id)) > 0
        AND length(btrim(reason_code)) > 0
        AND length(btrim(actor_name)) > 0
        AND length(btrim(idempotency_key)) > 0
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_inventory_status_event_batch_created
    ON public.inventory_status_event(company_id, inventory_batch_id, created_at, id);
CREATE INDEX idx_inventory_status_event_source
    ON public.inventory_status_event(company_id, source_type, source_id);

ALTER TABLE public.inventory_status_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_status_event FORCE ROW LEVEL SECURITY;
CREATE POLICY company_isolation ON public.inventory_status_event
    FOR ALL
    USING (company_id = public.bcwms_current_company_id())
    WITH CHECK (company_id = public.bcwms_current_company_id());

CREATE OR REPLACE FUNCTION public.prevent_inventory_status_event_mutation()
RETURNS TRIGGER AS $function$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.tenant_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'inventory_status_event is append-only';
END;
$function$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_status_event_immutable
BEFORE UPDATE OR DELETE ON public.inventory_status_event
FOR EACH ROW
EXECUTE FUNCTION public.prevent_inventory_status_event_mutation();

COMMENT ON TABLE public.inventory_status_event IS
    'Append-only lineage for inventory quality-state transitions; quantity changes remain in stock_transactions.';
