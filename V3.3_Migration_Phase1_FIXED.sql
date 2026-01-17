-- ============================================================================
-- BC ERP-WMS V3.3 Migration Script - Phase 1 (强力修复版)
-- Database & Entity Layer Migration - PostgreSQL 16
-- ============================================================================
--
-- 用途：从旧版本迁移到 V3.3 架构（SPU-SKU 层级 + 多库位批次管理）
-- 特性：
-- - 防错处理（IF NOT EXISTS）
-- - 自动清理旧约束（通用方式）
-- - 数据初始化保护（防止外键错误）
-- - 无事务控制（每条语句独立执行）
--
-- 执行方式：直接在 IntelliJ IDEA Database Console 中运行
--
-- Author: WMS Team
-- Date: 2026-01-17
-- Version: 3.3 (强力修复版)
-- ============================================================================

-- ============================================================================
-- STEP 1: 创建 product_spu 表（产品家族表）
-- ============================================================================

-- 1.1 创建表（如果不存在）
CREATE TABLE IF NOT EXISTS product_spu (
    id BIGSERIAL PRIMARY KEY,
    spu_code VARCHAR(50) NOT NULL,
    spu_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    brand VARCHAR(100),
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 添加唯一约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_spu_spu_code_key' AND conrelid = 'product_spu'::regclass
    ) THEN
        ALTER TABLE product_spu ADD CONSTRAINT product_spu_spu_code_key UNIQUE (spu_code);
    END IF;
END $$;

-- 1.3 创建索引（如果不存在）
CREATE INDEX IF NOT EXISTS idx_spu_code ON product_spu(spu_code);
CREATE INDEX IF NOT EXISTS idx_spu_name ON product_spu(spu_name);
CREATE INDEX IF NOT EXISTS idx_spu_category ON product_spu(category);

-- 1.4 添加表注释
COMMENT ON TABLE product_spu IS 'Product SPU (Standard Product Unit) - Product Family';
COMMENT ON COLUMN product_spu.spu_code IS 'SPU code (unique identifier)';
COMMENT ON COLUMN product_spu.spu_name IS 'SPU name (product family name)';

-- ============================================================================
-- STEP 2: 插入默认 SPU (ID=0) - 必须在修改 products 表之前完成
-- ============================================================================

-- 2.1 插入默认 SPU（如果不存在）
INSERT INTO product_spu (id, spu_code, spu_name, category, brand, description, enabled)
VALUES (
    0,
    'DEFAULT-SPU',
    'Default Product Family (Legacy Data)',
    'Uncategorized',
    NULL,
    'Default SPU for existing products before V3.3 migration. Please update product-spu mapping manually.',
    true
)
ON CONFLICT (id) DO NOTHING;

-- 2.2 重置序列（确保下一个自动生成的 ID 从 1 开始）
SELECT setval('product_spu_id_seq', GREATEST(1, (SELECT MAX(id) FROM product_spu)), true);

-- ============================================================================
-- STEP 3: 修改 products 表（添加 SPU-SKU 字段）
-- ============================================================================

-- 3.1 添加 spu_id 列（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'spu_id'
    ) THEN
        ALTER TABLE products ADD COLUMN spu_id BIGINT;
    END IF;
END $$;

-- 3.2 添加 sku_name 列（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'sku_name'
    ) THEN
        ALTER TABLE products ADD COLUMN sku_name VARCHAR(100);
    END IF;
END $$;

-- 3.3 添加 specs 列（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'specs'
    ) THEN
        ALTER TABLE products ADD COLUMN specs VARCHAR(500);
    END IF;
END $$;

-- 3.4 添加列注释
COMMENT ON COLUMN products.spu_id IS 'Foreign key to product_spu (Product Family)';
COMMENT ON COLUMN products.sku_name IS 'SKU-specific name (short identifier)';
COMMENT ON COLUMN products.specs IS 'Specification description (JSON or text)';

-- ============================================================================
-- STEP 4: 数据清洗 - products 表（必须在添加约束之前完成）
-- ============================================================================

