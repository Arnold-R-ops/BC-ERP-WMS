export const PLATFORM_AUTH_STORAGE_KEY = '2g-wms.platform-auth-session';

export interface PlatformSession {
  token: string;
  tokenType: string;
  email: string;
  roles: string[];
  expiresAt: number;
}

function isPlatformSession(value: unknown): value is PlatformSession {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<PlatformSession>;
  return typeof candidate.token === 'string'
    && typeof candidate.tokenType === 'string'
    && typeof candidate.email === 'string'
    && Array.isArray(candidate.roles)
    && candidate.roles.every((role) => typeof role === 'string')
    && typeof candidate.expiresAt === 'number';
}

export function readPlatformSession(now = Date.now()): PlatformSession | null {
  const raw = sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY);
  if (!raw) return null;

  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isPlatformSession(parsed) || parsed.expiresAt <= now) {
      clearPlatformSession();
      return null;
    }
    return parsed;
  } catch {
    clearPlatformSession();
    return null;
  }
}

export function writePlatformSession(session: PlatformSession): void {
  sessionStorage.setItem(PLATFORM_AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function clearPlatformSession(): void {
  sessionStorage.removeItem(PLATFORM_AUTH_STORAGE_KEY);
}
