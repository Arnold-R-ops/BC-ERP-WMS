-- =====================================================
-- BC ERP-WMS 动态 RBAC 权限系统 - 数据库架构
-- Version: 2.0
-- Date: 2026-01-18
-- Description: 创建5张RBAC核心表及初始化数据
-- =====================================================

-- =====================================================
-- 表 1: sys_role（角色表）
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    role_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',  -- SYSTEM=系统预设, CUSTOM=自定义
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE=启用, DISABLED=禁用
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_role_type_status ON sys_role(role_type, status);
CREATE INDEX IF NOT EXISTS idx_role_code ON sys_role(role_code);

-- 添加注释
COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.role_code IS '角色编码（唯一标识）';
COMMENT ON COLUMN sys_role.role_name IS '角色名称（显示名称）';
COMMENT ON COLUMN sys_role.role_type IS '角色类型：SYSTEM=系统预设，CUSTOM=自定义';
COMMENT ON COLUMN sys_role.status IS '角色状态：ACTIVE=启用，DISABLED=禁用';

-- =====================================================
-- 表 2: sys_permission（权限/资源表）
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL,              -- MENU=菜单, BUTTON=按钮, API=接口
    parent_id BIGINT,

    -- API 权限字段
    resource_path VARCHAR(200),                        -- 资源路径: /api/inventory/**
    http_method VARCHAR(10),                           -- HTTP方法: GET, POST, PUT, DELETE, *

    -- 菜单权限字段
    menu_url VARCHAR(200),                             -- 菜单URL: /inventory
    menu_icon VARCHAR(50),                             -- 菜单图标: icon-inventory

    -- 数据权限字段（预留）
    data_scope VARCHAR(20) DEFAULT 'ALL',              -- ALL=全部, DEPT=本部门, SELF=仅本人

    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_permission_type_status ON sys_permission(permission_type, status);
CREATE INDEX IF NOT EXISTS idx_permission_resource ON sys_permission(resource_path, http_method);
CREATE INDEX IF NOT EXISTS idx_permission_parent ON sys_permission(parent_id);
CREATE INDEX IF NOT EXISTS idx_permission_code ON sys_permission(permission_code);

-- 添加注释
COMMENT ON TABLE sys_permission IS '系统权限/资源表（统一管理菜单、按钮、API权限）';
COMMENT ON COLUMN sys_permission.permission_type IS '权限类型：MENU=菜单，BUTTON=按钮，API=接口';
COMMENT ON COLUMN sys_permission.resource_path IS 'API资源路径（支持Ant匹配）';
COMMENT ON COLUMN sys_permission.http_method IS 'HTTP方法：GET, POST, PUT, DELETE, *（通配符）';
COMMENT ON COLUMN sys_permission.data_scope IS '数据范围：ALL=全部，DEPT=本部门，SELF=仅本人';

-- =====================================================
-- 表 3: sys_user_role（用户角色关联表）
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT,                                -- 分配人ID

    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON sys_user_role(role_id);

-- 添加注释
COMMENT ON TABLE sys_user_role IS '用户角色关联表（多对多）';
COMMENT ON COLUMN sys_user_role.assigned_by IS '分配角色的管理员ID';

-- =====================================================
-- 表 4: sys_role_permission（角色权限关联表）
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT,                                 -- 授权人ID

    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_role_permission_role ON sys_role_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permission_permission ON sys_role_permission(permission_id);

-- 添加注释
COMMENT ON TABLE sys_role_permission IS '角色权限关联表（多对多）';
COMMENT ON COLUMN sys_role_permission.granted_by IS '授权操作的管理员ID';

-- =====================================================
-- 表 5: sys_role_inherit（角色继承表）⭐
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role_inherit (
    id BIGSERIAL PRIMARY KEY,
    child_role_id BIGINT NOT NULL,                     -- 子角色ID（继承者，如董事长）
    parent_role_id BIGINT NOT NULL,                    -- 父角色ID（被继承者，如仓库管理员）
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_role_inherit_child FOREIGN KEY (child_role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_inherit_parent FOREIGN KEY (parent_role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_inherit UNIQUE (child_role_id, parent_role_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_role_inherit_child ON sys_role_inherit(child_role_id);
CREATE INDEX IF NOT EXISTS idx_role_inherit_parent ON sys_role_inherit(parent_role_id);

-- 添加注释
COMMENT ON TABLE sys_role_inherit IS '角色继承关系表（支持董事长等复合角色）';
COMMENT ON COLUMN sys_role_inherit.child_role_id IS '子角色（继承者）';
COMMENT ON COLUMN sys_role_inherit.parent_role_id IS '父角色（被继承者）';

-- =====================================================
-- 初始化系统角色数据
-- =====================================================
INSERT INTO sys_role (role_code, role_name, description, role_type, sort_order) VALUES
('SUPER_ADMIN', '超级管理员', '系统最高权限，拥有所有功能的完全控制权', 'SYSTEM', 1),
('CHAIRMAN', '董事长', '公司最高管理者，自动继承仓库管理员、采购员、销售员的所有权限，并拥有全局数据查看和高级审批权限', 'SYSTEM', 2),
('WAREHOUSE_ADMIN', '仓库管理员', '负责库存管理、入库出库、库存调拨等仓储业务', 'SYSTEM', 3),
('BUYER', '采购员', '负责采购订单管理、供应商管理、采购入库等采购业务', 'SYSTEM', 4),
('SELLER', '销售员', '负责销售订单管理、客户管理、销售出库等销售业务', 'SYSTEM', 5)
ON CONFLICT (role_code) DO NOTHING;

-- =====================================================
-- 初始化董事长角色继承关系
-- =====================================================
INSERT INTO sys_role_inherit (child_role_id, parent_role_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN'),
    (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN')
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_inherit
    WHERE child_role_id = (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN')
      AND parent_role_id = (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN')
);

INSERT INTO sys_role_inherit (child_role_id, parent_role_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN'),
    (SELECT id FROM sys_role WHERE role_code = 'BUYER')
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_inherit
    WHERE child_role_id = (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN')
      AND parent_role_id = (SELECT id FROM sys_role WHERE role_code = 'BUYER')
);

INSERT INTO sys_role_inherit (child_role_id, parent_role_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN'),
    (SELECT id FROM sys_role WHERE role_code = 'SELLER')
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_inherit
    WHERE child_role_id = (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN')
      AND parent_role_id = (SELECT id FROM sys_role WHERE role_code = 'SELLER')
);

-- =====================================================
-- 初始化系统权限数据（示例）
-- =====================================================

-- 菜单权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, menu_url, menu_icon, sort_order) VALUES
('menu:inventory', '库存管理', 'MENU', '/inventory', 'icon-inventory', 1),
('menu:purchase', '采购管理', 'MENU', '/purchase', 'icon-purchase', 2),
('menu:sales', '销售管理', 'MENU', '/sales', 'icon-sales', 3),
('menu:reports', '报表中心', 'MENU', '/reports', 'icon-reports', 4),
('menu:system', '系统管理', 'MENU', '/system', 'icon-system', 5)
ON CONFLICT (permission_code) DO NOTHING;

-- 库存管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, parent_id, sort_order) VALUES
('inventory:view', '查看库存', 'API', '/api/inventory/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:inventory'), 1),
('inventory:create', '创建库存', 'API', '/api/inventory', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:inventory'), 2),
('inventory:update', '更新库存', 'API', '/api/inventory/*', 'PUT', (SELECT id FROM sys_permission WHERE permission_code = 'menu:inventory'), 3),
('inventory:delete', '删除库存', 'API', '/api/inventory/*', 'DELETE', (SELECT id FROM sys_permission WHERE permission_code = 'menu:inventory'), 4),
('inventory:transfer', '库存调拨', 'API', '/api/inventory/transfer', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:inventory'), 5)
ON CONFLICT (permission_code) DO NOTHING;

-- 采购管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, parent_id, sort_order) VALUES
('purchase:view', '查看采购', 'API', '/api/purchase/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:purchase'), 1),
('purchase:create', '创建采购订单', 'API', '/api/purchase', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:purchase'), 2),
('purchase:update', '更新采购订单', 'API', '/api/purchase/*', 'PUT', (SELECT id FROM sys_permission WHERE permission_code = 'menu:purchase'), 3),
('purchase:delete', '删除采购订单', 'API', '/api/purchase/*', 'DELETE', (SELECT id FROM sys_permission WHERE permission_code = 'menu:purchase'), 4),
('purchase:approve', '审批采购订单', 'API', '/api/purchase/*/approve', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:purchase'), 5)
ON CONFLICT (permission_code) DO NOTHING;

