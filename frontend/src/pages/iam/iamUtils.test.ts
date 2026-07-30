import { describe, expect, it } from 'vitest';
import { findDefaultRoleId, isCurrentUser, orderRoleIds, roleLabel } from './iamUtils';

describe('IAM page utilities', () => {
  it('keeps the selected default role first without duplicating roles', () => {
    expect(orderRoleIds([3, 5, 3, 7], 5)).toEqual([5, 3, 7]);
    expect(orderRoleIds([3, 5], 9)).toEqual([3, 5]);
  });

  it('matches the current account by immutable username', () => {
    expect(isCurrentUser({ username: 'admin' }, 'admin')).toBe(true);
    expect(isCurrentUser({ username: 'operator' }, 'admin')).toBe(false);
  });

  it('resolves role identity and readable labels', () => {
    const roles = [{ id: 3, roleCode: 'WAREHOUSE_ADMIN', roleName: 'Warehouse administrator' }];
    expect(findDefaultRoleId({ defaultRoleCode: 'WAREHOUSE_ADMIN' }, roles)).toBe(3);
    expect(roleLabel(roles[0])).toBe('Warehouse administrator (WAREHOUSE_ADMIN)');
  });
});
