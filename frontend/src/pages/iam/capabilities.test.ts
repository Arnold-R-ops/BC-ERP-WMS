import { describe, expect, it } from 'vitest';
import { getIamCapabilities } from './capabilities';

describe('IAM capabilities', () => {
  it('allows the company administrator to govern company IAM', () => {
    expect(getIamCapabilities('TENANT_ADMIN')).toEqual({
      canManage: true,
      canManageProtectedIdentities: true,
      canApproveHighRiskPackages: true,
    });
  });

  it('allows SECURITY_ADMIN to manage ordinary IAM only', () => {
    expect(getIamCapabilities('SECURITY_ADMIN')).toEqual({
      canManage: true,
      canManageProtectedIdentities: false,
      canApproveHighRiskPackages: false,
    });
  });

  it.each(['GENERAL_MANAGER', 'WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF', 'SALESPERSON', 'PURCHASER', ''])
    ('denies IAM management to %s', (role) => {
      expect(getIamCapabilities(role)).toEqual({
        canManage: false,
        canManageProtectedIdentities: false,
        canApproveHighRiskPackages: false,
      });
    });
});
