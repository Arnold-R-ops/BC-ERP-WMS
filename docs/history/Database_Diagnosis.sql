-- ============================================================================
-- BC ERP-WMS V3.3 Phase 1 数据库诊断 SQL（简化版）
-- ============================================================================
-- 用途：快速诊断数据库当前状态
-- 适用：PostgreSQL 16
-- 说明：分步执行，逐步排查问题
-- ============================================================================

-- ============================================================================
-- 步骤 1: 检查所有表是否存在
-- ============================================================================
SELECT
    table_name AS "表名"
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 预期结果：应该能看到以下表名（如果迁移成功）
-- - inventory
-- - inventory_batch
-- - locations
-- - product_spu  ← 如果这个表不存在，说明迁移失败
-- - products
-- - purchase_order
-- - purchase_order_item
-- - stock_transaction
-- - users

-- ============================================================================
-- 步骤 2: 检查 product_spu 表的列（如果表存在）
-- ============================================================================
-- 如果步骤 1 中能看到 product_spu 表，执行下面的查询
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    is_nullable AS "可空",
    column_default AS "默认值"
FROM information_schema.columns
WHERE table_name = 'product_spu'
ORDER BY ordinal_position;

-- 预期结果（正确的字段名）：
-- id, spu_code, spu_name, category, brand, description, enabled, created_at, updated_at
--
-- 可能的错误结果（旧的字段名）：
-- id, code, name, category, brand, description, enabled, created_at, updated_at

-- ============================================================================
-- 步骤 3: 检查 products 表的列
-- ============================================================================
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    is_nullable AS "可空"
FROM information_schema.columns
WHERE table_name = 'products'
ORDER BY ordinal_position;

-- 重点检查是否包含以下新列：
-- - spu_id (bigint, NOT NULL)
-- - sku_name (character varying, NOT NULL)
-- - specs (character varying, NULL)

-- ============================================================================
-- 步骤 4: 检查 inventory_batch 表的列
-- ============================================================================
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    is_nullable AS "可空"
FROM information_schema.columns
WHERE table_name = 'inventory_batch'
ORDER BY ordinal_position;

-- 重点检查是否包含新列：
-- - location_code (character varying, NOT NULL)

-- ============================================================================
-- 步骤 5: 检查 product_spu 表的数据（如果表存在）
-- ============================================================================
-- 如果 product_spu 表存在且字段名正确，执行下面的查询
SELECT * FROM product_spu;

-- 预期结果：至少有 1 条记录
-- id=0, spu_code='DEFAULT-SPU', spu_name='Default Product Family (Legacy Data)'

-- ============================================================================
-- 诊断完成！
-- ============================================================================
-- 请将以上 5 个步骤的执行结果告诉我：
-- 1. 步骤 1 的表名列表
-- 2. 步骤 2 的结果（如果 product_spu 表存在）
-- 3. 步骤 3 中是否有 spu_id, sku_name, specs 这三列
-- 4. 步骤 4 中是否有 location_code 这一列
-- 5. 步骤 5 的查询结果（如果能执行）
-- ============================================================================
