-- Keep the PostgreSQL status constraint aligned with OutboundTaskStatus.
-- V4.45 added VOIDED archive fields but the pre-existing enum check still
-- accepted only PENDING, PICKING, and COMPLETED.

ALTER TABLE outbound_tasks
    DROP CONSTRAINT IF EXISTS outbound_tasks_status_check;

ALTER TABLE outbound_tasks
    ADD CONSTRAINT outbound_tasks_status_check CHECK (
        status IN ('PENDING', 'PICKING', 'COMPLETED', 'VOIDED')
    );

COMMENT ON COLUMN outbound_tasks.status IS
    'Task status: PENDING, PICKING, COMPLETED, VOIDED';
