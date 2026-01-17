-- ============================================================================
-- BC ERP-WMS V3.3 Phase 1 Schema Verification SQL
-- ============================================================================
-- 用途：验证数据库是否符合 V3.3 Phase 1 标准
-- 执行方式：在 IntelliJ IDEA Database Console 中运行
-- 说明：每个查询都包含预期结果的说明
-- ============================================================================

-- ============================================================================
-- Section 1: 基础检查 - 表是否存在
-- ============================================================================

-- 1.1 列出所有表
SELECT
    table_name AS "表名",
    CASE
        WHEN table_name = 'product_spu' THEN '✓ V3.3 新表'
        WHEN table_name = 'products' THEN '✓ 已修改'
        WHEN table_name = 'inventory_batch' THEN '✓ 已修改'
        ELSE '其他表'
    END AS "状态"
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 预期结果：应该能看到 product_spu, products, inventory_batch 等表
-- 如果 product_spu 不存在，说明迁移失败

-- ============================================================================
-- Section 2: product_spu 表详细验证
-- ============================================================================

-- 2.1 检查 product_spu 表是否存在
SELECT
    COUNT(*) AS "product_spu表存在",
    CASE
        WHEN COUNT(*) > 0 THEN '✓ 表已创建'
        ELSE '✗ 表不存在，迁移失败'
    END AS "检查结果"
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name = 'product_spu';

-- 预期结果：product_spu表存在 = 1

-- 2.2 检查 product_spu 表的列（仅在表存在时执行）
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    character_maximum_length AS "最大长度",
    is_nullable AS "可空",
    column_default AS "默认值"
FROM information_schema.columns
WHERE table_name = 'product_spu'
ORDER BY ordinal_position;

-- 预期结果（共9列）：
-- id, spu_code, spu_name, category, brand, description, enabled, created_at, updated_at
--
-- 常见错误：
-- - 如果列名是 code, name 而不是 spu_code, spu_name，说明字段名错误
-- - 如果少于 9 列，说明表创建不完整

-- 2.3 检查 product_spu 表的约束
SELECT
    conname AS "约束名",
    contype AS "约束类型",
    CASE contype
        WHEN 'p' THEN 'PRIMARY KEY'
        WHEN 'u' THEN 'UNIQUE'
        WHEN 'f' THEN 'FOREIGN KEY'
        WHEN 'c' THEN 'CHECK'
        ELSE contype::text
    END AS "约束类型说明"
FROM pg_constraint
WHERE conrelid = 'product_spu'::regclass;

-- 预期结果：
-- - product_spu_pkey (PRIMARY KEY)
-- - product_spu_spu_code_key (UNIQUE)

-- 2.4 检查 product_spu 表的索引
SELECT
    indexname AS "索引名",
    indexdef AS "索引定义"
FROM pg_indexes
WHERE tablename = 'product_spu'
ORDER BY indexname;

-- 预期结果（至少3个）：
-- - idx_spu_code (UNIQUE)
-- - idx_spu_name
-- - idx_spu_category

-- 2.5 检查 product_spu 表的数据
SELECT
    id,
    spu_code,
    spu_name,
    category,
    brand,
    enabled
FROM product_spu
ORDER BY id;

-- 预期结果：至少有 1 行
-- id=0, spu_code='DEFAULT-SPU', spu_name='Default Product Family (Legacy Data)'

-- 2.6 统计 product_spu 数据
SELECT
    COUNT(*) AS "总SPU数量",
    COUNT(*) FILTER (WHERE id = 0) AS "默认SPU数量",
    COUNT(*) FILTER (WHERE enabled = true) AS "启用的SPU数量"
FROM product_spu;

-- 预期结果：总SPU数量 >= 1, 默认SPU数量 = 1

-- ============================================================================
-- Section 3: products 表验证（新增字段）
-- ============================================================================

-- 3.1 检查 products 表的新列
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    character_maximum_length AS "最大长度",
    is_nullable AS "可空"
FROM information_schema.columns
WHERE table_name = 'products'
  AND column_name IN ('spu_id', 'sku_name', 'specs')
