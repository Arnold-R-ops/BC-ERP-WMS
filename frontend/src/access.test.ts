import { describe, expect, it } from 'vitest';
import { canAccessModule } from './access';

describe('coarse module access', () => {
  it('allows super administrators to see every module', () => {
    expect(canAccessModule('SUPER_ADMIN', 'integrations')).toBe(true);
  });

  it('limits warehouse operators to warehouse workflows', () => {
    expect(canAccessModule('WAREHOUSE_STAFF', 'inventory')).toBe(true);
    expect(canAccessModule('WAREHOUSE_STAFF', 'sales')).toBe(false);
  });

  it('keeps the dashboard visible for unknown roles', () => {
    expect(canAccessModule('CUSTOM_ROLE', 'dashboard')).toBe(true);
    expect(canAccessModule('CUSTOM_ROLE', 'products')).toBe(false);
  });
});
