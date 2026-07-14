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
  login as loginRequest,
  switchRole as switchRoleRequest,
  type ChangeMyPasswordRequest,
  type LoginRequest,
} from '../api/auth';
import {
  AUTH_FORBIDDEN_EVENT,
  AUTH_UNAUTHORIZED_EVENT,
} from '../api/http';
import {
  clearAuthSession,
  type AuthSession,
  readAuthSession,
  writeAuthSession,
} from './storage';
import { createAuthSession, mergeRoleSwitchSession } from './session';

interface AuthContextValue {
  session: AuthSession | null;
  login: (request: LoginRequest) => Promise<AuthSession>;
  logout: () => void;
  switchRole: (targetRoleCode: string) => Promise<void>;
  changePassword: (request: ChangeMyPasswordRequest) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
  const [session, setSession] = useState<AuthSession | null>(() => readAuthSession());
  const sessionRef = useRef(session);
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const logout = useCallback(() => {
    clearAuthSession();
    setSession(null);
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const nextSession = createAuthSession(await loginRequest(request));
    writeAuthSession(nextSession);
    setSession(nextSession);
    return nextSession;
  }, []);

  const switchRole = useCallback(async (targetRoleCode: string) => {
    const currentSession = sessionRef.current;
    if (!currentSession) {
      return;
    }

    const response = await switchRoleRequest({ targetRoleCode });
    const nextSession = mergeRoleSwitchSession(currentSession, response);
    writeAuthSession(nextSession);
    setSession(nextSession);
  }, []);

  const changePassword = useCallback(async (request: ChangeMyPasswordRequest) => {
    await changeMyPasswordRequest(request);
    logout();
  }, [logout]);

  useEffect(() => {
    const handleUnauthorized = () => {
      logout();
      message.warning(t('auth.sessionExpired'));
      navigate('/login', { replace: true });
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
  }, [logout, message, navigate, t]);

  const value = useMemo<AuthContextValue>(
    () => ({ session, login, logout, switchRole, changePassword }),
    [changePassword, login, logout, session, switchRole],
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
