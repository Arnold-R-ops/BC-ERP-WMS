import { describe, expect, it } from 'vitest';
import type { PlatformEffectiveAccess } from './api';
import { canAccessTenantManagement } from './PlatformTenantAccessProvider';

describe('platform tenant management entry', () => {
  it('is always available to the super administrator', () => {
    expect(canAccessTenantManagement({ superAdmin: true, tenantDirectoryScope: 'ALL', scopes: [] })).toBe(true);
  });

  it('is available to an ordinary administrator with an effective delegated scope', () => {
    const delegated: PlatformEffectiveAccess = {
      superAdmin: false,
      tenantDirectoryScope: 'GRANTED',
      scopes: [{ tenantId: 6, datasetCode: 'users', read: true, export: false }],
    };
    expect(canAccessTenantManagement(delegated)).toBe(true);
  });

  it('fails closed when no effective access is available', () => {
    expect(canAccessTenantManagement({ superAdmin: false, tenantDirectoryScope: 'NONE', scopes: [] })).toBe(false);
    expect(canAccessTenantManagement(null)).toBe(false);
  });

  it('is available to an operations administrator even before any data grant exists', () => {
    expect(canAccessTenantManagement({
      superAdmin: false,
      tenantDirectoryScope: 'ALL',
      scopes: [],
    })).toBe(true);
  });
});
