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
  | 'integrationAdmin'
  | 'reconciliation'
  | 'iam'
  | 'security';

const modulePermissions: Record<Exclude<AppModule, 'dashboard'>, readonly string[]> = {
  products: ['menu:product-catalog'],
  masterData: ['menu:customers', 'menu:suppliers'],
  customers: ['menu:customers'],
  suppliers: ['menu:suppliers'],
  inventory: ['menu:inventory'],
  sales: ['menu:sales'],
  analytics: ['menu:reports'],
  purchasing: ['menu:purchase'],
  inbound: ['menu:inbound'],
  outbound: ['menu:outbound'],
  stocktake: ['menu:stocktake'],
  inventoryCorrection: ['menu:inventory-correction'],
  warehouseMobile: ['menu:warehouse-mobile'],
  warehouseSetup: ['menu:warehouse-setup'],
  integrations: ['menu:system', 'menu:integration-reconciliation'],
  integrationAdmin: ['menu:system'],
  reconciliation: ['menu:integration-reconciliation'],
  iam: ['menu:system'],
  security: [],
};

export function hasPermission(
  role: string | undefined,
  permissionCodes: readonly string[] | undefined,
  permissionCode: string,
): boolean {
  if (role === 'TENANT_ADMIN') {
    return true;
  }
  return permissionCodes?.includes(permissionCode) ?? false;
}

export function hasAnyPermission(
  role: string | undefined,
  permissionCodes: readonly string[] | undefined,
  required: readonly string[],
): boolean {
  return role === 'TENANT_ADMIN'
    || required.some((permission) => permissionCodes?.includes(permission));
}

export function canAccessModule(
  role: string | undefined,
  module: AppModule,
  permissionCodes: readonly string[] | undefined,
): boolean {
  if (module === 'dashboard' || module === 'security') return Boolean(role);
  return hasAnyPermission(role, permissionCodes, modulePermissions[module]);
}