-- 4.1 更新 spu_id：将所有 NULL 值更新为 0（默认 SPU）
UPDATE products SET spu_id = 0 WHERE spu_id IS NULL;

-- 4.2 更新 sku_name：将所有 NULL 值更新为 name
UPDATE products SET sku_name = name WHERE sku_name IS NULL OR sku_name = '';

-- ============================================================================
-- STEP 5: 添加 products 表的约束和索引
-- ============================================================================

-- 5.1 添加 NOT NULL 约束（如果尚未添加）
DO $$
BEGIN
    -- 设置 spu_id 为 NOT NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'spu_id' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE products ALTER COLUMN spu_id SET NOT NULL;
    END IF;

    -- 设置 sku_name 为 NOT NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'products' AND column_name = 'sku_name' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE products ALTER COLUMN sku_name SET NOT NULL;
    END IF;
END $$;

-- 5.2 添加外键约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_product_spu' AND conrelid = 'products'::regclass
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT fk_product_spu
        FOREIGN KEY (spu_id)
        REFERENCES product_spu(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;
    END IF;
END $$;

-- 5.3 创建索引（如果不存在）
CREATE INDEX IF NOT EXISTS idx_spu_id ON products(spu_id);

-- ============================================================================
-- STEP 6: 修改 inventory_batch 表（添加 location_code，移除 batch_code 唯一约束）
-- ============================================================================

-- 6.1 删除 batch_code 上的所有唯一约束（通用方式）
DO $$
DECLARE
    constraint_name_rec RECORD;
BEGIN
    -- 查找所有作用于 batch_code 列的唯一约束
    FOR constraint_name_rec IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'inventory_batch'::regclass
          AND contype = 'u'  -- 'u' = unique constraint
          AND conname IN (
              SELECT constraint_name
              FROM information_schema.constraint_column_usage
              WHERE table_name = 'inventory_batch'
                AND column_name = 'batch_code'
          )
    LOOP
        EXECUTE format('ALTER TABLE inventory_batch DROP CONSTRAINT IF EXISTS %I', constraint_name_rec.conname);
        RAISE NOTICE 'Dropped unique constraint: %', constraint_name_rec.conname;
    END LOOP;
END $$;

-- 6.2 删除 batch_code 上的唯一索引（如果存在）
DO $$
DECLARE
    index_name_rec RECORD;
BEGIN
    -- 查找所有作用于 batch_code 列的唯一索引
    FOR index_name_rec IN
        SELECT indexname
        FROM pg_indexes
        WHERE tablename = 'inventory_batch'
          AND indexdef LIKE '%UNIQUE%'
          AND indexdef LIKE '%batch_code%'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', index_name_rec.indexname);
        RAISE NOTICE 'Dropped unique index: %', index_name_rec.indexname;
    END LOOP;
END $$;

-- 6.3 添加 location_code 列（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_batch' AND column_name = 'location_code'
    ) THEN
        ALTER TABLE inventory_batch ADD COLUMN location_code VARCHAR(50);
    END IF;
END $$;

-- 6.4 添加列注释
COMMENT ON COLUMN inventory_batch.location_code IS 'V3.3: Location code string (redundant field for quick query)';

-- ============================================================================
-- STEP 7: 数据清洗 - inventory_batch 表（必须在添加约束之前完成）
-- ============================================================================

-- 7.1 从 locations 表同步 location_code（如果 location_id 不为空）
UPDATE inventory_batch ib
SET location_code = l.location_code
FROM locations l
WHERE ib.location_id = l.id
  AND ib.location_code IS NULL;

-- 7.2 为未分配库位的批次（IN_TRANSIT 状态）设置占位符
UPDATE inventory_batch
SET location_code = 'PENDING'
WHERE location_code IS NULL AND location_id IS NULL;

-- 7.3 为其他 NULL 值设置默认值（边缘情况）
UPDATE inventory_batch
SET location_code = 'UNASSIGNED'
WHERE location_code IS NULL;

-- ============================================================================
-- STEP 8: 添加 inventory_batch 表的约束和索引
-- ============================================================================

