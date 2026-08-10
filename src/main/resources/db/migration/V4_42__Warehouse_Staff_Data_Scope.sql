-- V4.42: explicit warehouse assignments for the activated WAREHOUSE_STAFF role.
-- Existing staff accounts without assignments intentionally fail closed.

CREATE TABLE IF NOT EXISTS sys_user_warehouse (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT,

    CONSTRAINT fk_user_warehouse_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_warehouse_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses(id) ON DELETE RESTRICT,
    CONSTRAINT uk_sys_user_warehouse_company_user_warehouse
        UNIQUE (company_id, user_id, warehouse_id)
);

CREATE INDEX IF NOT EXISTS idx_user_warehouse_user
    ON sys_user_warehouse(user_id);
CREATE INDEX IF NOT EXISTS idx_user_warehouse_warehouse
    ON sys_user_warehouse(warehouse_id);

COMMENT ON TABLE sys_user_warehouse IS
    'Warehouses assigned to users while operating under WAREHOUSE_STAFF';
COMMENT ON COLUMN sys_user_warehouse.assigned_by IS
    'Administrator user ID that last assigned the warehouse';

-- The IAM user catalogue owns this read-only child endpoint as well.
UPDATE sys_permission
SET resource_path = '/api/users/**',
    permission_name = '查看用户与可分配仓库'
WHERE permission_code = 'system:user:view';
