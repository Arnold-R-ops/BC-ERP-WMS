-- =====================================================
-- V4.1.1 - 客户导出权限配置
-- =====================================================
-- 功能说明：
-- 1. 添加 customer:export 权限
-- 2. 仅授予 ADMIN 和 MANAGER 角色
-- 3. 禁止 SALES 角色导出客户数据
--
-- 业务场景：
-- - 保护客户数据，防止批量导出泄露
-- - 只有管理员和经理可以导出客户数据
-- - 销售员只能查看和管理自己的客户
--
-- @author WMS Team
-- @since 2026-02-12
-- @version 4.1 (Customer Data Security)
-- =====================================================

-- 1. 添加 customer:export 权限（如果不存在）
INSERT INTO sys_permissions (permission_code, permission_name, description, resource_type, created_at, updated_at)
SELECT 'customer:export', '导出客户数据', '允许导出客户数据到Excel', 'OPERATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permissions WHERE permission_code = 'customer:export'
);

-- 2. 为 SUPER_ADMIN 角色授予 customer:export 权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM sys_roles r, sys_permissions p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code = 'customer:export'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );

-- 3. 为 WAREHOUSE_ADMIN 角色授予 customer:export 权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM sys_roles r, sys_permissions p
WHERE r.role_code = 'WAREHOUSE_ADMIN'
  AND p.permission_code = 'customer:export'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );

-- 4. 为 CHAIRMAN 角色授予 customer:export 权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM sys_roles r, sys_permissions p
WHERE r.role_code = 'CHAIRMAN'
  AND p.permission_code = 'customer:export'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );

-- 5. 确保 SALESPERSON 角色没有 customer:export 权限
-- 如果之前误授予了该权限，则删除
DELETE FROM sys_role_permission
WHERE role_id IN (SELECT id FROM sys_roles WHERE role_code = 'SALESPERSON')
  AND permission_id IN (SELECT id FROM sys_permissions WHERE permission_code = 'customer:export');

-- 6. 确保 STAFF 角色没有 customer:export 权限
DELETE FROM sys_role_permission
WHERE role_id IN (SELECT id FROM sys_roles WHERE role_code = 'STAFF')
  AND permission_id IN (SELECT id FROM sys_permissions WHERE permission_code = 'customer:export');

-- =====================================================
-- 迁移完成
-- =====================================================
