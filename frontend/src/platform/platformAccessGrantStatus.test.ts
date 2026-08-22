import { describe, expect, it } from 'vitest';
import type { PlatformAccessGrant } from './api';
import { platformAccessGrantStatus } from './platformAccessGrantStatus';

const grant: PlatformAccessGrant = {
  id: 1,
  platformUserId: 2,
  platformUserEmail: 'delegate@example.com',
  capability: 'READ',
  tenantId: 3,
  tenantName: 'Tenant',
  datasetCode: 'users',
  effectiveFrom: '2026-08-21T00:00:00Z',
  expiresAt: '2026-08-22T00:00:00Z',
  revokedAt: null,
};

describe('platformAccessGrantStatus', () => {
  it('marks a currently effective grant active', () => {
    expect(platformAccessGrantStatus(grant, Date.parse('2026-08-21T12:00:00Z'))).toBe('ACTIVE');
  });

  it('distinguishes scheduled and expired grants', () => {
    expect(platformAccessGrantStatus(grant, Date.parse('2026-08-20T23:59:59Z'))).toBe('SCHEDULED');
    expect(platformAccessGrantStatus(grant, Date.parse('2026-08-22T00:00:00Z'))).toBe('EXPIRED');
  });

  it('gives revocation precedence over time status', () => {
    expect(platformAccessGrantStatus(
      { ...grant, revokedAt: '2026-08-21T01:00:00Z' },
      Date.parse('2026-08-23T00:00:00Z'),
    )).toBe('REVOKED');
  });
});