ORDER BY
    CASE column_name
        WHEN 'spu_id' THEN 1
        WHEN 'sku_name' THEN 2
        WHEN 'specs' THEN 3
    END;

-- 预期结果（3行）：
-- spu_id (bigint, NO)
-- sku_name (character varying, NO)
-- specs (character varying, YES)
--
-- 如果没有这3列，说明 products 表迁移失败

-- 3.2 检查 products 表的外键约束
SELECT
    conname AS "约束名",
    pg_get_constraintdef(oid) AS "约束定义"
FROM pg_constraint
WHERE conrelid = 'products'::regclass
  AND contype = 'f'
  AND conname = 'fk_product_spu';

-- 预期结果：
-- fk_product_spu | FOREIGN KEY (spu_id) REFERENCES product_spu(id) ON UPDATE CASCADE ON DELETE RESTRICT

-- 3.3 检查 products 表的新索引
SELECT
    indexname AS "索引名",
    indexdef AS "索引定义"
FROM pg_indexes
WHERE tablename = 'products'
  AND indexname = 'idx_spu_id';

-- 预期结果：
-- idx_spu_id | CREATE INDEX idx_spu_id ON public.products USING btree (spu_id)

-- 3.4 检查 products 表的数据完整性
SELECT
    COUNT(*) AS "总产品数量",
    COUNT(spu_id) AS "有spu_id的产品数量",
    COUNT(sku_name) AS "有sku_name的产品数量",
    COUNT(*) FILTER (WHERE spu_id IS NULL) AS "spu_id为NULL的数量",
    COUNT(*) FILTER (WHERE sku_name IS NULL OR sku_name = '') AS "sku_name为空的数量",
    COUNT(*) FILTER (WHERE spu_id = 0) AS "使用默认SPU的产品数量"
FROM products;

-- 预期结果：
-- - 总产品数量 = 有spu_id的产品数量 = 有sku_name的产品数量
-- - spu_id为NULL的数量 = 0
-- - sku_name为空的数量 = 0
-- - 使用默认SPU的产品数量 > 0 （旧产品应该都指向默认SPU）

-- 3.5 抽样检查 products 数据
SELECT
    id,
    name AS "产品名称",
    spu_id,
    sku_name,
    specs,
    enabled
FROM products
ORDER BY id
LIMIT 10;

-- 预期结果：
-- - 每行都有 spu_id (通常是 0)
-- - 每行都有 sku_name (通常与 name 相同)
-- - specs 可以为 NULL

-- ============================================================================
-- Section 4: inventory_batch 表验证（新增字段）
-- ============================================================================

-- 4.1 检查 inventory_batch 表的新列
SELECT
    column_name AS "列名",
    data_type AS "数据类型",
    character_maximum_length AS "最大长度",
    is_nullable AS "可空"
FROM information_schema.columns
WHERE table_name = 'inventory_batch'
  AND column_name = 'location_code';

-- 预期结果：
-- location_code (character varying, 50, NO)
--
-- 如果不存在，说明 inventory_batch 表迁移失败

-- 4.2 检查 batch_code 是否还有唯一约束（应该被删除了）
SELECT
    conname AS "约束名",
    contype AS "约束类型",
    pg_get_constraintdef(oid) AS "约束定义"
FROM pg_constraint
WHERE conrelid = 'inventory_batch'::regclass
  AND contype = 'u'
  AND pg_get_constraintdef(oid) LIKE '%batch_code%';

-- 预期结果：0 行（batch_code 的唯一约束应该已被删除）
-- 如果有结果，说明约束删除失败，需要手动删除

-- 4.3 检查 inventory_batch 表的新索引
SELECT
    indexname AS "索引名",
    indexdef AS "索引定义"
FROM pg_indexes
WHERE tablename = 'inventory_batch'
  AND indexname IN ('idx_batch_code', 'idx_batch_location', 'idx_location_code')
ORDER BY indexname;

-- 预期结果（3行）：
-- idx_batch_code | CREATE INDEX idx_batch_code ON public.inventory_batch USING btree (batch_code)
-- idx_batch_location | CREATE INDEX idx_batch_location ON public.inventory_batch USING btree (batch_code, location_code)
-- idx_location_code | CREATE INDEX idx_location_code ON public.inventory_batch USING btree (location_code)

