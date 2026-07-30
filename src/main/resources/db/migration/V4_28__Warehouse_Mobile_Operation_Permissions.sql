-- P2-B: minimum RBAC wiring for the warehouse mobile operation surface.
-- This does not redesign IAM. It only creates the existing warehouse-operator
-- role and grants the API permissions required by receiving, picking and blind count.

INSERT INTO sys_role (
    company_id, role_code, role_name, description, role_type, status,
    sort_order, created_at, updated_at
)
SELECT
    1, 'WAREHOUSE_STAFF', '库管员',
    '执行移动收货、拣货和盲盘计数，不具备审批、复核或主数据维护权限',
    'SYSTEM', 'ACTIVE', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE company_id = 1 AND role_code = 'WAREHOUSE_STAFF'
);

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    menu_url, menu_icon, sort_order, status, description, created_at, updated_at
)
SELECT
    1, 'menu:warehouse-mobile', '仓库移动作业', 'MENU',
    '/mobile/picking', 'ScanOutlined', 35, 'ACTIVE',
    'Mobile receiving, picking and blind-count workspace',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE company_id = 1 AND permission_code = 'menu:warehouse-mobile'
);

INSERT INTO sys_permission (
    company_id, permission_code, permission_name, permission_type,
    resource_path, http_method, parent_id, sort_order, status,
    description, created_at, updated_at
)
SELECT
    1,
    permission_code,
    permission_name,
    'API',
    resource_path,
    http_method,
    (SELECT id FROM sys_permission WHERE company_id = 1 AND permission_code = 'menu:warehouse-mobile'),
    sort_order,
    'ACTIVE',
    description,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('inbound:list', '查看入库任务列表', '/api/inbound-orders/**', 'GET', 10, 'List inbound orders available for warehouse receipt'),
    ('inbound:view', '查看入库任务', '/api/inbound-orders/**', 'GET', 11, 'View inbound order details'),
    ('inbound:receive_goods', '执行仓库收货', '/api/inbound-orders/*/receive-goods', 'POST', 12, 'Receive confirmed inbound goods'),
    ('outbound:view', '查看出库任务', '/api/outbound-tasks/**', 'GET', 20, 'View outbound picking tasks'),
    ('outbound:pick', '执行出库拣货', '/api/outbound-tasks/*/confirm', 'POST', 21, 'Confirm one outbound picking task'),
    ('stocktake:view', '查看盘点任务', '/api/stocktake/tasks/**', 'GET', 30, 'View stocktake task headers'),
    ('stocktake:count', '执行盲盘计数', '/api/stocktake/tasks/**', 'POST', 31, 'Read blind count items, submit counts and finish counting')
) AS permissions(permission_code, permission_name, resource_path, http_method, sort_order, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission existing
    WHERE existing.company_id = 1
      AND existing.permission_code = permissions.permission_code
);

-- Keep historical permission rows active and align them under the new audit menu.
UPDATE sys_permission
SET parent_id = (
        SELECT id
        FROM sys_permission
        WHERE company_id = 1 AND permission_code = 'menu:warehouse-mobile'
    ),
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND permission_code IN (
      'inbound:list', 'inbound:view', 'inbound:receive_goods',
      'outbound:view', 'outbound:pick',
      'stocktake:view', 'stocktake:count'
  );

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = role.company_id
WHERE role.company_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF')
  AND permission.permission_code IN (
      'menu:warehouse-mobile',
      'inbound:list', 'inbound:view', 'inbound:receive_goods',
      'outbound:view', 'outbound:pick',
      'stocktake:view', 'stocktake:count'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = 1
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMENT ON COLUMN sys_role.role_code IS
    'Stable authorization role code; WAREHOUSE_STAFF is the restricted mobile warehouse operator.';
