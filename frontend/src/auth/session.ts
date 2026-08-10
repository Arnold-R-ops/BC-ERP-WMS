import type { LoginResponse, SwitchRoleResponse } from '../api/auth';
import type { AuthSession } from './storage';

export function createAuthSession(response: LoginResponse, now = Date.now()): AuthSession {
  if (
    !response.token ||
    !response.username ||
    !response.currentRole ||
    !response.availableRoles?.length ||
    !response.permissionCodes ||
    !response.expiresIn
  ) {
    throw new Error('INVALID_AUTH_RESPONSE');
  }

  return {
    token: response.token,
    tokenType: response.tokenType ?? 'Bearer',
    username: response.username,
    currentRole: response.currentRole,
    availableRoles: response.availableRoles,
    permissionCodes: response.permissionCodes,
    expiresAt: now + response.expiresIn,
    mustChangePassword: response.mustChangePassword ?? false,
  };
}

export function mergeRoleSwitchSession(
  current: AuthSession,
  response: SwitchRoleResponse,
  now = Date.now(),
): AuthSession {
  if (!response.token || !response.currentRole || !response.permissionCodes) {
    throw new Error('INVALID_AUTH_RESPONSE');
  }

  return {
    ...current,
    token: response.token,
    tokenType: response.tokenType ?? current.tokenType,
    currentRole: response.currentRole,
    permissionCodes: response.permissionCodes,
    expiresAt: now + (response.expiresIn ?? 86_400_000),
  };
}
