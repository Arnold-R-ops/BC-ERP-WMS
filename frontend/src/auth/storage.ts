export const AUTH_STORAGE_KEY = '2g-wms.auth-session';
export const AUTH_ACTIVITY_STORAGE_KEY = '2g-wms.auth-last-activity';
export const AUTH_REFRESH_LOCK_KEY = '2g-wms.auth-refresh-lock';

export interface AuthSession {
  token: string;
  tokenType: string;
  username: string;
  currentRole: string;
  availableRoles: string[];
  permissionCodes: string[];
  expiresAt: number;
  sessionEndsAt?: number;
  mustChangePassword: boolean;
}

function isAuthSession(value: unknown): value is AuthSession {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<AuthSession>;
  return (
    typeof candidate.token === 'string' &&
    typeof candidate.tokenType === 'string' &&
    typeof candidate.username === 'string' &&
    typeof candidate.currentRole === 'string' &&
    Array.isArray(candidate.availableRoles) &&
    candidate.availableRoles.every((role) => typeof role === 'string') &&
    Array.isArray(candidate.permissionCodes) &&
    candidate.permissionCodes.every((permission) => typeof permission === 'string') &&
    typeof candidate.expiresAt === 'number' &&
    (candidate.sessionEndsAt === undefined || typeof candidate.sessionEndsAt === 'number') &&
    typeof candidate.mustChangePassword === 'boolean'
  );
}

export function readAuthSession(): AuthSession | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isAuthSession(parsed) || parsed.expiresAt <= Date.now()) {
      clearAuthSession();
      return null;
    }
    return parsed;
  } catch {
    clearAuthSession();
    return null;
  }
}

export function writeAuthSession(session: AuthSession): void {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function clearAuthSession(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function readLastUserActivity(): number | null {
  const value = Number(localStorage.getItem(AUTH_ACTIVITY_STORAGE_KEY));
  return Number.isFinite(value) && value > 0 ? value : null;
}

export function recordUserActivity(at = Date.now()): void {
  localStorage.setItem(AUTH_ACTIVITY_STORAGE_KEY, String(at));
}

export function clearUserActivity(): void {
  localStorage.removeItem(AUTH_ACTIVITY_STORAGE_KEY);
  localStorage.removeItem(AUTH_REFRESH_LOCK_KEY);
}