-- 8.1 添加 NOT NULL 约束（如果尚未添加）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_batch' AND column_name = 'location_code' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE inventory_batch ALTER COLUMN location_code SET NOT NULL;
    END IF;
END $$;

-- 8.2 创建新索引（支持多库位批次管理）
CREATE INDEX IF NOT EXISTS idx_batch_code ON inventory_batch(batch_code);
CREATE INDEX IF NOT EXISTS idx_batch_location ON inventory_batch(batch_code, location_code);
CREATE INDEX IF NOT EXISTS idx_location_code ON inventory_batch(location_code);

-- ============================================================================
-- STEP 9: 验证迁移结果
-- ============================================================================

-- 9.1 验证 product_spu 表和默认 SPU
SELECT
    'product_spu 表验证' AS check_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN id = 0 THEN 1 ELSE 0 END) AS default_spu_count
FROM product_spu;

-- 9.2 验证 products 表的新列
SELECT
    'products 表验证' AS check_name,
    COUNT(*) AS total_products,
    SUM(CASE WHEN spu_id IS NULL THEN 1 ELSE 0 END) AS products_without_spu,
    SUM(CASE WHEN sku_name IS NULL THEN 1 ELSE 0 END) AS products_without_sku_name,
    SUM(CASE WHEN spu_id = 0 THEN 1 ELSE 0 END) AS products_with_default_spu
FROM products;

-- 9.3 验证 inventory_batch 表的新列
SELECT
    'inventory_batch 表验证' AS check_name,
    COUNT(*) AS total_batches,
    SUM(CASE WHEN location_code IS NULL THEN 1 ELSE 0 END) AS batches_without_location_code,
    SUM(CASE WHEN location_code = 'PENDING' THEN 1 ELSE 0 END) AS batches_pending,
    SUM(CASE WHEN location_code = 'UNASSIGNED' THEN 1 ELSE 0 END) AS batches_unassigned
FROM inventory_batch;

-- 9.4 检查约束和索引
SELECT
    'constraints 验证' AS check_name,
    COUNT(*) FILTER (WHERE conname = 'fk_product_spu') AS fk_product_spu_exists,
    COUNT(*) FILTER (WHERE conname = 'product_spu_spu_code_key') AS spu_code_unique_exists
FROM pg_constraint
WHERE conrelid IN ('products'::regclass, 'product_spu'::regclass);

SELECT
    'indexes 验证' AS check_name,
    COUNT(*) FILTER (WHERE indexname = 'idx_spu_id') AS idx_spu_id_exists,
    COUNT(*) FILTER (WHERE indexname = 'idx_batch_code') AS idx_batch_code_exists,
    COUNT(*) FILTER (WHERE indexname = 'idx_batch_location') AS idx_batch_location_exists
FROM pg_indexes
WHERE tablename IN ('products', 'inventory_batch');

-- ============================================================================
-- STEP 10: 迁移完成总结
-- ============================================================================

SELECT
    'V3.3 Migration Completed!' AS status,
    (SELECT COUNT(*) FROM product_spu) AS total_spus,
    (SELECT COUNT(*) FROM products) AS total_skus,
    (SELECT COUNT(*) FROM inventory_batch) AS total_batches,
    CURRENT_TIMESTAMP AS completed_at;

-- ============================================================================
-- 迁移完成！
-- ============================================================================
-- 预期结果：
-- 1. product_spu 表已创建，包含 1 条默认 SPU (ID=0)
-- 2. products 表新增字段：spu_id (NOT NULL), sku_name (NOT NULL), specs
-- 3. 所有现有产品的 spu_id = 0, sku_name = name
-- 4. inventory_batch 表新增字段：location_code (NOT NULL)
-- 5. batch_code 的唯一约束已移除，新增复合索引 (batch_code, location_code)
--
-- 下一步：
-- 1. 在 IntelliJ IDEA 中刷新数据库连接（View → Tool Windows → Database → 右键 → Refresh）
-- 2. 启动应用：mvn spring-boot:run
-- 3. 检查启动日志，确保无 Hibernate 错误
-- ============================================================================
