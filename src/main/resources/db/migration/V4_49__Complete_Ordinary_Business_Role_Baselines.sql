-- Complete the provisional GENERAL_MANAGER and SALESPERSON business templates.
--
-- This migration intentionally keeps WAREHOUSE_STAFF (8) and
-- WAREHOUSE_ADMIN (21) unchanged. Privileged roles remain non-importable.
-- Permission changes are exact baselines and invalidate affected users' JWTs.

CREATE TEMP TABLE _ordinary_permission_catalogue (
    permission_code VARCHAR(100) PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL,
    resource_path VARCHAR(200) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    custom_assignable BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL
) ON COMMIT DROP;

INSERT INTO _ordinary_permission_catalogue (
    permission_code, permission_name, resource_path, http_method,
    risk_level, custom_assignable, sort_order, description
)
VALUES
    ('customer:create', '创建客户', '/api/customers', 'POST', 'NORMAL', TRUE, 110, 'Create client or consumer master data'),
    ('customer:view', '查看客户', '/api/customers/**', 'GET', 'NORMAL', TRUE, 111, 'View client and consumer master data'),
    ('customer:edit', '编辑客户', '/api/customers/*', 'PUT', 'NORMAL', TRUE, 112, 'Edit client or consumer master data'),
    ('customer:delete', '删除客户', '/api/customers/*', 'DELETE', 'HIGH', TRUE, 113, 'Logically delete client or consumer master data'),
    ('inventory:view', '查看库存', '/api/inventory/**', 'GET', 'NORMAL', TRUE, 120, 'View inventory summaries, details and batches'),
    ('inventory:correction:view', '查看库存校正', '/api/emergency-stock-corrections/**', 'GET', 'NORMAL', TRUE, 121, 'View emergency stock correction requests'),
    ('inventory:correction:review', '复核库存校正', '/api/emergency-stock-corrections/*/review', 'POST', 'HIGH', TRUE, 122, 'Review an emergency stock correction request'),
    ('inventory:correction:approve', '批准库存校正', '/api/emergency-stock-corrections/*/approve', 'POST', 'HIGH', TRUE, 123, 'Approve an emergency stock correction request'),
    ('inventory:correction:reject', '驳回库存校正', '/api/emergency-stock-corrections/*/reject', 'POST', 'HIGH', TRUE, 124, 'Reject an emergency stock correction request'),
    ('sales:create', '创建销售订单', '/api/sales-orders/**', 'POST', 'NORMAL', TRUE, 130, 'Create sales orders, import lines and calculate batch options'),
    ('sales:view', '查看销售订单', '/api/sales-orders/**', 'GET', 'NORMAL', TRUE, 131, 'View sales orders and shipment records'),
    ('sales:edit', '编辑销售订单', '/api/sales-orders/*', 'PUT', 'NORMAL', TRUE, 132, 'Edit an eligible sales order'),
    ('sales:shipment:edit', '维护销售运单', '/api/sales-orders/*/shipments/**', '*', 'NORMAL', TRUE, 133, 'Create, edit or void internal shipment records'),
    ('sales:approve', '批准销售订单', '/api/sales-orders/*/approve', 'POST', 'HIGH', TRUE, 134, 'Approve a pending sales order'),
    ('sales:reject', '驳回销售订单', '/api/sales-orders/*/reject', 'POST', 'HIGH', TRUE, 135, 'Reject a pending sales order'),
    ('sales:cancel', '取消销售订单', '/api/sales-orders/*/cancel', 'POST', 'HIGH', TRUE, 136, 'Cancel an eligible business sales order'),
    ('sales:void', '作废销售订单', '/api/sales-orders/*/void', 'POST', 'CRITICAL', FALSE, 137, 'Privileged administrative sales-order void operation'),
    ('purchase:view', '查看采购订单', '/api/purchase-orders/**', 'GET', 'NORMAL', TRUE, 140, 'View purchase orders without changing their state'),
    ('purchase:create', '创建采购订单', '/api/purchase-orders/**', 'POST', 'NORMAL', TRUE, 141, 'Create or import a purchase order'),
    ('purchase:update', '编辑采购订单', '/api/purchase-orders/*', 'PUT', 'NORMAL', TRUE, 142, 'Edit a purchase order while it remains in ordering'),
    ('purchase:confirm', '确认采购在途', '/api/purchase-orders/*/confirm', 'PUT', 'HIGH', TRUE, 143, 'Confirm purchase ASN and generate batch codes'),
    ('purchase:receive', '采购收货', '/api/purchase-orders/*/receive', 'PUT', 'HIGH', TRUE, 144, 'Receive purchase-order goods into inventory'),
    ('purchase:rollback', '回退采购订单', '/api/purchase-orders/*/rollback', 'PUT', 'HIGH', TRUE, 145, 'Roll an in-transit purchase order back to ordering'),
    ('inbound:create', '创建入库计划', '/api/inbound-orders', 'POST', 'NORMAL', TRUE, 149, 'Create an inbound plan'),
    ('inbound:approve_plan', '审批入库方案', '/api/inbound-orders/*/approve-plan', 'POST', 'HIGH', TRUE, 150, 'Approve an inbound plan'),
    ('inbound:reject', '驳回入库方案', '/api/inbound-orders/*/reject', 'POST', 'HIGH', TRUE, 151, 'Reject an inbound plan'),
    ('inbound:confirm_order', '确认入库订单', '/api/inbound-orders/*/confirm-order', 'POST', 'HIGH', TRUE, 152, 'Confirm approved inbound order quantities and batches'),
    ('stocktake:create', '创建盘点任务', '/api/stocktake/tasks', 'POST', 'HIGH', TRUE, 160, 'Create a stocktake task'),
    ('stocktake:review', '复核盘点结果', '/api/stocktake/tasks/*/review*', '*', 'HIGH', TRUE, 161, 'View differences and review a completed stocktake count'),
    ('warehouse:view', '查看仓库', '/api/warehouses/**', 'GET', 'NORMAL', TRUE, 170, 'View warehouse configuration'),
    ('location:view', '查看库位', '/api/locations/**', 'GET', 'NORMAL', TRUE, 171, 'View warehouse locations'),
    ('warehouse:manage', '维护仓库', '/api/warehouses/**', '*', 'HIGH', TRUE, 172, 'Create, edit, activate or deactivate warehouses'),
    ('location:manage', '维护库位', '/api/locations/**', '*', 'HIGH', TRUE, 173, 'Create, edit, enable or disable warehouse locations');

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, data_scope, risk_level, custom_assignable,
    description, status, sort_order, created_at, updated_at
)
SELECT
    1, seed.permission_code, seed.permission_name, 'API',
    seed.resource_path, seed.http_method, 'ALL', seed.risk_level,
    seed.custom_assignable, seed.description, 'ACTIVE', seed.sort_order,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM _ordinary_permission_catalogue seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = seed.permission_code
);

