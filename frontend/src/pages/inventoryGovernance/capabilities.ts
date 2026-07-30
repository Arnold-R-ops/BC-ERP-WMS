export interface StocktakeCapabilities {
  canCreate: boolean;
  canCount: boolean;
  canReview: boolean;
}

export interface CorrectionCapabilities {
  canAdjust: boolean;
  canApprove: boolean;
}

export function getStocktakeCapabilities(role?: string): StocktakeCapabilities {
  return {
    canCreate: role === 'SUPER_ADMIN' || role === 'GENERAL_MANAGER' || role === 'WAREHOUSE_ADMIN',
    canCount: role === 'SUPER_ADMIN' || role === 'WAREHOUSE_ADMIN' || role === 'WAREHOUSE_STAFF',
    canReview: role === 'SUPER_ADMIN' || role === 'GENERAL_MANAGER',
  };
}

export function getCorrectionCapabilities(role?: string): CorrectionCapabilities {
  return {
    canAdjust: role === 'SUPER_ADMIN' || role === 'WAREHOUSE_ADMIN',
    canApprove: role === 'SUPER_ADMIN' || role === 'GENERAL_MANAGER',
  };
}
