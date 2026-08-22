import type { components } from './schema';
import { apiRequest } from './http';

export type LoginRequest = components['schemas']['LoginRequest'];
export type LoginResponse = components['schemas']['LoginResponse'] & { sessionEndsAt?: number };
export type SwitchRoleRequest = components['schemas']['SwitchRoleRequest'];
export type SwitchRoleResponse = components['schemas']['SwitchRoleResponse'] & { sessionEndsAt?: number };
export type ChangeMyPasswordRequest = components['schemas']['ChangeMyPasswordRequest'];
export interface SessionHandoffRequest {
  code: string;
}

export interface RevokeOwnSessionsRequest {
  currentPassword: string;
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: request,
    suppressAuthEvents: true,
  });
}

export function consumeSessionHandoff(request: SessionHandoffRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/public/v1/session-handoff/consume', {
    method: 'POST',
    body: request,
    suppressAuthEvents: true,
    omitAuth: true,
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

export function refreshTenantToken(): Promise<LoginResponse | undefined> {
  return apiRequest<LoginResponse | undefined>('/api/auth/refresh-token', {
    method: 'POST',
    suppressAuthEvents: true,
  });
}

export function revokeAllSessions(request: RevokeOwnSessionsRequest): Promise<void> {
  return apiRequest<void>('/api/auth/revoke-all-sessions', {
    method: 'POST',
    body: request,
  });
}
