-- ============================================================================
-- WMS Phase 3.4: 一键执行迁移脚本
-- 请在 pgAdmin 中完整执行此脚本
-- ============================================================================

-- Step 1: 创建 warehouses 表
CREATE TABLE warehouses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    contact VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE warehouses IS 'Warehouse master data table';
CREATE INDEX idx_warehouse_code ON warehouses(code);
CREATE INDEX idx_warehouse_active ON warehouses(is_active);

-- Step 2: 从 locations 表提取仓库数据
INSERT INTO warehouses (code, name, is_active, created_at, updated_at)
SELECT DISTINCT
    warehouse_code AS code,
    CONCAT('Warehouse ', warehouse_code) AS name,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at
FROM locations
WHERE warehouse_code IS NOT NULL
ORDER BY warehouse_code;

-- Step 3: 为 locations 表添加 warehouse_id 列
ALTER TABLE locations ADD COLUMN warehouse_id BIGINT;
CREATE INDEX idx_location_warehouse_id ON locations(warehouse_id);

-- Step 4: 填充 warehouse_id 数据
UPDATE locations l
SET warehouse_id = w.id
FROM warehouses w
WHERE l.warehouse_code = w.code;

-- Step 5: 添加约束
ALTER TABLE locations ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE locations
ADD CONSTRAINT fk_location_warehouse
FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- Step 6: 更新注释
COMMENT ON COLUMN locations.warehouse_id IS 'Foreign key to warehouses table';
COMMENT ON COLUMN locations.warehouse_code IS 'Warehouse code (redundant, synced from warehouse.code for performance)';

-- ============================================================================
-- 验证结果
-- ============================================================================
SELECT '✅ 创建的仓库数量:' AS 步骤, COUNT(*) AS 数量 FROM warehouses;
SELECT '✅ 仓库列表:' AS 步骤, * FROM warehouses ORDER BY code;
SELECT '✅ Locations 表更新:' AS 步骤, COUNT(*) AS 已更新数量 FROM locations WHERE warehouse_id IS NOT NULL;
SELECT '✅ 数据一致性检查:' AS 步骤, COUNT(*) AS 不一致数量 FROM locations l INNER JOIN warehouses w ON l.warehouse_id = w.id WHERE l.warehouse_code != w.code;

-- 显示最终结果
SELECT
    '🎉 迁移完成!' AS 状态,
    (SELECT COUNT(*) FROM warehouses) AS 仓库总数,
    (SELECT COUNT(*) FROM locations) AS 库位总数,
    (SELECT COUNT(*) FROM locations WHERE warehouse_id IS NOT NULL) AS 已关联库位数;
