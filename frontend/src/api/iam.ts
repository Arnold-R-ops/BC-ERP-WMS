import type { components } from './schema';
import { apiRequest } from './http';

export type UserAccount = components['schemas']['UserWithRolesDTO'];
export type Role = components['schemas']['RoleDTO'];
export type Permission = components['schemas']['PermissionDTO'];
export type CreateUserPayload = components['schemas']['CreateUserRequest'];
export type UpdateUserPayload = components['schemas']['UpdateUserRequest'];
export type ResetPasswordResult = components['schemas']['ResetPasswordResponse'];
export type RoleCopyRequest = components['schemas']['RoleCopyRequest'];
export type RoleCopyPreview = components['schemas']['RoleCopyPreviewResponse'];
export type RoleCopyResult = components['schemas']['RoleCopyResult'];
export type RolePackageCreateRequest = components['schemas']['RolePackageCreateRequest'];
export type RolePackageDraftRequest = components['schemas']['RolePackageDraftRequest'];
export type RoleReviewSubmitRequest = components['schemas']['RoleReviewSubmitRequest'];
export type RoleReviewRequest = components['schemas']['RoleReviewRequest'];
export type RoleRuntimeStatusRequest = components['schemas']['RoleRuntimeStatusRequest'];
export type RoleGovernanceResult = components['schemas']['RoleGovernanceResult'];
export type RoleGovernanceAudit = components['schemas']['RoleGovernanceAuditDTO'];
export type AssignableWarehouse = components['schemas']['AssignableWarehouseDTO'];

export type PermissionRequestStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'REVOKED';
export interface PermissionRequest {
  id: number;
  targetUserId: number;
  targetUsername: string;
  requestedRoleId: number;
  requestedRoleCode: string;
  requestedRoleName: string;
  warehouses: AssignableWarehouse[];
  requestReason?: string;
  status: PermissionRequestStatus;
  highRiskPermissionCount: number;
  submittedByUsername: string;
  submittedAt: string;
  reviewedByUsername?: string;
  reviewedAt?: string;
  reviewComment?: string;
  revokedByUsername?: string;
  revokedAt?: string;
  revocationComment?: string;
}
export interface PermissionRequestPage {
  items: PermissionRequest[];
  total: number;
  page: number;
  size: number;
}
export interface PermissionRequestCreatePayload {
  targetUserId: number;
  requestedRoleId: number;
  warehouseIds?: number[];
  requestReason?: string;
}
export interface PermissionRequestListParams {
  status?: PermissionRequestStatus;
  targetUserId?: number;
  requestedRoleId?: number;
  page?: number;
  size?: number;
}

export const USERS_QUERY_KEY = ['iam', 'users'] as const;
export const ROLES_QUERY_KEY = ['iam', 'roles'] as const;
export const PERMISSIONS_QUERY_KEY = ['iam', 'permissions'] as const;
export const ASSIGNABLE_WAREHOUSES_QUERY_KEY = ['iam', 'assignable-warehouses'] as const;
export const PERMISSION_REQUESTS_QUERY_KEY = ['iam', 'permission-requests'] as const;

export function listUsers(): Promise<UserAccount[]> {
  return apiRequest<UserAccount[]>('/api/users');
}

export function createUser(payload: CreateUserPayload): Promise<UserAccount> {
  return apiRequest<UserAccount>('/api/users', { method: 'POST', body: payload });
}

export function updateUser(id: number, payload: UpdateUserPayload): Promise<UserAccount> {
  return apiRequest<UserAccount>(`/api/users/${id}`, { method: 'PUT', body: payload });
}

export function replaceUserRoles(
  id: number,
  roleIds: number[],
  defaultRoleId: number,
  warehouseIds: number[] = [],
): Promise<UserAccount> {
  return apiRequest<UserAccount>(`/api/users/${id}/roles`, {
    method: 'POST',
    body: { roleIds, defaultRoleId, warehouseIds },
  });
}

export function listAssignableWarehouses(): Promise<AssignableWarehouse[]> {
  return apiRequest<AssignableWarehouse[]>('/api/users/assignable-warehouses');
}

