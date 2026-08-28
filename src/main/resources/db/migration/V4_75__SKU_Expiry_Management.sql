-- V4.75: SKU-level expiry-management policy and optional batch expiry dates.
--
-- Legacy SKUs are marked as expiry-managed because the pre-V4.75 receiving
-- flow always required an expiry date. New SKUs default to ordinary,
-- non-expiry-managed goods unless the tenant opts in explicitly.

ALTER TABLE public.product_skus
    ADD COLUMN IF NOT EXISTS expiry_management_enabled BOOLEAN;

-- product_skus uses FORCE ROW LEVEL SECURITY. Backfill one company at a time
-- so this migration never bypasses or accidentally crosses tenant scope.
DO $sku_expiry_backfill$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP
        PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE);

        UPDATE public.product_skus
        SET expiry_management_enabled = TRUE
        WHERE company_id = company_record.id
          AND expiry_management_enabled IS NULL;
    END LOOP;

    PERFORM set_config('app.company_id', '', TRUE);
END
$sku_expiry_backfill$;

ALTER TABLE public.product_skus
    ALTER COLUMN expiry_management_enabled SET DEFAULT FALSE,
    ALTER COLUMN expiry_management_enabled SET NOT NULL;

COMMENT ON COLUMN public.product_skus.expiry_management_enabled IS
    'Whether production_date and expiry_date are mandatory when receiving this SKU; legacy SKUs were backfilled TRUE and new SKUs default FALSE.';

ALTER TABLE public.inventory_batch
    ALTER COLUMN expiry_date DROP NOT NULL;

COMMENT ON COLUMN public.inventory_batch.expiry_date IS
    'Optional for SKUs without expiry management; dated batches remain FEFO-prioritized ahead of undated batches.';
