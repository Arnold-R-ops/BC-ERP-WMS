-- Add reviewer name to stocktake tasks
ALTER TABLE stocktake_tasks
    ADD COLUMN IF NOT EXISTS reviewed_by_name VARCHAR(100);

COMMENT ON COLUMN stocktake_tasks.reviewed_by_name IS 'Reviewer name';
