import { describe, expect, it } from 'vitest';
import { getWarehouseSetupCapabilities } from './capabilities';

describe('warehouse setup capabilities', () => {
  it.each(['SUPER_ADMIN', 'WAREHOUSE_ADMIN'])('allows %s to maintain setup data', (role) => {
    expect(getWarehouseSetupCapabilities(role).canManage).toBe(true);
  });

  it.each(['GENERAL_MANAGER', 'WAREHOUSE_STAFF', 'SALESPERSON', 'PURCHASER'])('keeps %s read only', (role) => {
    expect(getWarehouseSetupCapabilities(role).canManage).toBe(false);
  });
});
