-- ===================================================================
-- PostgreSQL 测试数据库创建脚本
-- ===================================================================
-- 用途：为集成测试创建独立的测试数据库
-- 使用方法：使用 psql 或 pgAdmin 执行此脚本
-- ===================================================================

-- 如果测试数据库已存在，先删除（⚠️ 谨慎操作）
DROP DATABASE IF EXISTS wms_db_test;

-- 创建测试数据库
CREATE DATABASE wms_db_test
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'Chinese (Simplified)_China.936'
    LC_CTYPE = 'Chinese (Simplified)_China.936'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- 切换到测试数据库
\c wms_db_test

-- 为测试数据库创建扩展（可选）
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- UUID 生成支持

-- 授予权限
GRANT ALL PRIVILEGES ON DATABASE wms_db_test TO postgres;

-- 显示提示信息
SELECT '✓ 测试数据库 wms_db_test 创建成功！' AS message;
