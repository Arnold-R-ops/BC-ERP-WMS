import { describe, expect, it } from 'vitest';
import { getIamCapabilities } from './capabilities';

describe('IAM capabilities', () => {
  it('allows only SUPER_ADMIN to manage identities', () => {
    expect(getIamCapabilities('SUPER_ADMIN').canManage).toBe(true);
  });

  it.each(['GENERAL_MANAGER', 'WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF', 'SALESPERSON', 'PURCHASER', ''])
    ('denies IAM management to %s', (role) => {
      expect(getIamCapabilities(role).canManage).toBe(false);
    });
});
