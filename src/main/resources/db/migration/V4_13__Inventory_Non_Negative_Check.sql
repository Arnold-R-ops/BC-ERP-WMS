-- ====================================================================
-- V4.13: Database-level guard against negative inventory (P0 fix)
--
-- Why:
-- Negative-stock prevention previously existed only in Java entity
-- methods (InventoryBatch.decreaseQuantity / releaseReservedQuantity).
-- Any write path that bypasses them (manual SQL, bulk JPQL updates,
-- future code that forgets the entity methods) could silently corrupt
-- stock figures. This CHECK constraint is the last line of defence:
-- the database rejects the write and the transaction rolls back,
-- regardless of where it came from.
--
-- Deliberately NOT enforced here: reserved_quantity <= quantity.
-- A stocktake correction may legitimately shrink quantity below the
-- currently reserved amount (physical count < reservations). That
-- mismatch must be resolved by releasing reservations in the business
-- flow, not rejected at the database layer.
--
-- Notes:
-- - GlobalExceptionHandler already maps DataIntegrityViolationException,
--   so a violation surfaces as a structured error response, not a bare 500.
-- ====================================================================

-- Step 1: refuse to migrate if existing data already violates the rule.
-- The exception message lists offending batch ids so they can be
-- corrected (via the emergency stock correction flow) before retrying.
DO $$
DECLARE
    v_bad_ids TEXT;
    v_bad_count BIGINT;
BEGIN
    SELECT COUNT(*), string_agg(id::text, ', ' ORDER BY id)
    INTO v_bad_count, v_bad_ids
    FROM (
        SELECT id
        FROM inventory_batch
        WHERE quantity < 0
           OR reserved_quantity < 0
        LIMIT 50
    ) bad;

    IF v_bad_count > 0 THEN
        RAISE EXCEPTION
            'V4_13 aborted: % inventory_batch row(s) have negative stock (ids: %). Fix them via emergency stock correction, then rerun the migration.',
            v_bad_count, v_bad_ids;
    END IF;
END $$;

-- Step 2: add the constraint (idempotent).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_batch_non_negative'
    ) THEN
        ALTER TABLE inventory_batch
            ADD CONSTRAINT chk_inventory_batch_non_negative
            CHECK (quantity >= 0 AND reserved_quantity >= 0);
    END IF;
END $$;
