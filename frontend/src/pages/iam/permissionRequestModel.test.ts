import { describe, expect, it } from 'vitest';
import type { Permission, Role, UserAccount } from '../../api/iam';
import { isProtectedIdentity, isRequestableRole, roleHighRiskPermissionCount } from './permissionRequestModel';

const role: Role = {
  id: 4,
  roleCode: 'WAREHOUSE_STAFF',
  roleName: 'Warehouse staff',
  roleType: 'SYSTEM',
  status: 'ACTIVE',
  permissionIds: [11, 12],
};

const permissions: Permission[] = [
  { id: 11, permissionCode: 'inbound:receive', permissionName: 'Receive', permissionType: 'BUTTON', riskLevel: 'NORMAL', status: 'ACTIVE' },
  { id: 12, permissionCode: 'outbound:pick', permissionName: 'Pick', permissionType: 'BUTTON', riskLevel: 'HIGH', status: 'ACTIVE' },
];

describe('permission request preview rules', () => {
  it('allows active ordinary packages but rejects protected and unapproved packages', () => {
    expect(isRequestableRole(role)).toBe(true);
    expect(isRequestableRole({ ...role, roleCode: 'SUPER_ADMIN', privilegedRole: true })).toBe(false);
    expect(isRequestableRole({ ...role, roleType: 'CUSTOM', reviewStatus: 'DRAFT' })).toBe(false);
    expect(isRequestableRole({ ...role, status: 'DISABLED' })).toBe(false);
  });

  it('derives high-risk approval isolation from the requested package permissions', () => {
    expect(roleHighRiskPermissionCount(role, permissions)).toBe(1);
  });

  it('recognizes protected administrator identities', () => {
    const user: UserAccount = { id: 9, username: 'security', roleCodes: ['SECURITY_ADMIN'] };
    expect(isProtectedIdentity(user)).toBe(true);
    expect(isProtectedIdentity({ ...user, roleCodes: ['WAREHOUSE_STAFF'] })).toBe(false);
  });
});
