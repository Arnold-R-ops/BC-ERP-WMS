import { App as AntdApp } from 'antd';
import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  changeMyPassword as changeMyPasswordRequest,
  consumeSessionHandoff as consumeSessionHandoffRequest,
  login as loginRequest,
  refreshTenantToken as refreshTenantTokenRequest,
  revokeAllSessions as revokeAllSessionsRequest,
  switchRole as switchRoleRequest,
  type ChangeMyPasswordRequest,
  type LoginRequest,
} from '../api/auth';
import {
  type ApiError,
  AUTH_FORBIDDEN_EVENT,
  AUTH_UNAUTHORIZED_EVENT,
} from '../api/http';
import {
  clearAuthSession,
  clearUserActivity,
  AUTH_ACTIVITY_STORAGE_KEY,
  AUTH_REFRESH_LOCK_KEY,
  AUTH_STORAGE_KEY,
  readLastUserActivity,
  recordUserActivity,
  type AuthSession,
  readAuthSession,
  writeAuthSession,
} from './storage';
import { createAuthSession, mergeRoleSwitchSession } from './session';

interface AuthContextValue {
  session: AuthSession | null;
  login: (request: LoginRequest) => Promise<AuthSession>;
  completeHandoff: (code: string) => Promise<AuthSession>;
  logout: () => void;
  switchRole: (targetRoleCode: string) => Promise<void>;
  changePassword: (request: ChangeMyPasswordRequest) => Promise<void>;
  revokeAllSessions: (currentPassword: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
export const TENANT_IDLE_TIMEOUT_MS = 60 * 60 * 1000;
export const TENANT_REFRESH_THRESHOLD_MS = 60 * 60 * 1000;
const SESSION_CHECK_INTERVAL_MS = 15 * 1000;
const REFRESH_LOCK_TTL_MS = 30 * 1000;

interface RefreshLease {
  owner: string;
  expiresAt: number;
}

function acquireRefreshLease(owner: string, now: number): boolean {
  try {
    const raw = localStorage.getItem(AUTH_REFRESH_LOCK_KEY);
    const current = raw ? JSON.parse(raw) as Partial<RefreshLease> : null;
    if (current?.owner && current.owner !== owner
        && typeof current.expiresAt === 'number' && current.expiresAt > now) {
      return false;
    }
    localStorage.setItem(AUTH_REFRESH_LOCK_KEY, JSON.stringify({
      owner,
      expiresAt: now + REFRESH_LOCK_TTL_MS,
    } satisfies RefreshLease));
    const confirmed = JSON.parse(localStorage.getItem(AUTH_REFRESH_LOCK_KEY) ?? '{}') as Partial<RefreshLease>;
    return confirmed.owner === owner;
  } catch {
    return false;
  }
}

function releaseRefreshLease(owner: string): void {
  try {
    const current = JSON.parse(localStorage.getItem(AUTH_REFRESH_LOCK_KEY) ?? '{}') as Partial<RefreshLease>;
    if (current.owner === owner) localStorage.removeItem(AUTH_REFRESH_LOCK_KEY);
  } catch {
    localStorage.removeItem(AUTH_REFRESH_LOCK_KEY);
  }
}

export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
  const [session, setSession] = useState<AuthSession | null>(() => readAuthSession());
  const sessionRef = useRef(session);
  const refreshInFlightRef = useRef(false);
  const tabOwnerRef = useRef(`tenant-tab-${Date.now()}-${Math.random()}`);
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const logout = useCallback(() => {
    clearAuthSession();
    clearUserActivity();
    setSession(null);
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const nextSession = createAuthSession(await loginRequest(request));
    writeAuthSession(nextSession);
    recordUserActivity();
    setSession(nextSession);
    return nextSession;
  }, []);

  const completeHandoff = useCallback(async (code: string) => {
    const nextSession = createAuthSession(await consumeSessionHandoffRequest({ code }));
    writeAuthSession(nextSession);
    recordUserActivity();
    setSession(nextSession);
    return nextSession;
  }, []);

  const switchRole = useCallback(async (targetRoleCode: string) => {
    const currentSession = sessionRef.current;
    if (!currentSession) {
      return;
    }

    const response = await switchRoleRequest({ targetRoleCode });
    if (readAuthSession()?.token !== currentSession.token) {
      return;
    }
    const nextSession = mergeRoleSwitchSession(currentSession, response);
    writeAuthSession(nextSession);
    setSession(nextSession);
  }, []);

  const changePassword = useCallback(async (request: ChangeMyPasswordRequest) => {
    await changeMyPasswordRequest(request);
    logout();
  }, [logout]);

  const revokeAllSessions = useCallback(async (currentPassword: string) => {
    await revokeAllSessionsRequest({ currentPassword });
    logout();
  }, [logout]);

  const endSession = useCallback((translationKey: string) => {
    logout();
    message.warning(t(translationKey));
    navigate('/login', { replace: true });
  }, [logout, message, navigate, t]);

  const maintainSession = useCallback(async () => {
    const currentSession = sessionRef.current;
    if (!currentSession || refreshInFlightRef.current) return;

    const now = Date.now();
    const lastActivity = readLastUserActivity() ?? now;
    if (now - lastActivity >= TENANT_IDLE_TIMEOUT_MS) {
      endSession('auth.idleLogout');
      return;
    }
    if (currentSession.expiresAt <= now
        || (currentSession.sessionEndsAt !== undefined && currentSession.sessionEndsAt <= now)) {
      endSession('auth.sessionExpired');
      return;
    }
    if (currentSession.expiresAt - now >= TENANT_REFRESH_THRESHOLD_MS) return;
    // A token already capped by the seven-day deadline cannot be meaningfully renewed.
    if (currentSession.sessionEndsAt !== undefined
        && currentSession.sessionEndsAt - now <= TENANT_REFRESH_THRESHOLD_MS) return;
    if (!acquireRefreshLease(tabOwnerRef.current, now)) return;

    refreshInFlightRef.current = true;
    try {
      const response = await refreshTenantTokenRequest();
      if (!response) return;
      // Another tab may have signed out or replaced the token while this
      // request was in flight. Never resurrect or overwrite that newer state.
      if (readAuthSession()?.token !== currentSession.token) return;
      const nextSession = createAuthSession(response);
      writeAuthSession(nextSession);
      setSession(nextSession);
    } catch {
      endSession('auth.refreshFailed');
    } finally {
      refreshInFlightRef.current = false;
      releaseRefreshLease(tabOwnerRef.current);
    }
  }, [endSession]);

  useEffect(() => {
    if (!session) return undefined;
    if (readLastUserActivity() === null) recordUserActivity();

    const recordActivity = (): void => {
      if (!sessionRef.current) return;
      recordUserActivity();
      void maintainSession();
    };
    const activityEvents: Array<keyof WindowEventMap> = [
      'keydown', 'pointerdown', 'touchstart', 'submit',
    ];
    activityEvents.forEach((eventName) => window.addEventListener(eventName, recordActivity, true));
    const intervalId = window.setInterval(() => void maintainSession(), SESSION_CHECK_INTERVAL_MS);
    void maintainSession();
    return () => {
      activityEvents.forEach((eventName) => window.removeEventListener(eventName, recordActivity, true));
      window.clearInterval(intervalId);
    };
  }, [maintainSession, session !== null]);

  useEffect(() => {
    const handleStorage = (event: StorageEvent): void => {
      if (event.storageArea !== localStorage) return;
      if (event.key === AUTH_STORAGE_KEY) {
        if (event.newValue === null) {
          if (sessionRef.current) {
            setSession(null);
            message.warning(t('auth.sessionEndedElsewhere'));
            navigate('/login', { replace: true });
          }
          return;
        }
        const updated = readAuthSession();
        // A login in another tab shares localStorage, but must not turn a
        // tab that is deliberately showing the login screen into ERP. Tabs
        // that already have a session still receive token/role refreshes.
        if (updated && sessionRef.current) setSession(updated);
      }
      if (event.key === AUTH_ACTIVITY_STORAGE_KEY) void maintainSession();
    };
    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, [maintainSession, message, navigate, t]);

  useEffect(() => {
    const handleUnauthorized = (event: Event) => {
      const errorKey = (event as CustomEvent<ApiError>).detail?.errorKey;
      endSession(errorKey === 'AUTH_TOKEN_EXPIRED'
        ? 'auth.sessionExpired'
        : 'auth.sessionInvalidated');
    };

    const handleForbidden = () => {
      if (sessionRef.current?.mustChangePassword) {
        return;
      }
      message.error(t('auth.permissionDenied'));
    };

    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    window.addEventListener(AUTH_FORBIDDEN_EVENT, handleForbidden);
    return () => {
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
      window.removeEventListener(AUTH_FORBIDDEN_EVENT, handleForbidden);
    };
  }, [endSession, message, t]);

  const value = useMemo<AuthContextValue>(
    () => ({ session, login, completeHandoff, logout, switchRole, changePassword, revokeAllSessions }),
    [changePassword, completeHandoff, login, logout, revokeAllSessions, session, switchRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
