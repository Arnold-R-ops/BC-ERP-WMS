import { describe, expect, it } from 'vitest';
import type { PlatformEffectiveAccess } from './api';
import { effectiveDatasetAccess, effectiveDatasets } from './TenantBrowser';

describe('delegated tenant browser visibility', () => {
  const delegated: PlatformEffectiveAccess = {
    superAdmin: false,
    scopes: [
      { tenantId: 6, datasetCode: 'users', read: true, export: false },
      { tenantId: 6, datasetCode: 'inventory', read: false, export: true },
      { tenantId: 8, datasetCode: 'sales_orders', read: true, export: true },
    ],
  };

  it('lists only datasets delegated for the selected tenant', () => {
    expect(effectiveDatasets(delegated, 6).map((dataset) => dataset.code)).toEqual(['users', 'inventory']);
    expect(effectiveDatasets(delegated, 7)).toEqual([]);
  });

  it('keeps read and export capabilities independent', () => {
    expect(effectiveDatasetAccess(delegated, 6, 'users')).toEqual({ read: true, export: false });
    expect(effectiveDatasetAccess(delegated, 6, 'inventory')).toEqual({ read: false, export: true });
    expect(effectiveDatasetAccess(delegated, 8, 'sales_orders')).toEqual({ read: true, export: true });
    expect(effectiveDatasetAccess(delegated, 6, 'products')).toEqual({ read: false, export: false });
  });

  it('keeps the super administrator unrestricted', () => {
    const superAdmin: PlatformEffectiveAccess = { superAdmin: true, scopes: [] };
    expect(effectiveDatasets(superAdmin, 999)).toHaveLength(6);
    expect(effectiveDatasetAccess(superAdmin, 999, 'products')).toEqual({ read: true, export: true });
  });
});