-- 销售管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, parent_id, sort_order) VALUES
('sales:view', '查看销售', 'API', '/api/sales/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:sales'), 1),
('sales:create', '创建销售订单', 'API', '/api/sales', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:sales'), 2),
('sales:update', '更新销售订单', 'API', '/api/sales/*', 'PUT', (SELECT id FROM sys_permission WHERE permission_code = 'menu:sales'), 3),
('sales:delete', '删除销售订单', 'API', '/api/sales/*', 'DELETE', (SELECT id FROM sys_permission WHERE permission_code = 'menu:sales'), 4),
('sales:approve', '审批销售订单', 'API', '/api/sales/*/approve', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:sales'), 5)
ON CONFLICT (permission_code) DO NOTHING;

-- 董事长专属权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, parent_id, sort_order) VALUES
('global:view', '全局数据查看', 'API', '/api/reports/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:reports'), 1),
('approval:high', '高级审批权限', 'API', '/api/approval/high', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 2),
('global:export', '全局数据导出', 'API', '/api/reports/export/**', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:reports'), 3)
ON CONFLICT (permission_code) DO NOTHING;

-- 系统管理 API 权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, http_method, parent_id, sort_order) VALUES
('system:role:view', '查看角色', 'API', '/api/system/roles/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 1),
('system:role:create', '创建角色', 'API', '/api/system/roles', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 2),
('system:role:update', '更新角色', 'API', '/api/system/roles/*', 'PUT', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 3),
('system:role:delete', '删除角色', 'API', '/api/system/roles/*', 'DELETE', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 4),
('system:permission:view', '查看权限', 'API', '/api/system/permissions/**', 'GET', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 5),
('system:permission:create', '创建权限', 'API', '/api/system/permissions', 'POST', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 6),
('system:permission:update', '更新权限', 'API', '/api/system/permissions/*', 'PUT', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 7),
('system:permission:delete', '删除权限', 'API', '/api/system/permissions/*', 'DELETE', (SELECT id FROM sys_permission WHERE permission_code = 'menu:system'), 8)
ON CONFLICT (permission_code) DO NOTHING;

-- =====================================================
-- 为系统预设角色分配权限
-- =====================================================

-- 仓库管理员权限分配
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'),
    p.id
FROM sys_permission p
WHERE p.permission_code IN (
    'menu:inventory',
    'inventory:view',
    'inventory:create',
    'inventory:update',
    'inventory:delete',
    'inventory:transfer'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 采购员权限分配
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'BUYER'),
    p.id
FROM sys_permission p
WHERE p.permission_code IN (
    'menu:purchase',
    'purchase:view',
    'purchase:create',
    'purchase:update',
    'purchase:delete',
    'purchase:approve'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 销售员权限分配
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'SELLER'),
    p.id
FROM sys_permission p
WHERE p.permission_code IN (
    'menu:sales',
    'sales:view',
    'sales:create',
    'sales:update',
    'sales:delete',
    'sales:approve'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 董事长专属权限分配（除继承权限外的额外权限）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'CHAIRMAN'),
    p.id
FROM sys_permission p
WHERE p.permission_code IN (
    'menu:reports',
    'global:view',
    'approval:high',
    'global:export'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 超级管理员拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM sys_role WHERE role_code = 'SUPER_ADMIN'),
    p.id
FROM sys_permission p
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =====================================================
-- 数据迁移：将现有用户角色迁移到新表
-- =====================================================

-- 将现有用户的 role 字段迁移到 sys_user_role 表
INSERT INTO sys_user_role (user_id, role_id, assigned_at)
SELECT
    u.id,
    r.id,
    NOW()
FROM users u
INNER JOIN sys_role r ON UPPER(r.role_code) = UPPER(u.role)
WHERE u.role IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role sur
      WHERE sur.user_id = u.id AND sur.role_id = r.id
  );

-- =====================================================
-- 完成标记
-- =====================================================
-- 添加版本控制注释
COMMENT ON TABLE sys_role IS 'BC ERP-WMS RBAC 系统 - 角色表 v2.0';
COMMENT ON TABLE sys_permission IS 'BC ERP-WMS RBAC 系统 - 权限表 v2.0';
COMMENT ON TABLE sys_user_role IS 'BC ERP-WMS RBAC 系统 - 用户角色关联表 v2.0';
COMMENT ON TABLE sys_role_permission IS 'BC ERP-WMS RBAC 系统 - 角色权限关联表 v2.0';
COMMENT ON TABLE sys_role_inherit IS 'BC ERP-WMS RBAC 系统 - 角色继承表 v2.0';
