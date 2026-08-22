import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { PLATFORM_AUTH_UNAUTHORIZED_EVENT, platformLogin, type PlatformAuthResponse, type PlatformLoginRequest } from './api';
import {
  clearPlatformSession,
  readPlatformSession,
  type PlatformSession,
  writePlatformSession,
} from './storage';

interface PlatformAuthContextValue {
  session: PlatformSession | null;
  sessionNotice: 'unauthorized' | null;
  clearSessionNotice: () => void;
  login: (request: PlatformLoginRequest) => Promise<PlatformAuthResponse>;
  establishSession: (response: PlatformAuthResponse) => void;
  logout: () => void;
}

const PlatformAuthContext = createContext<PlatformAuthContextValue | null>(null);

export function PlatformAuthProvider({ children }: PropsWithChildren): JSX.Element {
  const [session, setSession] = useState<PlatformSession | null>(() => readPlatformSession());
  const [sessionNotice, setSessionNotice] = useState<'unauthorized' | null>(null);

  const login = useCallback(async (request: PlatformLoginRequest) => {
    const response = await platformLogin(request);
    return response;
  }, []);

  const establishSession = useCallback((response: PlatformAuthResponse) => {
    if (response.status !== 'AUTHENTICATED' || !response.token || !response.expiresIn) {
      throw new Error('PLATFORM_AUTHENTICATION_INCOMPLETE');
    }
    const nextSession: PlatformSession = {
      token: response.token,
      tokenType: response.tokenType || 'Bearer',
      email: response.email,
      roles: response.roles,
      expiresAt: Date.now() + response.expiresIn,
    };
    writePlatformSession(nextSession);
    setSession(nextSession);
    setSessionNotice(null);
  }, []);

  const logout = useCallback(() => {
    clearPlatformSession();
    setSession(null);
    setSessionNotice(null);
  }, []);

  const invalidateSession = useCallback(() => {
    clearPlatformSession();
    setSession(null);
    setSessionNotice('unauthorized');
  }, []);

  const clearSessionNotice = useCallback(() => setSessionNotice(null), []);

  useEffect(() => {
    window.addEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, invalidateSession);
    return () => window.removeEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, invalidateSession);
  }, [invalidateSession]);

  const value = useMemo(
    () => ({ session, sessionNotice, clearSessionNotice, login, establishSession, logout }),
    [clearSessionNotice, establishSession, login, logout, session, sessionNotice],
  );
  return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>;
}

export function usePlatformAuth(): PlatformAuthContextValue {
  const context = useContext(PlatformAuthContext);
  if (!context) throw new Error('usePlatformAuth must be used inside PlatformAuthProvider');
  return context;
}
