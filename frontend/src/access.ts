export type AppModule =
  | 'dashboard'
  | 'products'
  | 'inventory'
  | 'sales'
  | 'purchasing'
  | 'inbound'
  | 'integrations';

const roleModules: Record<string, readonly AppModule[]> = {
  GENERAL_MANAGER: ['dashboard', 'products', 'inventory', 'sales', 'purchasing', 'inbound', 'integrations'],
  WAREHOUSE_ADMIN: ['dashboard', 'products', 'inventory', 'inbound'],
  WAREHOUSE_STAFF: ['dashboard', 'inventory', 'inbound'],
  SALESPERSON: ['dashboard', 'products', 'inventory', 'sales'],
  PURCHASER: ['dashboard', 'products', 'inventory', 'purchasing', 'inbound'],
};

export function canAccessModule(role: string, module: AppModule): boolean {
  if (role === 'SUPER_ADMIN') {
    return true;
  }
  return roleModules[role]?.includes(module) ?? module === 'dashboard';
}
