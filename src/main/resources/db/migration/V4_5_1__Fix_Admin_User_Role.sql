-- V4.5.1: 修复 admin 用户角色缺失问题
-- 问题：admin 用户（id=2）在 sys_user_role 中没有角色绑定，导致登录时返回 USER_NO_ROLES (403)
-- 根因：初始化脚本重置了密码，但未同步绑定角色

-- 确保 SUPER_ADMIN 角色存在（幂等，如已存在则跳过）
INSERT INTO sys_role (role_code, role_name, role_type, status, created_at, updated_at)
VALUES ('SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ACTIVE', NOW(), NOW())
ON CONFLICT (role_code) DO NOTHING;

-- 将 admin 用户（id=2）绑定到 SUPER_ADMIN 角色（幂等，如已存在则跳过）
INSERT INTO sys_user_role (user_id, role_id, assigned_by, assigned_at)
SELECT
    2,
    r.id,
    2,
    NOW()
FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
