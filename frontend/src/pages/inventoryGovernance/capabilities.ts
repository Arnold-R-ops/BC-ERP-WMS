export interface StocktakeCapabilities {
  canCreate: boolean;
  canCount: boolean;
  canReview: boolean;
}

export interface CorrectionCapabilities {
  canAdjust: boolean;
  canReview: boolean;
  canApprove: boolean;
  canReject: boolean;
}

export function getStocktakeCapabilities(role?: string, permissionCodes?: string[]): StocktakeCapabilities {
  return {
    canCreate: hasPermission(role, permissionCodes, 'stocktake:create'),
    canCount: hasPermission(role, permissionCodes, 'stocktake:count'),
    canReview: hasPermission(role, permissionCodes, 'stocktake:review'),
  };
}

export function getCorrectionCapabilities(role?: string, permissionCodes?: string[]): CorrectionCapabilities {
  return {
    canAdjust: hasPermission(role, permissionCodes, 'inventory:adjust'),
    canReview: hasPermission(role, permissionCodes, 'inventory:correction:review'),
    canApprove: hasPermission(role, permissionCodes, 'inventory:correction:approve'),
    canReject: hasPermission(role, permissionCodes, 'inventory:correction:reject'),
  };
}
import { hasPermission } from '../../access';
