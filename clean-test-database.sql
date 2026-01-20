-- ===================================================================
-- 清空 PostgreSQL 测试数据库脚本
-- 数据库: wms_db_test
-- 用途: 在运行 @DataJpaTest 测试前清空所有表和索引
-- ===================================================================

-- 1. 删除所有表（按照依赖顺序）
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_role_permission CASCADE;
DROP TABLE IF EXISTS sys_role_inherit CASCADE;
DROP TABLE IF EXISTS sys_permission CASCADE;
DROP TABLE IF EXISTS sys_role CASCADE;
DROP TABLE IF EXISTS stock_transactions CASCADE;
DROP TABLE IF EXISTS inventory_batch CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;
DROP TABLE IF EXISTS purchase_order_item CASCADE;
DROP TABLE IF EXISTS purchase_order CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS product_spu CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 2. 删除所有序列
DROP SEQUENCE IF EXISTS inventory_batch_seq CASCADE;
DROP SEQUENCE IF EXISTS inventory_seq CASCADE;
DROP SEQUENCE IF EXISTS locations_seq CASCADE;
DROP SEQUENCE IF EXISTS product_spu_seq CASCADE;
DROP SEQUENCE IF EXISTS products_seq CASCADE;
DROP SEQUENCE IF EXISTS purchase_order_item_seq CASCADE;
DROP SEQUENCE IF EXISTS purchase_order_seq CASCADE;
DROP SEQUENCE IF EXISTS stock_transactions_seq CASCADE;
DROP SEQUENCE IF EXISTS sys_permission_seq CASCADE;
DROP SEQUENCE IF EXISTS sys_role_inherit_seq CASCADE;
DROP SEQUENCE IF EXISTS sys_role_permission_seq CASCADE;
DROP SEQUENCE IF EXISTS sys_role_seq CASCADE;
DROP SEQUENCE IF EXISTS sys_user_role_seq CASCADE;
DROP SEQUENCE IF EXISTS users_seq CASCADE;

-- 3. 删除所有索引（如果单独创建的）
-- Hibernate 会自动创建的索引通常会随表删除，但以防万一
DROP INDEX IF EXISTS idx_location_id CASCADE;
DROP INDEX IF EXISTS idx_product_id CASCADE;
DROP INDEX IF EXISTS idx_username CASCADE;
DROP INDEX IF EXISTS idx_role CASCADE;
DROP INDEX IF EXISTS idx_enabled CASCADE;
DROP INDEX IF EXISTS idx_barcode CASCADE;
DROP INDEX IF EXISTS idx_location_code CASCADE;
DROP INDEX IF EXISTS idx_po_number CASCADE;
DROP INDEX IF EXISTS idx_transaction_date CASCADE;

-- 4. 清空完成提示
SELECT 'wms_db_test 数据库已清空，可以运行测试' AS status;