-- Converge legacy permission rows whose paths or risk metadata predate the
-- current controllers. This avoids a displayed permission that cannot reach
-- the endpoint it claims to authorize.
UPDATE sys_permission permission
SET permission_name = seed.permission_name,
    permission_type = 'API',
    resource_path = seed.resource_path,
    http_method = seed.http_method,
    data_scope = 'ALL',
    risk_level = seed.risk_level,
    custom_assignable = seed.custom_assignable,
    description = seed.description,
    status = 'ACTIVE',
    sort_order = seed.sort_order,
    updated_at = CURRENT_TIMESTAMP
FROM _ordinary_permission_catalogue seed
WHERE permission.company_id = 1
  AND permission.permission_code = seed.permission_code;

CREATE TEMP TABLE _ordinary_menu_catalogue (
    permission_code VARCHAR(100) PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL,
    menu_url VARCHAR(200) NOT NULL,
    menu_icon VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL
) ON COMMIT DROP;

INSERT INTO _ordinary_menu_catalogue (
    permission_code, permission_name, menu_url, menu_icon, sort_order, description
)
VALUES
    ('menu:customers', '客户管理', '/master-data/clients', 'TeamOutlined', 31, 'Client and consumer workspace'),
    ('menu:suppliers', '供应商管理', '/master-data/suppliers', 'TruckOutlined', 32, 'Supplier master-data workspace'),
    ('menu:inventory', '库存查询', '/inventory', 'DatabaseOutlined', 33, 'Inventory query workspace'),
    ('menu:sales', '销售订单', '/sales', 'ShoppingCartOutlined', 34, 'Sales-order workspace'),
    ('menu:purchase', '采购管理', '/purchasing', 'TruckOutlined', 35, 'Purchase-order workspace'),
    ('menu:inbound', '入库管理', '/inbound', 'InboxOutlined', 36, 'Inbound-order workspace'),
    ('menu:outbound', '出库作业', '/outbound', 'ExportOutlined', 37, 'Outbound-task workspace'),
    ('menu:stocktake', '盘点治理', '/inventory-governance/stocktake', 'AuditOutlined', 38, 'Stocktake governance workspace'),
    ('menu:inventory-correction', '库存校正', '/inventory-governance/corrections', 'ThunderboltOutlined', 39, 'Emergency stock correction workspace'),
    ('menu:warehouse-setup', '仓储配置', '/warehouse-setup/warehouses', 'HomeOutlined', 40, 'Read-only warehouse and location setup workspace'),
    ('menu:integration-reconciliation', '渠道对账', '/integrations/reconciliation', 'SafetyCertificateOutlined', 41, 'Read-only channel reconciliation workspace');

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    menu_url, menu_icon, data_scope, risk_level, custom_assignable,
    description, status, sort_order, created_at, updated_at
)
SELECT
    1, seed.permission_code, seed.permission_name, 'MENU',
    seed.menu_url, seed.menu_icon, 'ALL', 'NORMAL', TRUE,
    seed.description, 'ACTIVE', seed.sort_order,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM _ordinary_menu_catalogue seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = seed.permission_code
);

