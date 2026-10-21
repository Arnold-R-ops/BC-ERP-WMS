-- Existing balances deliberately remain unverified: division cannot identify physical boxes.
ALTER TABLE inventory_batch ADD COLUMN packaging jsonb;
ALTER TABLE inventory_batch ADD COLUMN stocktake_task_id bigint;
ALTER TABLE stocktake_items ADD COLUMN counted_packaging jsonb;
ALTER TABLE stocktake_items ADD COLUMN snapshot_batch_version integer;
ALTER TABLE transfer_dispatch_line ADD COLUMN packaging jsonb;
CREATE INDEX idx_batch_stocktake_task ON inventory_batch(company_id, stocktake_task_id)
    WHERE stocktake_task_id IS NOT NULL;