export function resetUserPassword(id: number): Promise<ResetPasswordResult> {
  return apiRequest<ResetPasswordResult>(`/api/users/${id}/reset-password`, { method: 'POST' });
}

export function revokeUserSessions(id: number, reason: string): Promise<void> {
  return apiRequest<void>(`/api/users/${id}/revoke-sessions`, {
    method: 'POST',
    body: { reason },
  });
}

export function deleteUser(id: number): Promise<void> {
  return apiRequest<void>(`/api/users/${id}`, { method: 'DELETE' });
}

export function listRoles(activeOnly = false): Promise<Role[]> {
  return apiRequest<Role[]>(`/api/roles?activeOnly=${activeOnly}`);
}

export function listPermissions(params: { activeOnly?: boolean; type?: string } = {}): Promise<Permission[]> {
  const search = new URLSearchParams();
  search.set('activeOnly', String(params.activeOnly ?? false));
  if (params.type) search.set('type', params.type);
  return apiRequest<Permission[]>(`/api/permissions?${search.toString()}`);
}

export function listRolePermissions(id: number): Promise<Permission[]> {
  return apiRequest<Permission[]>(`/api/roles/${id}/permissions`);
}

export function getRoleCopyPreview(id: number): Promise<RoleCopyPreview> {
  return apiRequest<RoleCopyPreview>(`/api/roles/${id}/copy-preview`);
}

export function copyRole(id: number, payload: RoleCopyRequest): Promise<RoleCopyResult> {
  return apiRequest<RoleCopyResult>(`/api/roles/${id}/copies`, { method: 'POST', body: payload });
}

export function createRolePackageDraft(payload: RolePackageCreateRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>('/api/roles/drafts', { method: 'POST', body: payload });
}

export function updateRolePackageDraft(id: number, payload: RolePackageDraftRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>(`/api/roles/${id}/draft`, { method: 'PUT', body: payload });
}

export function submitRolePackageReview(id: number, payload: RoleReviewSubmitRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>(`/api/roles/${id}/submit-review`, { method: 'POST', body: payload });
}

export function reviewRolePackage(id: number, payload: RoleReviewRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>(`/api/roles/${id}/review`, { method: 'POST', body: payload });
}

export function activateRolePackage(id: number, payload: RoleRuntimeStatusRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>(`/api/roles/${id}/activate`, { method: 'POST', body: payload });
}

export function deactivateRolePackage(id: number, payload: RoleRuntimeStatusRequest): Promise<RoleGovernanceResult> {
  return apiRequest<RoleGovernanceResult>(`/api/roles/${id}/deactivate`, { method: 'POST', body: payload });
}

export function getRoleGovernanceHistory(id: number): Promise<RoleGovernanceAudit[]> {
  return apiRequest<RoleGovernanceAudit[]>(`/api/roles/${id}/governance-history`);
}

export function listPermissionRequests(params: PermissionRequestListParams = {}): Promise<PermissionRequestPage> {
  const search = new URLSearchParams();
  if (params.status) search.set('status', params.status);
  if (params.targetUserId !== undefined) search.set('targetUserId', String(params.targetUserId));
  if (params.requestedRoleId !== undefined) search.set('requestedRoleId', String(params.requestedRoleId));
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  return apiRequest<PermissionRequestPage>(`/api/permission-requests?${search.toString()}`);
}

export function createPermissionRequest(payload: PermissionRequestCreatePayload): Promise<PermissionRequest> {
  return apiRequest<PermissionRequest>('/api/permission-requests', { method: 'POST', body: payload });
}

export function reviewPermissionRequest(
  id: number,
  approved: boolean,
  comment?: string,
): Promise<PermissionRequest> {
  return apiRequest<PermissionRequest>(`/api/permission-requests/${id}/review`, {
    method: 'POST', body: { approved, comment },
  });
}

export function revokePermissionRequest(id: number, comment: string): Promise<PermissionRequest> {
  return apiRequest<PermissionRequest>(`/api/permission-requests/${id}/revoke`, {
    method: 'POST', body: { comment },
  });
}
