export interface WarehouseSetupCapabilities {
  canManage: boolean;
}

export function getWarehouseSetupCapabilities(role: string, permissionCodes?: string[]): WarehouseSetupCapabilities {
  return {
    canManage: hasPermission(role, permissionCodes, 'warehouse:manage')
      && hasPermission(role, permissionCodes, 'location:manage'),
  };
}
import { hasPermission } from '../../access';
