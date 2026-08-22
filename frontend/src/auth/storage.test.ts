import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AUTH_STORAGE_KEY,
  clearAuthSession,
  clearUserActivity,
  type AuthSession,
  readAuthSession,
  writeAuthSession,
} from './storage';

const validSession: AuthSession = {
  token: 'token',
  tokenType: 'Bearer',
  username: 'admin',
  currentRole: 'TENANT_ADMIN',
  availableRoles: ['TENANT_ADMIN'],
  permissionCodes: [],
  expiresAt: 2_000,
  mustChangePassword: false,
};

describe('auth session storage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.spyOn(Date, 'now').mockReturnValue(1_000);
  });

  it('round-trips a valid session', () => {
    writeAuthSession(validSession);
    expect(readAuthSession()).toEqual(validSession);
  });

  it('removes an expired session', () => {
    writeAuthSession({ ...validSession, expiresAt: 999 });
    expect(readAuthSession()).toBeNull();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });

  it('removes malformed persisted data', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, '{bad json');
    expect(readAuthSession()).toBeNull();
  });

  it('clears the persisted session', () => {
    localStorage.setItem('2g-wms.platform-auth-session', 'platform-session');
    writeAuthSession(validSession);
    clearAuthSession();
    clearUserActivity();
    expect(readAuthSession()).toBeNull();
    expect(localStorage.getItem('2g-wms.platform-auth-session')).toBe('platform-session');
  });
});
