-- V3.8 智能盘点系统
-- 创建日期: 2026-01-29
-- 描述: 实现基于快照的盲盘功能，支持月度/季度盘点

-- ============================================================
-- 1. 盘点任务主表 (stocktake_tasks)
-- ============================================================
CREATE TABLE IF NOT EXISTS stocktake_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(30) UNIQUE NOT NULL,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    cycle_type VARCHAR(20) NOT NULL,  -- MONTHLY, QUARTERLY, ANNUAL, ADHOC
    status VARCHAR(20) NOT NULL,      -- CREATED, COUNTING, REVIEWING, COMPLETED

    -- 快照时间（锁定库存的时间点）
    snapshot_time TIMESTAMP NOT NULL,

    -- 统计信息
    total_items INTEGER DEFAULT 0,
    counted_items INTEGER DEFAULT 0,
    difference_items INTEGER DEFAULT 0,

    -- 审核信息
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    review_comment VARCHAR(500),

    -- 创建人信息
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stocktake_task_no ON stocktake_tasks(task_no);
CREATE INDEX IF NOT EXISTS idx_stocktake_warehouse ON stocktake_tasks(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stocktake_status ON stocktake_tasks(status);
CREATE INDEX IF NOT EXISTS idx_stocktake_cycle_type ON stocktake_tasks(cycle_type);
CREATE INDEX IF NOT EXISTS idx_stocktake_created_at ON stocktake_tasks(created_at);

COMMENT ON TABLE stocktake_tasks IS '盘点任务主表';
COMMENT ON COLUMN stocktake_tasks.task_no IS '盘点任务编号（如 TK-202601-M01）';
COMMENT ON COLUMN stocktake_tasks.warehouse_id IS '仓库ID';
COMMENT ON COLUMN stocktake_tasks.cycle_type IS '盘点周期类型：MONTHLY（月度）, QUARTERLY（季度）, ANNUAL（年度）, ADHOC（临时）';
COMMENT ON COLUMN stocktake_tasks.status IS '任务状态：CREATED（已创建）, COUNTING（盘点中）, REVIEWING（审核中）, COMPLETED（已完成）';
COMMENT ON COLUMN stocktake_tasks.snapshot_time IS '快照时间（锁定库存的时间点）';
COMMENT ON COLUMN stocktake_tasks.total_items IS '盘点明细总数';
COMMENT ON COLUMN stocktake_tasks.counted_items IS '已盘点明细数';
COMMENT ON COLUMN stocktake_tasks.difference_items IS '有差异的明细数';
COMMENT ON COLUMN stocktake_tasks.reviewed_by IS '审核人ID';
COMMENT ON COLUMN stocktake_tasks.reviewed_at IS '审核时间';
COMMENT ON COLUMN stocktake_tasks.review_comment IS '审核意见';
COMMENT ON COLUMN stocktake_tasks.created_by IS '创建人ID';
COMMENT ON COLUMN stocktake_tasks.created_by_name IS '创建人姓名';

-- ============================================================
-- 2. 盘点明细表 (stocktake_items)
-- ============================================================
CREATE TABLE IF NOT EXISTS stocktake_items (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES stocktake_tasks(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    batch_id BIGINT NOT NULL REFERENCES inventory_batch(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),

    -- 快照数量（账面数 - 不可变）
    snapshot_qty INTEGER NOT NULL,

    -- 实盘数量
    counted_qty INTEGER,

    -- 差异数量（counted_qty - snapshot_qty）
    difference_qty INTEGER DEFAULT 0,

    -- 盘点状态
    is_counted BOOLEAN DEFAULT FALSE,

    -- 盘点人信息
    counted_by BIGINT,
    counted_by_name VARCHAR(100),
    counted_at TIMESTAMP,

    -- 备注
    remark VARCHAR(500),

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stocktake_item_task ON stocktake_items(task_id);
CREATE INDEX IF NOT EXISTS idx_stocktake_item_product ON stocktake_items(product_id);
CREATE INDEX IF NOT EXISTS idx_stocktake_item_batch ON stocktake_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_stocktake_item_location ON stocktake_items(location_id);
CREATE INDEX IF NOT EXISTS idx_stocktake_item_counted ON stocktake_items(is_counted);

COMMENT ON TABLE stocktake_items IS '盘点明细表';
COMMENT ON COLUMN stocktake_items.task_id IS '盘点任务ID';
COMMENT ON COLUMN stocktake_items.product_id IS '产品ID';
COMMENT ON COLUMN stocktake_items.batch_id IS '批次ID';
COMMENT ON COLUMN stocktake_items.location_id IS '库位ID';
COMMENT ON COLUMN stocktake_items.snapshot_qty IS '快照数量（账面数 - 不可变）';
COMMENT ON COLUMN stocktake_items.counted_qty IS '实盘数量';
COMMENT ON COLUMN stocktake_items.difference_qty IS '差异数量（counted_qty - snapshot_qty）';
COMMENT ON COLUMN stocktake_items.is_counted IS '是否已盘点';
COMMENT ON COLUMN stocktake_items.counted_by IS '盘点人ID';
COMMENT ON COLUMN stocktake_items.counted_by_name IS '盘点人姓名';
COMMENT ON COLUMN stocktake_items.counted_at IS '盘点时间';
COMMENT ON COLUMN stocktake_items.remark IS '备注';

-- ============================================================
-- 3. 更新 updated_at 触发器
-- ============================================================

-- stocktake_tasks 表触发器
CREATE OR REPLACE FUNCTION update_stocktake_tasks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_stocktake_tasks_updated_at ON stocktake_tasks;
CREATE TRIGGER trigger_update_stocktake_tasks_updated_at
    BEFORE UPDATE ON stocktake_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_stocktake_tasks_updated_at();

-- stocktake_items 表触发器
CREATE OR REPLACE FUNCTION update_stocktake_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_stocktake_items_updated_at ON stocktake_items;
CREATE TRIGGER trigger_update_stocktake_items_updated_at
    BEFORE UPDATE ON stocktake_items
    FOR EACH ROW
    EXECUTE FUNCTION update_stocktake_items_updated_at();

-- ============================================================
-- 4. 更新 TransactionType 枚举（如果需要）
-- ============================================================
-- 注意：如果 source_type 字段使用的是 VARCHAR 而不是 ENUM，则无需此步骤
-- 如果使用 ENUM，需要添加 INVENTORY_ADJUSTMENT 类型

-- 示例（如果使用 ENUM）：
-- ALTER TYPE source_type ADD VALUE IF NOT EXISTS 'INVENTORY_ADJUSTMENT';
