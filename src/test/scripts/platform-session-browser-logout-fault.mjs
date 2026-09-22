import { createHash } from 'node:crypto';

// Test-only denial, never an authentication bypass. No tokens or decoded claims are logged.
export function shouldDropLogout(request, control, now = Date.now()) {
  if (request.method !== 'POST' || request.pathname !== '/api/platform/auth/logout') return false;
  if (!control || control.enabled !== true || control.scenario !== 'drop-logout-before-upstream') return false;
  const start = Date.parse(control.armedAt);
  const end = Date.parse(control.expiresAt);
  if (!Number.isFinite(start) || !Number.isFinite(end) || now < start || now >= end || end - start > 30 * 60_000) return false;
  if (!/^[a-f0-9]{64}$/.test(control.sessionSha256 ?? '')) return false;
  if (typeof request.authorization !== 'string' || !request.authorization.startsWith('Bearer ')) return false;
  try {
    const pieces = request.authorization.slice(7).split('.');
    if (pieces.length !== 3) return false;
    const claims = JSON.parse(Buffer.from(pieces[1], 'base64url').toString('utf8'));
    if (claims.principal_type !== 'PLATFORM' || claims.normalized_email !== 'session-uat-a@platform.invalid') return false;
    if (typeof claims.platform_session_id !== 'string' || !/^[0-9a-f-]{36}$/i.test(claims.platform_session_id)) return false;
    return createHash('sha256').update(claims.platform_session_id).digest('hex') === control.sessionSha256;
  } catch { return false; }
}
