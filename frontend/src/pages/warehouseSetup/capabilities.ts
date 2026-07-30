export interface WarehouseSetupCapabilities {
  canManage: boolean;
}

const MANAGER_ROLES = new Set(['SUPER_ADMIN', 'WAREHOUSE_ADMIN']);

export function getWarehouseSetupCapabilities(role: string): WarehouseSetupCapabilities {
  return { canManage: MANAGER_ROLES.has(role) };
}
