import { expect, it } from 'vitest';
import type { AuthSession } from './storage';
import { sessionCacheScope } from './sessionCacheScope';
function session(company: number, epoch: number, expiration = 1): AuthSession {
  return { token: `header.${btoa(JSON.stringify({ company_id: company, tenant_session_version: epoch, security_version: 1, exp: expiration }))}.signature`, tokenType: 'Bearer', username: 'same-owner', currentRole: 'TENANT_ADMIN', availableRoles: [], permissionCodes: [], expiresAt: 1, mustChangePassword: false };
}
it('separates tenants even when owner identity and role are the same', () => {
  expect(sessionCacheScope(session(1, 1))).not.toBe(sessionCacheScope(session(2, 1)));
});
it('never reuses pre-suspension cached business data after resume', () => {
  expect(sessionCacheScope(session(1, 1))).not.toBe(sessionCacheScope(session(1, 3)));
});
it('ordinary refresh within the same tenant and security version preserves the cache', () => {
  expect(sessionCacheScope(session(1, 1, 1))).toBe(sessionCacheScope(session(1, 1, 2)));
});
