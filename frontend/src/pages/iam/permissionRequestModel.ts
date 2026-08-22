import type { Permission, PermissionRequestStatus, Role, UserAccount } from '../../api/iam';

export type PermissionRequestDecision = 'APPROVED' | 'REJECTED' | 'REVOKED';
export type { PermissionRequestStatus };

export interface PermissionRequestFormValues {
  targetUserId: number;
  requestedRoleId: number;
  warehouseIds?: number[];
  requestReason?: string;
}

export function isRequestableRole(role: Role): boolean {
  return role.status === 'ACTIVE'
    && role.assignableToUsers !== false
    && role.privilegedRole !== true
    && role.roleCode !== 'TENANT_ADMIN'
    && role.roleCode !== 'SECURITY_ADMIN'
    && (role.roleType !== 'CUSTOM' || role.reviewStatus === 'APPROVED');
}

export function isProtectedIdentity(user: UserAccount): boolean {
  return user.roleCodes?.some((code) => code === 'TENANT_ADMIN' || code === 'SECURITY_ADMIN') ?? false;
}

export function roleHighRiskPermissionCount(role: Role, permissions: Permission[]): number {
  const permissionIds = new Set(role.permissionIds ?? []);
  return permissions.filter((permission) => permission.id !== undefined
    && permissionIds.has(permission.id)
    && permission.riskLevel === 'HIGH').length;
}
