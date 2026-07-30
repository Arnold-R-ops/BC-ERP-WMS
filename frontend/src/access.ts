export type AppModule =
  | 'dashboard'
  | 'products'
  | 'masterData'
  | 'customers'
  | 'suppliers'
  | 'inventory'
  | 'sales'
  | 'analytics'
  | 'purchasing'
  | 'inbound'
  | 'outbound'
  | 'stocktake'
  | 'inventoryCorrection'
  | 'warehouseMobile'
  | 'warehouseSetup'
  | 'integrations'
  | 'iam';

const roleModules: Record<string, readonly AppModule[]> = {
  GENERAL_MANAGER: ['dashboard', 'products', 'masterData', 'customers', 'suppliers', 'inventory', 'sales', 'analytics', 'purchasing', 'inbound', 'outbound', 'stocktake', 'inventoryCorrection', 'warehouseSetup', 'integrations'],
  WAREHOUSE_ADMIN: ['dashboard', 'products', 'inventory', 'inbound', 'outbound', 'stocktake', 'inventoryCorrection', 'warehouseMobile', 'warehouseSetup'],
  WAREHOUSE_STAFF: ['dashboard', 'inventory', 'inbound', 'outbound', 'stocktake', 'warehouseMobile', 'warehouseSetup'],
  SALESPERSON: ['dashboard', 'products', 'masterData', 'customers', 'inventory', 'sales'],
  PURCHASER: ['dashboard', 'products', 'masterData', 'suppliers', 'inventory', 'purchasing', 'inbound'],
};

export function canAccessModule(role: string, module: AppModule): boolean {
  if (role === 'SUPER_ADMIN') {
    return true;
  }
  return roleModules[role]?.includes(module) ?? module === 'dashboard';
}