UPDATE sys_permission permission
SET permission_name = seed.permission_name,
    permission_type = 'MENU',
    resource_path = NULL,
    http_method = NULL,
    menu_url = seed.menu_url,
    menu_icon = seed.menu_icon,
    data_scope = 'ALL',
    risk_level = 'NORMAL',
    custom_assignable = TRUE,
    description = seed.description,
    status = 'ACTIVE',
    sort_order = seed.sort_order,
    updated_at = CURRENT_TIMESTAMP
FROM _ordinary_menu_catalogue seed
WHERE permission.company_id = 1
  AND permission.permission_code = seed.permission_code;

CREATE TEMP TABLE _ordinary_role_permission (
    role_code VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
) ON COMMIT DROP;

INSERT INTO _ordinary_role_permission (role_code, permission_code)
VALUES
    ('GENERAL_MANAGER', 'menu:product-catalog'),
    ('GENERAL_MANAGER', 'menu:customers'),
    ('GENERAL_MANAGER', 'menu:suppliers'),
    ('GENERAL_MANAGER', 'menu:inventory'),
    ('GENERAL_MANAGER', 'menu:sales'),
    ('GENERAL_MANAGER', 'menu:purchase'),
    ('GENERAL_MANAGER', 'menu:inbound'),
    ('GENERAL_MANAGER', 'menu:outbound'),
    ('GENERAL_MANAGER', 'menu:stocktake'),
    ('GENERAL_MANAGER', 'menu:inventory-correction'),
    ('GENERAL_MANAGER', 'menu:warehouse-setup'),
    ('GENERAL_MANAGER', 'menu:integration-reconciliation'),
    ('GENERAL_MANAGER', 'category:view'),
    ('GENERAL_MANAGER', 'product:view'),
    ('GENERAL_MANAGER', 'product-sku:view'),
    ('GENERAL_MANAGER', 'customer:view'),
    ('GENERAL_MANAGER', 'supplier:view'),
    ('GENERAL_MANAGER', 'inventory:view'),
    ('GENERAL_MANAGER', 'sales:view'),
    ('GENERAL_MANAGER', 'sales:approve'),
    ('GENERAL_MANAGER', 'sales:reject'),
    ('GENERAL_MANAGER', 'sales:cancel'),
    ('GENERAL_MANAGER', 'purchase:view'),
    ('GENERAL_MANAGER', 'inbound:list'),
    ('GENERAL_MANAGER', 'inbound:view'),
    ('GENERAL_MANAGER', 'inbound:approve_plan'),
    ('GENERAL_MANAGER', 'inbound:reject'),
    ('GENERAL_MANAGER', 'outbound:view'),
    ('GENERAL_MANAGER', 'stocktake:view'),
    ('GENERAL_MANAGER', 'stocktake:create'),
    ('GENERAL_MANAGER', 'stocktake:review'),
    ('GENERAL_MANAGER', 'inventory:correction:view'),
    ('GENERAL_MANAGER', 'inventory:correction:review'),
    ('GENERAL_MANAGER', 'inventory:correction:approve'),
    ('GENERAL_MANAGER', 'inventory:correction:reject'),
    ('GENERAL_MANAGER', 'warehouse:view'),
    ('GENERAL_MANAGER', 'location:view'),
    ('GENERAL_MANAGER', 'menu:reports'),
    ('GENERAL_MANAGER', 'global:view'),
    ('GENERAL_MANAGER', 'integration:reconcile:view'),

    ('SALESPERSON', 'menu:product-catalog'),
    ('SALESPERSON', 'menu:customers'),
    ('SALESPERSON', 'menu:inventory'),
    ('SALESPERSON', 'menu:sales'),
    ('SALESPERSON', 'category:view'),
    ('SALESPERSON', 'product:view'),
    ('SALESPERSON', 'product-sku:view'),
    ('SALESPERSON', 'customer:create'),
    ('SALESPERSON', 'customer:view'),
    ('SALESPERSON', 'customer:edit'),
    ('SALESPERSON', 'inventory:view'),
    ('SALESPERSON', 'sales:create'),
    ('SALESPERSON', 'sales:view'),
    ('SALESPERSON', 'sales:edit'),
    ('SALESPERSON', 'sales:shipment:edit');

