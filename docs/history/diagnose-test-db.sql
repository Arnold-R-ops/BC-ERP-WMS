-- =====================================================
-- PostgreSQL 测试环境诊断脚本
-- =====================================================
-- 用途：检查测试数据库是否已创建
-- 使用方法：在 psql 或 pgAdmin 中执行
-- =====================================================

-- 1. 检查测试数据库是否存在
SELECT datname AS "数据库名",
       pg_size_pretty(pg_database_size(datname)) AS "大小"
FROM pg_database
WHERE datname IN ('wms_db', 'wms_db_test')
ORDER BY datname;

-- 2. 如果测试数据库不存在，创建它
-- （如果上面的查询没有显示 wms_db_test，执行下面的语句）

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'wms_db_test') THEN
        EXECUTE 'CREATE DATABASE wms_db_test WITH OWNER = postgres ENCODING = ''UTF8''';
        RAISE NOTICE '✓ 测试数据库 wms_db_test 创建成功';
    ELSE
        RAISE NOTICE '✓ 测试数据库 wms_db_test 已存在';
    END IF;
END
$$;

-- 3. 验证连接权限
SELECT current_user AS "当前用户",
       current_database() AS "当前数据库";

-- 4. 显示当前连接信息
SELECT
    usename AS "用户名",
    client_addr AS "客户端地址",
    state AS "状态"
FROM pg_stat_activity
WHERE datname = 'wms_db_test';
