export const AUTH_STORAGE_KEY = '2g-wms.auth-session';

export interface AuthSession {
  token: string;
  tokenType: string;
  username: string;
  currentRole: string;
  availableRoles: string[];
  permissionCodes: string[];
  expiresAt: number;
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
