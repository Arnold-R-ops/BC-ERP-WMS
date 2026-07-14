import { describe, expect, it } from 'vitest';
import type { AuthSession } from './storage';
import { createAuthSession, mergeRoleSwitchSession } from './session';

describe('authentication session transitions', () => {
  it('creates a complete local session from a login response', () => {
    expect(
      createAuthSession(
        {
          token: 'login-token',
          tokenType: 'Bearer',
          username: 'operator',
          currentRole: 'WAREHOUSE_ADMIN',
          availableRoles: ['WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF'],
          expiresIn: 60_000,
          mustChangePassword: true,
        },
        1_000,
      ),
    ).toMatchObject({
      token: 'login-token',
      expiresAt: 61_000,
      mustChangePassword: true,
    });
  });

  it('replaces role and token while retaining login-only context', () => {
    const current: AuthSession = {
      token: 'old-token',
      tokenType: 'Bearer',
      username: 'operator',
      currentRole: 'WAREHOUSE_ADMIN',
      availableRoles: ['WAREHOUSE_ADMIN', 'WAREHOUSE_STAFF'],
      expiresAt: 10_000,
      mustChangePassword: false,
    };

    expect(
      mergeRoleSwitchSession(
        current,
        {
          token: 'new-token',
          tokenType: 'Bearer',
          currentRole: 'WAREHOUSE_STAFF',
          expiresIn: 30_000,
        },
        2_000,
      ),
    ).toEqual({
      ...current,
      token: 'new-token',
      currentRole: 'WAREHOUSE_STAFF',
      expiresAt: 32_000,
    });
  });

  it('rejects an incomplete role-switch response', () => {
    const current = {} as AuthSession;
    expect(() => mergeRoleSwitchSession(current, {})).toThrow('INVALID_AUTH_RESPONSE');
  });
});
