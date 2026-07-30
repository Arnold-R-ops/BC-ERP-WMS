import type { components } from './schema';
import { apiRequest } from './http';

export type UserAccount = components['schemas']['UserWithRolesDTO'];
export type Role = components['schemas']['RoleDTO'];
export type Permission = components['schemas']['PermissionDTO'];
export type CreateUserPayload = components['schemas']['CreateUserRequest'];
export type UpdateUserPayload = components['schemas']['UpdateUserRequest'];
export type ResetPasswordResult = components['schemas']['ResetPasswordResponse'];

export const USERS_QUERY_KEY = ['iam', 'users'] as const;
export const ROLES_QUERY_KEY = ['iam', 'roles'] as const;
export const PERMISSIONS_QUERY_KEY = ['iam', 'permissions'] as const;

export function listUsers(): Promise<UserAccount[]> {
  return apiRequest<UserAccount[]>('/api/users');
}

export function createUser(payload: CreateUserPayload): Promise<UserAccount> {
  return apiRequest<UserAccount>('/api/users', { method: 'POST', body: payload });
}

export function updateUser(id: number, payload: UpdateUserPayload): Promise<UserAccount> {
  return apiRequest<UserAccount>(`/api/users/${id}`, { method: 'PUT', body: payload });
}

export function replaceUserRoles(id: number, roleIds: number[], defaultRoleId: number): Promise<UserAccount> {
  return apiRequest<UserAccount>(`/api/users/${id}/roles`, {
    method: 'POST',
    body: { roleIds, defaultRoleId },
  });
}

export function resetUserPassword(id: number): Promise<ResetPasswordResult> {
  return apiRequest<ResetPasswordResult>(`/api/users/${id}/reset-password`, { method: 'POST' });
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
