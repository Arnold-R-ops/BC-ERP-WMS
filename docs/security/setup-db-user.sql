-- ===================================================================
-- P0 安全修复：数据库账号治理
--
-- 目标：
--   1. 换掉已泄露的 postgres 密码（旧密码 123465 已进 git 历史，视同泄露）
--   2. 新建低权限应用账号 wms_app，应用不再使用 postgres 超级用户连库
--
-- 使用方法：用 postgres 超级用户连接后执行本脚本（psql 或 pgAdmin），
--          执行前把下面两处 <...> 占位符替换为你自己掌握的强密码。
--          执行后设置环境变量：DB_USERNAME=wms_app、DB_PASSWORD=<应用账号新密码>
-- ===================================================================

-- -------------------------------------------------------------------
-- Step 1: 轮换 postgres 超级用户密码（必做——旧密码已泄露）
-- -------------------------------------------------------------------
ALTER USER postgres WITH PASSWORD '<postgres超级用户的新强密码>';

-- -------------------------------------------------------------------
-- Step 2: 创建应用专用低权限账号
--   - 仅能登录，不是超级用户，不能建库建角色
-- -------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wms_app') THEN
        CREATE ROLE wms_app LOGIN PASSWORD '<wms_app应用账号的强密码>'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END $$;

-- -------------------------------------------------------------------
-- Step 3: 把业务库 wms_db 的所有权移交给 wms_app
--   说明：应用启动时 Flyway 需要执行 DDL（建表/加约束），
--        因此 wms_app 需要是库内对象的 owner——它仍然不是超级用户，
--        权限被限制在 wms_db 这一个库内。
-- -------------------------------------------------------------------
\connect wms_db

ALTER DATABASE wms_db OWNER TO wms_app;
ALTER SCHEMA public OWNER TO wms_app;

-- 移交现有表/序列/视图的 owner
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO wms_app', r.tablename);
    END LOOP;
    FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO wms_app', r.sequencename);
    END LOOP;
    FOR r IN SELECT viewname FROM pg_views WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO wms_app', r.viewname);
    END LOOP;
END $$;

-- -------------------------------------------------------------------
-- Step 4: 测试库同样处理（测试用 ddl-auto=create，需要 owner 权限）
-- -------------------------------------------------------------------
\connect wms_db_test

ALTER DATABASE wms_db_test OWNER TO wms_app;
ALTER SCHEMA public OWNER TO wms_app;

-- -------------------------------------------------------------------
-- 验证（执行后应看到 wms_app 为 owner，且 rolsuper = f）
-- -------------------------------------------------------------------
-- SELECT rolname, rolsuper FROM pg_roles WHERE rolname IN ('postgres', 'wms_app');
-- SELECT datname, pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname LIKE 'wms%';
