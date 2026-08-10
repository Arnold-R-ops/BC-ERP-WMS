-- Preserve historical test evidence while removing eligible test orders/tasks from live work queues.

ALTER TABLE outbound_tasks
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archived_by BIGINT,
    ADD COLUMN IF NOT EXISTS archive_reason VARCHAR(500);

ALTER TABLE outbound_tasks
    DROP CONSTRAINT IF EXISTS ck_outbound_task_archive_fields;

ALTER TABLE outbound_tasks
    ADD CONSTRAINT ck_outbound_task_archive_fields CHECK (
        (status = 'VOIDED' AND archived_at IS NOT NULL AND archived_by IS NOT NULL AND archive_reason IS NOT NULL)
        OR
        (status <> 'VOIDED' AND archived_at IS NULL AND archived_by IS NULL AND archive_reason IS NULL)
    );

CREATE INDEX IF NOT EXISTS idx_outbound_tasks_archive
    ON outbound_tasks(company_id, archived_at DESC)
    WHERE status = 'VOIDED';

CREATE TABLE IF NOT EXISTS historical_test_data_archive_audit (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    sales_order_id BIGINT NOT NULL,
    order_no VARCHAR(30) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_username VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    snapshot_fingerprint VARCHAR(64) NOT NULL,
    before_snapshot TEXT NOT NULL,
    after_snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_historical_archive_audit_order
    ON historical_test_data_archive_audit(company_id, sales_order_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_historical_archive_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'historical_test_data_archive_audit is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_historical_archive_audit_immutable
    ON historical_test_data_archive_audit;

CREATE TRIGGER trg_historical_archive_audit_immutable
BEFORE UPDATE OR DELETE ON historical_test_data_archive_audit
FOR EACH ROW EXECUTE FUNCTION prevent_historical_archive_audit_mutation();

COMMENT ON TABLE historical_test_data_archive_audit IS
    'Append-only before/after evidence for guarded historical test data archives';
