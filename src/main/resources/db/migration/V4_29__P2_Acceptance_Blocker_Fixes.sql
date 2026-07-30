-- P2 acceptance blocker fixes.
-- Repair historical permission rows whose codes existed before V4_28, grant
-- analytics reads to GENERAL_MANAGER, and add explicit shipment void audit.

UPDATE sys_permission
SET resource_path = CASE permission_code
        WHEN 'inbound:list' THEN '/api/inbound-orders/**'
        WHEN 'inbound:view' THEN '/api/inbound-orders/**'
        WHEN 'inbound:receive_goods' THEN '/api/inbound-orders/*/receive-goods'
        WHEN 'outbound:view' THEN '/api/outbound-tasks/**'
        WHEN 'outbound:pick' THEN '/api/outbound-tasks/*/confirm'
        WHEN 'stocktake:view' THEN '/api/stocktake/tasks/**'
        WHEN 'stocktake:count' THEN '/api/stocktake/tasks/**'
        ELSE resource_path
    END,
    http_method = CASE permission_code
        WHEN 'inbound:list' THEN 'GET'
        WHEN 'inbound:view' THEN 'GET'
        WHEN 'inbound:receive_goods' THEN 'POST'
        WHEN 'outbound:view' THEN 'GET'
        WHEN 'outbound:pick' THEN 'POST'
        WHEN 'stocktake:view' THEN 'GET'
        WHEN 'stocktake:count' THEN 'POST'
        ELSE http_method
    END,
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
      WHERE existing.company_id = role.company_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

UPDATE sys_permission
SET resource_path = '/api/reports/**',
    http_method = 'GET',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND permission_code = 'global:view';

INSERT INTO sys_role_permission (company_id, role_id, permission_id, granted_at)
SELECT 1, role.id, permission.id, CURRENT_TIMESTAMP
FROM sys_role role
JOIN sys_permission permission ON permission.company_id = role.company_id
WHERE role.company_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'GENERAL_MANAGER')
  AND permission.permission_code IN ('menu:reports', 'global:view')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.company_id = role.company_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

ALTER TABLE sales_order_shipments
    ADD COLUMN IF NOT EXISTS voided_by BIGINT,
    ADD COLUMN IF NOT EXISTS voided_by_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;

UPDATE sales_order_shipments
SET voided_by = COALESCE(voided_by, created_by),
    voided_by_name = COALESCE(voided_by_name, created_by_name),
    voided_at = COALESCE(voided_at, updated_at)
WHERE status = 'VOIDED'
  AND voided_at IS NULL;

COMMENT ON COLUMN sales_order_shipments.voided_by IS 'User ID that voided the shipment.';
COMMENT ON COLUMN sales_order_shipments.voided_by_name IS 'Username snapshot for the shipment void action.';
COMMENT ON COLUMN sales_order_shipments.voided_at IS 'Timestamp of the shipment void action.';