-- 4.4 检查 inventory_batch 表的数据完整性
SELECT
    COUNT(*) AS "总批次数量",
    COUNT(location_code) AS "有location_code的批次数量",
    COUNT(*) FILTER (WHERE location_code IS NULL) AS "location_code为NULL的数量",
    COUNT(*) FILTER (WHERE location_code = 'PENDING') AS "待分配库位的数量",
    COUNT(*) FILTER (WHERE location_code = 'UNASSIGNED') AS "未分配库位的数量"
FROM inventory_batch;

-- 预期结果：
-- - 总批次数量 = 有location_code的批次数量
-- - location_code为NULL的数量 = 0

-- 4.5 抽样检查 inventory_batch 数据
SELECT
    id,
    batch_code,
    location_id,
    location_code,
    quantity,
    available_quantity
FROM inventory_batch
ORDER BY id
LIMIT 10;

-- 预期结果：
-- - 每行都有 location_code
-- - 如果 location_id 不为 NULL，location_code 应该与 locations 表中的 location_code 一致

-- ============================================================================
-- Section 5: 关联验证（跨表检查）
-- ============================================================================

-- 5.1 验证 products.spu_id 与 product_spu.id 的外键完整性
SELECT
    COUNT(*) AS "孤立的产品数量",
    CASE
        WHEN COUNT(*) = 0 THEN '✓ 外键完整性正常'
        ELSE '✗ 存在孤立产品，外键约束有问题'
    END AS "检查结果"
FROM products p
LEFT JOIN product_spu s ON p.spu_id = s.id
WHERE s.id IS NULL;

-- 预期结果：孤立的产品数量 = 0

-- 5.2 统计每个 SPU 下的 SKU 数量
SELECT
    s.id AS "SPU_ID",
    s.spu_code AS "SPU编码",
    s.spu_name AS "SPU名称",
    COUNT(p.id) AS "SKU数量"
FROM product_spu s
LEFT JOIN products p ON s.id = p.spu_id
GROUP BY s.id, s.spu_code, s.spu_name
ORDER BY s.id;

-- 预期结果：
-- - ID=0 的默认 SPU 应该有 SKU 数量 > 0（所有旧产品）
-- - 其他 SPU 可能有 0 个 SKU（如果还没创建新 SPU）

-- 5.3 验证 inventory_batch.location_code 与 locations.location_code 的一致性
SELECT
    COUNT(*) AS "location_code不一致的批次数量",
    CASE
        WHEN COUNT(*) = 0 THEN '✓ location_code 同步正常'
        ELSE '✗ 存在不一致，需要修复'
    END AS "检查结果"
FROM inventory_batch ib
INNER JOIN locations l ON ib.location_id = l.id
WHERE ib.location_code != l.location_code
  AND ib.location_code NOT IN ('PENDING', 'UNASSIGNED');

-- 预期结果：location_code不一致的批次数量 = 0

-- 5.4 检查同一 batch_code 在多个 location_code 的情况（多库位批次）
SELECT
    batch_code AS "批次号",
    COUNT(DISTINCT location_code) AS "库位数量",
    STRING_AGG(DISTINCT location_code, ', ' ORDER BY location_code) AS "库位列表",
    SUM(quantity) AS "总数量"
FROM inventory_batch
GROUP BY batch_code
HAVING COUNT(DISTINCT location_code) > 1
ORDER BY COUNT(DISTINCT location_code) DESC
LIMIT 10;

-- 预期结果：
-- - 如果有结果，说明多库位批次管理功能正常
-- - 如果没有结果，说明当前没有跨库位的批次（这也是正常的）

-- ============================================================================
-- Section 6: 序列验证
-- ============================================================================

-- 6.1 检查 product_spu_id_seq 序列
SELECT
    sequence_name AS "序列名",
    last_value AS "当前值",
    increment_by AS "增量",
    CASE
        WHEN last_value >= 1 THEN '✓ 序列正常（ID=0 插入后已重置）'
        ELSE '✗ 序列值异常'
    END AS "检查结果"
