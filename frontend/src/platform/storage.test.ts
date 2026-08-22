import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearPlatformSession,
  PLATFORM_AUTH_STORAGE_KEY,
  readPlatformSession,
  writePlatformSession,
} from './storage';

describe('platform session storage', () => {
  beforeEach(() => sessionStorage.clear());

  it('uses only the dedicated session storage key', () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: 2_000,
    });

    expect(readPlatformSession(1_000)).toMatchObject({ email: 'admin@bcwms.com' });
    expect(sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY)).toContain('platform-token');
    expect(localStorage.getItem('2g-wms.auth-session')).toBeNull();
  });

  it('clears an expired platform session', () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: 1_000,
    });

    expect(readPlatformSession(1_000)).toBeNull();
    expect(sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY)).toBeNull();
    clearPlatformSession();
  });
});
