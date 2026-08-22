import { describe, expect, it } from 'vitest';
import { getWarehouseSetupCapabilities } from './capabilities';

describe('warehouse setup capabilities', () => {
  it('lets the super administrator bypass permission checks', () => {
    expect(getWarehouseSetupCapabilities('TENANT_ADMIN').canManage).toBe(true);
  });

  it('allows a role with both setup permissions to maintain setup data', () => {
    expect(getWarehouseSetupCapabilities('CUSTOM_ROLE', ['warehouse:manage', 'location:manage']).canManage).toBe(true);
  });

  it.each(['GENERAL_MANAGER', 'WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF', 'SALESPERSON', 'PURCHASER'])('keeps %s read only without both permissions', (role) => {
    expect(getWarehouseSetupCapabilities(role, ['warehouse:manage']).canManage).toBe(false);
  });
});
