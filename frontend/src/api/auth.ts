import type { components } from './schema';
import { apiRequest } from './http';

export type LoginRequest = components['schemas']['LoginRequest'];
export type LoginResponse = components['schemas']['LoginResponse'];
export type SwitchRoleRequest = components['schemas']['SwitchRoleRequest'];
export type SwitchRoleResponse = components['schemas']['SwitchRoleResponse'];
export type ChangeMyPasswordRequest = components['schemas']['ChangeMyPasswordRequest'];

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: request,
    suppressAuthEvents: true,
  });
}

export function switchRole(request: SwitchRoleRequest): Promise<SwitchRoleResponse> {
  return apiRequest<SwitchRoleResponse>('/api/auth/switch-role', {
    method: 'POST',
    body: request,
  });
}

export function changeMyPassword(request: ChangeMyPasswordRequest): Promise<void> {
  return apiRequest<void>('/api/users/me/password', {
    method: 'PUT',
    body: request,
  });
}
