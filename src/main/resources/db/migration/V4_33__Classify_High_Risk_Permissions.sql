-- Classify the currently confirmed high-risk business permissions.
--
-- HIGH permissions remain assignable to custom roles. They require an
-- explicit warning and operation reason in future copy/edit workflows.
-- CRITICAL and reserved permission protection remains owned by V4_32.

UPDATE sys_permission
SET risk_level = 'HIGH',
    custom_assignable = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'category:update',
    'category:delete',
    'global:view',
    'inbound:receive_goods',
    'integration:reconcile:repair',
    'outbound:pick',
    'product:status',
    'product-sku:status',
    'stocktake:count',
    'supplier:update',
    'supplier:delete'
);

COMMENT ON COLUMN sys_permission.risk_level IS
    'Permission risk classification: NORMAL, HIGH (warning/reason required), CRITICAL (not custom assignable)';