DO $$
DECLARE
    missing_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO missing_count
    FROM _ordinary_role_permission baseline
    LEFT JOIN sys_role role
      ON role.company_id = 1
     AND role.role_code = baseline.role_code
     AND role.role_type = 'SYSTEM'
     AND role.system_category = 'BUSINESS_TEMPLATE'
    LEFT JOIN sys_permission permission
      ON permission.company_id = 1
     AND permission.permission_code = baseline.permission_code
     AND permission.status = 'ACTIVE'
    WHERE role.id IS NULL OR permission.id IS NULL;

    IF missing_count > 0 THEN
        RAISE EXCEPTION 'Cannot complete ordinary business templates: % required role/permission rows are missing', missing_count;
    END IF;
END $$;

CREATE TEMP TABLE _affected_ordinary_template_user (
    user_id BIGINT PRIMARY KEY
) ON COMMIT DROP;

WITH RECURSIVE affected_roles AS (
    SELECT id
    FROM sys_role
    WHERE company_id = 1
      AND role_code IN ('GENERAL_MANAGER', 'SALESPERSON')
    UNION
    SELECT inherit.child_role_id
    FROM sys_role_inherit inherit
    JOIN affected_roles parent ON parent.id = inherit.parent_role_id
)
INSERT INTO _affected_ordinary_template_user (user_id)
SELECT DISTINCT assignment.user_id
FROM sys_user_role assignment
JOIN affected_roles role ON role.id = assignment.role_id;

DELETE FROM sys_role_permission assignment
USING sys_role role, sys_permission permission
WHERE assignment.role_id = role.id
  AND assignment.permission_id = permission.id
  AND role.company_id = 1
  AND role.role_code IN ('GENERAL_MANAGER', 'SALESPERSON')
  AND NOT EXISTS (
      SELECT 1
      FROM _ordinary_role_permission baseline
      WHERE baseline.role_code = role.role_code
        AND baseline.permission_code = permission.permission_code
  );

INSERT INTO sys_role_permission (
    company_id, role_id, permission_id, granted_at
)
SELECT
    role.company_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM _ordinary_role_permission baseline
JOIN sys_role role
  ON role.company_id = 1
 AND role.role_code = baseline.role_code
JOIN sys_permission permission
  ON permission.company_id = role.company_id
 AND permission.permission_code = baseline.permission_code
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission existing
    WHERE existing.company_id = role.company_id
      AND existing.role_id = role.id
      AND existing.permission_id = permission.id
);

UPDATE sys_role
SET import_allowed = TRUE,
    description = CASE role_code
        WHEN 'GENERAL_MANAGER' THEN '公司业务只读、经营分析及受控业务审批；不执行仓库、库存或特权管理操作'
        WHEN 'SALESPERSON' THEN '客户与销售订单日常维护；不具备审批、取消、作废、库存或特权管理权限'
        ELSE description
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND role_code IN ('GENERAL_MANAGER', 'SALESPERSON')
  AND role_type = 'SYSTEM'
  AND system_category = 'BUSINESS_TEMPLATE';

UPDATE users
SET security_version = COALESCE(security_version, 0) + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT user_id FROM _affected_ordinary_template_user);
