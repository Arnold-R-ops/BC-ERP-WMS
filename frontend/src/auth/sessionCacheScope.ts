import type { AuthSession } from './storage';

/** Cache partition hint only; JWT authenticity and tenant access are always checked by the server. */
export function sessionCacheScope(session: AuthSession | null): string | null {
  if (!session) return null;
  let tenantContext: unknown = session.token; // Malformed/legacy tokens never share a cache partition.
  try {
    const payload = JSON.parse(atob(session.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    if (Number.isSafeInteger(payload.company_id) && payload.company_id > 0) {
      tenantContext = [payload.company_id, payload.tenant_session_version ?? 1, payload.security_version];
    }
  } catch { /* Authentication will reject an invalid token; this function grants no access. */ }
  return JSON.stringify([tenantContext, session.username, session.currentRole]);
}
