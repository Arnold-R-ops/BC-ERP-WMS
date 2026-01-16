-- ============================================================================
-- V3.3 架构升级迁移脚本 (Phase 1: Database Schema Migration)
-- 功能：SPU-SKU 层级管理 + 多库位批次管理
-- 执行时机：在启动应用之前执行
-- 数据库：MySQL 8.0+
--
-- 升级内容：
-- 1. 新建 product_spu 表（SPU 产品家族）
-- 2. 修改 products 表（升级为 SKU 层级）
-- 3. 修改 inventory_batch 表（支持多库位）
-- 4. 数据迁移（现有数据迁移到默认 SPU）
--
-- 回滚方案：见文件末尾
-- ============================================================================

-- ============================================================================
-- Step 1: 创建 product_spu 表 (SPU Product Table)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `product_spu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SPU ID（主键）',
    `code` VARCHAR(50) NOT NULL COMMENT 'SPU 编码（唯一标识符）',
    `name` VARCHAR(200) NOT NULL COMMENT 'SPU 名称（产品家族名称）',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '产品分类',
    `description` VARCHAR(2000) DEFAULT NULL COMMENT 'SPU 描述',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `brand` VARCHAR(100) DEFAULT NULL COMMENT '品牌名称',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_spu_code` (`code`),
    KEY `idx_spu_name` (`name`),
    KEY `idx_spu_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品 SPU 表（产品家族）';

-- ============================================================================
-- Step 2: 插入默认 SPU (Default SPU for Data Migration)
-- ============================================================================

-- 插入默认 SPU，用于容纳旧的 Product 数据
-- ID 固定为 1，确保数据迁移时可以引用
INSERT INTO `product_spu` (`id`, `code`, `name`, `category`, `description`, `enabled`)
VALUES (
    1,
    'SPU-DEFAULT-001',
    'Default SPU',
    'Default Category',
    'Default SPU for existing products during V3.3 migration. Please reassign products to proper SPU categories.',
    TRUE
)
ON DUPLICATE KEY UPDATE `id` = `id`;  -- 如果已存在则不重复插入

-- 重置自增 ID，确保后续插入的 SPU 从 2 开始
ALTER TABLE `product_spu` AUTO_INCREMENT = 2;

-- ============================================================================
-- Step 3: 修改 products 表 (Upgrade to SKU Level)
-- ============================================================================

-- 3.1 添加 spu_id 字段（外键关联到 product_spu）
ALTER TABLE `products`
ADD COLUMN `spu_id` BIGINT NULL COMMENT 'SPU ID（外键）' AFTER `id`;

-- 3.2 添加 sku_name 字段（SKU 特定名称）
ALTER TABLE `products`
ADD COLUMN `sku_name` VARCHAR(100) NULL COMMENT 'SKU 特定名称' AFTER `spu_id`;

-- 3.3 添加 specs 字段（规格描述）
ALTER TABLE `products`
ADD COLUMN `specs` VARCHAR(500) NULL COMMENT '规格描述（JSON 或文本）' AFTER `sku_name`;

-- 3.4 将现有所有 products 的 spu_id 更新为默认 SPU (ID=1)
UPDATE `products`
SET `spu_id` = 1
WHERE `spu_id` IS NULL;

-- 3.5 将 name 复制到 sku_name（如果 sku_name 为空）
UPDATE `products`
SET `sku_name` = `name`
WHERE `sku_name` IS NULL OR `sku_name` = '';

-- 3.6 将 spu_id 和 sku_name 设置为 NOT NULL（数据已迁移完成）
ALTER TABLE `products`
MODIFY COLUMN `spu_id` BIGINT NOT NULL COMMENT 'SPU ID（外键）';

ALTER TABLE `products`
MODIFY COLUMN `sku_name` VARCHAR(100) NOT NULL COMMENT 'SKU 特定名称';

-- 3.7 添加外键约束（关联到 product_spu）
ALTER TABLE `products`
ADD CONSTRAINT `fk_product_spu`
FOREIGN KEY (`spu_id`) REFERENCES `product_spu`(`id`)
ON DELETE RESTRICT ON UPDATE CASCADE;

-- 3.8 添加索引（优化查询性能）
ALTER TABLE `products`
ADD INDEX `idx_spu_id` (`spu_id`);

-- ============================================================================
-- Step 4: 修改 inventory_batch 表 (Multi-Location Batch Management)
-- ============================================================================

-- 4.1 添加 location_code 字段（库位编号字符串）
ALTER TABLE `inventory_batch`
ADD COLUMN `location_code` VARCHAR(50) NULL COMMENT '库位编号（如 A-1-101）' AFTER `batch_code`;

-- 4.2 将现有批次的 location_code 设置为默认值（如果有 location_id）
-- 假设 locations 表有 code 字段
UPDATE `inventory_batch` ib
INNER JOIN `locations` l ON ib.`location_id` = l.`id`
SET ib.`location_code` = l.`code`
WHERE ib.`location_code` IS NULL;

-- 4.3 如果没有 location，设置为默认值 "UNASSIGNED"
UPDATE `inventory_batch`
SET `location_code` = 'UNASSIGNED'
WHERE `location_code` IS NULL OR `location_code` = '';

-- 4.4 将 location_code 设置为 NOT NULL
ALTER TABLE `inventory_batch`
MODIFY COLUMN `location_code` VARCHAR(50) NOT NULL COMMENT '库位编号（如 A-1-101）';

-- 4.5 移除 batch_code 的唯一约束（允许同一批次在多个库位）
ALTER TABLE `inventory_batch`
DROP INDEX `idx_batch_code`;

-- 4.6 重新创建 batch_code 索引（非唯一）
ALTER TABLE `inventory_batch`
ADD INDEX `idx_batch_code` (`batch_code`);

-- 4.7 添加复合索引 (batch_code, location_code)
ALTER TABLE `inventory_batch`
ADD INDEX `idx_batch_location` (`batch_code`, `location_code`);

-- 4.8 添加 location_code 索引（优化库位查询）
ALTER TABLE `inventory_batch`
ADD INDEX `idx_location_code` (`location_code`);

-- ============================================================================
-- Step 5: 验证数据完整性 (Data Integrity Check)
-- ============================================================================

-- 5.1 检查所有 products 是否都有 spu_id
SELECT
    COUNT(*) AS total_products,
    SUM(CASE WHEN spu_id IS NULL THEN 1 ELSE 0 END) AS products_without_spu,
    SUM(CASE WHEN spu_id = 1 THEN 1 ELSE 0 END) AS products_with_default_spu
FROM `products`;

-- 5.2 检查所有 inventory_batch 是否都有 location_code
SELECT
    COUNT(*) AS total_batches,
    SUM(CASE WHEN location_code IS NULL THEN 1 ELSE 0 END) AS batches_without_location_code,
    SUM(CASE WHEN location_code = 'UNASSIGNED' THEN 1 ELSE 0 END) AS batches_unassigned
FROM `inventory_batch`;

-- 5.3 检查是否有重复的 (batch_code, location_code) 组合
SELECT
    batch_code,
    location_code,
    COUNT(*) AS duplicate_count
FROM `inventory_batch`
GROUP BY batch_code, location_code
HAVING COUNT(*) > 1;

-- ============================================================================
-- Step 6: 迁移完成提示 (Migration Summary)
-- ============================================================================

SELECT
    'V3.3 Migration Completed!' AS status,
    (SELECT COUNT(*) FROM product_spu) AS total_spus,
    (SELECT COUNT(*) FROM products) AS total_skus,
    (SELECT COUNT(*) FROM inventory_batch) AS total_batches;

-- ============================================================================
-- 回滚方案 (Rollback Script)
--
-- ⚠️ 警告：回滚将删除 V3.3 新增的所有数据和字段！
-- 请在执行回滚前务必备份数据库！
-- ============================================================================

/*
-- 回滚 Step 4: 恢复 inventory_batch 表
ALTER TABLE `inventory_batch` DROP INDEX `idx_location_code`;
ALTER TABLE `inventory_batch` DROP INDEX `idx_batch_location`;
ALTER TABLE `inventory_batch` DROP INDEX `idx_batch_code`;
ALTER TABLE `inventory_batch` ADD UNIQUE INDEX `idx_batch_code` (`batch_code`);
ALTER TABLE `inventory_batch` DROP COLUMN `location_code`;

-- 回滚 Step 3: 恢复 products 表
ALTER TABLE `products` DROP INDEX `idx_spu_id`;
ALTER TABLE `products` DROP FOREIGN KEY `fk_product_spu`;
ALTER TABLE `products` DROP COLUMN `specs`;
ALTER TABLE `products` DROP COLUMN `sku_name`;
ALTER TABLE `products` DROP COLUMN `spu_id`;

-- 回滚 Step 2 & 1: 删除 product_spu 表
DROP TABLE IF EXISTS `product_spu`;

SELECT 'V3.3 Migration Rolled Back!' AS status;
*/

-- ============================================================================
-- 迁移脚本结束
-- ============================================================================
