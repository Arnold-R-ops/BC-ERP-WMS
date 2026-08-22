import type { PlatformAccessGrant } from './api';

export type PlatformAccessGrantStatus = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'REVOKED';

export function platformAccessGrantStatus(
  grant: PlatformAccessGrant,
  now = Date.now(),
): PlatformAccessGrantStatus {
  if (grant.revokedAt) return 'REVOKED';
  if (Date.parse(grant.expiresAt) <= now) return 'EXPIRED';
  if (Date.parse(grant.effectiveFrom) > now) return 'SCHEDULED';
  return 'ACTIVE';
}