FROM information_schema.sequences s
JOIN pg_sequences ps ON s.sequence_name = ps.sequencename
WHERE sequence_name = 'product_spu_id_seq';

-- 预期结果：当前值 >= 1

-- ============================================================================
-- Section 7: 总体验证报告
-- ============================================================================

-- 7.1 综合验证报告
SELECT
    'product_spu 表' AS "检查项",
    CASE
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'product_spu')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END AS "状态"
UNION ALL
SELECT
    'product_spu 默认数据 (ID=0)',
    CASE
        WHEN EXISTS (SELECT 1 FROM product_spu WHERE id = 0)
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'products.spu_id 列',
    CASE
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'spu_id')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'products.sku_name 列',
    CASE
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'sku_name')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'products.specs 列',
    CASE
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'specs')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'inventory_batch.location_code 列',
    CASE
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'inventory_batch' AND column_name = 'location_code')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'fk_product_spu 外键约束',
    CASE
        WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_spu')
        THEN '✓ 存在'
        ELSE '✗ 不存在'
    END
UNION ALL
SELECT
    'batch_code 唯一约束已删除',
    CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'inventory_batch'::regclass
              AND contype = 'u'
              AND pg_get_constraintdef(oid) LIKE '%batch_code%'
        )
        THEN '✓ 已删除'
        ELSE '✗ 仍存在'
    END;

-- 预期结果：所有状态都应该是 ✓

-- ============================================================================
-- Section 8: 数据质量检查
-- ============================================================================

-- 8.1 检查是否有 NULL 数据（不应该有）
SELECT
    '产品缺少 spu_id' AS "数据质量问题",
    COUNT(*) AS "问题数量"
FROM products
WHERE spu_id IS NULL
UNION ALL
SELECT
    '产品缺少 sku_name',
    COUNT(*)
FROM products
WHERE sku_name IS NULL OR sku_name = ''
UNION ALL
SELECT
    '批次缺少 location_code',
    COUNT(*)
FROM inventory_batch
WHERE location_code IS NULL;

-- 预期结果：所有 "问题数量" 都应该是 0

-- ============================================================================
-- Section 9: 性能检查（可选）
-- ============================================================================

-- 9.1 检查索引使用情况（需要有实际查询后才有统计数据）
SELECT
    schemaname AS "schema",
    tablename AS "表名",
    indexname AS "索引名",
    idx_scan AS "索引扫描次数",
    idx_tup_read AS "索引返回行数",
    idx_tup_fetch AS "索引获取行数"
FROM pg_stat_user_indexes
WHERE tablename IN ('product_spu', 'products', 'inventory_batch')
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;

-- 说明：idx_scan = 0 表示索引还未被使用（新建表正常）

-- ============================================================================
-- 验证完成！
-- ============================================================================
--
-- 如何判断迁移是否成功：
--
-- ✓ 成功标准：
-- 1. Section 1: product_spu 表存在
-- 2. Section 2.2: product_spu 有 9 列，列名是 spu_code/spu_name（不是 code/name）
-- 3. Section 2.5: product_spu 至少有 1 行数据（ID=0）
-- 4. Section 3.1: products 有 spu_id, sku_name, specs 三列
-- 5. Section 3.4: 所有产品的 spu_id 和 sku_name 都不为 NULL
-- 6. Section 4.1: inventory_batch 有 location_code 列
-- 7. Section 4.4: 所有批次的 location_code 都不为 NULL
-- 8. Section 7.1: 所有检查项都是 ✓
--
-- ✗ 失败情况及解决方案：
-- 1. product_spu 表不存在 → 执行 V3.3_Migration_Simple_Step_by_Step.sql 的 STEP 1-6
-- 2. product_spu 列名错误 → DROP TABLE 后重建
-- 3. products 缺少新列 → 执行 V3.3_Migration_Simple_Step_by_Step.sql 的 STEP 7-15
-- 4. inventory_batch 缺少新列 → 执行 V3.3_Migration_Simple_Step_by_Step.sql 的 STEP 17-23
--
-- ============================================================================
