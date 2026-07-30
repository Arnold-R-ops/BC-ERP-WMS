import { describe, expect, it } from 'vitest';
import { canAccessModule } from './access';

describe('coarse module access', () => {
  it('allows super administrators to see every module', () => {
    expect(canAccessModule('SUPER_ADMIN', 'integrations')).toBe(true);
    expect(canAccessModule('SUPER_ADMIN', 'iam')).toBe(true);
  });

  it('keeps identity administration exclusive to super administrators', () => {
    expect(canAccessModule('GENERAL_MANAGER', 'iam')).toBe(false);
    expect(canAccessModule('WAREHOUSE_ADMIN', 'iam')).toBe(false);
    expect(canAccessModule('CUSTOM_ROLE', 'iam')).toBe(false);
  });

  it('limits company-wide analytics to management roles', () => {
    expect(canAccessModule('GENERAL_MANAGER', 'analytics')).toBe(true);
    expect(canAccessModule('SALESPERSON', 'analytics')).toBe(false);
    expect(canAccessModule('WAREHOUSE_ADMIN', 'analytics')).toBe(false);
  });

  it('limits warehouse operators to warehouse workflows', () => {
    expect(canAccessModule('WAREHOUSE_STAFF', 'inventory')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'outbound')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'stocktake')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseMobile')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseSetup')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'inventoryCorrection')).toBe(false);
    expect(canAccessModule('WAREHOUSE_STAFF', 'sales')).toBe(false);
  });

  it('keeps the mobile operation surface with warehouse roles', () => {
    expect(canAccessModule('WAREHOUSE_ADMIN', 'warehouseMobile')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseMobile')).toBe(true);
    expect(canAccessModule('GENERAL_MANAGER', 'warehouseMobile')).toBe(false);
    expect(canAccessModule('SALESPERSON', 'warehouseMobile')).toBe(false);
  });

  it('exposes warehouse setup only to warehouse-facing roles', () => {
    expect(canAccessModule('GENERAL_MANAGER', 'warehouseSetup')).toBe(true);
    expect(canAccessModule('WAREHOUSE_ADMIN', 'warehouseSetup')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'warehouseSetup')).toBe(true);
    expect(canAccessModule('SALESPERSON', 'warehouseSetup')).toBe(false);
    expect(canAccessModule('PURCHASER', 'warehouseSetup')).toBe(false);
  });

  it('keeps the dashboard visible for unknown roles', () => {
    expect(canAccessModule('CUSTOM_ROLE', 'dashboard')).toBe(true);
    expect(canAccessModule('CUSTOM_ROLE', 'products')).toBe(false);
  });

  it('separates client and supplier workspaces by operational role', () => {
    expect(canAccessModule('SALESPERSON', 'customers')).toBe(true);
    expect(canAccessModule('SALESPERSON', 'suppliers')).toBe(false);
    expect(canAccessModule('PURCHASER', 'customers')).toBe(false);
    expect(canAccessModule('PURCHASER', 'suppliers')).toBe(true);
  });
});
