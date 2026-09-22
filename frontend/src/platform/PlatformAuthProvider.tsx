import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { getPlatformCurrentSession, reportPlatformActivity, revokeCurrentPlatformSession, PLATFORM_AUTH_UNAUTHORIZED_EVENT, platformLogin, type PlatformAuthResponse, type PlatformLoginRequest } from './api';
import { isPlatformActivity, PLATFORM_ACTIVITY_EVENTS, PlatformActivityClock } from './sessionActivity';
import { beginBrowserLogout, browserLogoutFence, finishBrowserLogout, subscribeBrowserLogout, type BrowserLogoutSignal } from './browserLogout';
import {
  clearPlatformSession,
  readPlatformSession,
  type PlatformSession,
  writePlatformSession,
} from './storage';

interface PlatformAuthContextValue {
  session: PlatformSession | null;
  sessionNotice: SessionNotice;
  sessionVerified: boolean;
  clearSessionNotice: () => void;
  login: (request: PlatformLoginRequest) => Promise<PlatformAuthResponse>;
  establishSession: (response: PlatformAuthResponse) => void;
  logout: () => void;
  forgetSession: () => void;
}

type SessionNotice = 'unauthorized' | 'unavailable' | 'logout-pending' | 'logout-unconfirmed' | 'logout-revoked' | 'logout-invalid' | null;

const PlatformAuthContext = createContext<PlatformAuthContextValue | null>(null);

export function PlatformAuthProvider({ children }: PropsWithChildren): JSX.Element {
  const [session, setSession] = useState<PlatformSession | null>(() => readPlatformSession());
  const [sessionNotice, setSessionNotice] = useState<SessionNotice>(null);
  const [verifiedToken, setVerifiedToken] = useState<string | null>(null);
  const sessionRef = useRef(session);
  sessionRef.current = session;
  const generation = useRef(0);
  const pendingBrowserLogout = useRef<{ signal: BrowserLogoutSignal; generation: number } | null>(null);

  const login = useCallback(async (request: PlatformLoginRequest) => {
    const response = await platformLogin(request);
    return response;
  }, []);

  const establishSession = useCallback((response: PlatformAuthResponse) => {
    if (response.status !== 'AUTHENTICATED' || !response.token || !response.expiresIn) {
      throw new Error('PLATFORM_AUTHENTICATION_INCOMPLETE');
    }
    const fence = response.browserLogoutFence ?? browserLogoutFence(response.email);
    if (fence !== browserLogoutFence(response.email)) throw new Error('PLATFORM_LOGIN_INTERRUPTED_BY_LOGOUT');
    const nextSession: PlatformSession = {
      token: response.token,
      tokenType: response.tokenType || 'Bearer',
      email: response.email,
      roles: response.roles,
      expiresAt: Date.now() + response.expiresIn,
      logoutFence: fence,
    };
    writePlatformSession(nextSession);
    generation.current += 1;
    pendingBrowserLogout.current = null;
    sessionRef.current = nextSession;
    setVerifiedToken(null);
    setSession(nextSession);
    setSessionNotice(null);
  }, []);

  const forgetSession = useCallback(() => {
    generation.current += 1;
    clearPlatformSession();
    sessionRef.current = null;
    setSession(null);
    setVerifiedToken(null);
    setSessionNotice(null);
  }, []);

  const logout = useCallback(() => {
    const current = sessionRef.current;
    forgetSession();
    if (!current) return;
    const epoch = generation.current;
    setSessionNotice('logout-pending');
    const signal = beginBrowserLogout(current.email);
    pendingBrowserLogout.current = { signal, generation: epoch };
    void revokeCurrentPlatformSession(current).then(result => {
      finishBrowserLogout(signal, result);
      if (generation.current === epoch) setSessionNotice(result === 'revoked' ? 'logout-revoked' : 'logout-invalid');
    }).catch(() => {
      finishBrowserLogout(signal, 'unconfirmed');
      if (generation.current === epoch) setSessionNotice('logout-unconfirmed');
    });
  }, [forgetSession]);

  const clearSessionNotice = useCallback(() => setSessionNotice(null), []);

  useEffect(() => subscribeBrowserLogout(signal => {
    const current = sessionRef.current;
    if (current && current.email.toLowerCase() === signal.email.toLowerCase()) {
      // A login established after this logout already includes this fence. Ignore delayed messages.
      if ((current.logoutFence || '') === signal.id) return;
      try { if (browserLogoutFence(current.email) !== signal.id) return; } catch { /* Live signal still clears local state. */ }
      forgetSession();
      pendingBrowserLogout.current = { signal, generation: generation.current };
    }
    const pending = pendingBrowserLogout.current;
    if (!pending || pending.signal.id !== signal.id || pending.generation !== generation.current) return;
    setSessionNotice(signal.phase === 'pending' ? 'logout-pending'
      : signal.phase === 'revoked' ? 'logout-revoked' : signal.phase === 'invalid' ? 'logout-invalid' : 'logout-unconfirmed');
  }), [forgetSession]);

  useEffect(() => {
    const invalidateSession = (event: Event) => {
      const token = (event as CustomEvent<{ token?: string }>).detail?.token;
      if (token && token !== sessionRef.current?.token) return;
      forgetSession();
      setSessionNotice('unauthorized');
    };
    window.addEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, invalidateSession);
    return () => window.removeEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, invalidateSession);
  }, [forgetSession]);

  useEffect(() => {
    if (!session) return;
    let disposed = false;
    let busy = false;
    let validated = false;
    const clock = new PlatformActivityClock();
    const update = async (kind: 'check' | 'activity') => {
      if (busy || disposed) return;
      busy = true;
      const started = performance.now();
      if (kind === 'activity') clock.sending(started);
      try {
        const result = await (kind === 'check' ? getPlatformCurrentSession() : reportPlatformActivity());
        if (disposed || sessionRef.current?.token !== session.token) return;
        clock.accept(result, started);
        validated = true;
        setVerifiedToken(session.token);
      } catch {
        if (!disposed && sessionRef.current?.token === session.token) {
          forgetSession();
          setSessionNotice('unavailable');
        }
      } finally { busy = false; }
    };
    const activity = (event: Event) => {
      if (validated && isPlatformActivity(event, document.visibilityState === 'visible')) {
        clock.activity();
        const due = clock.due(performance.now());
        if (due) void update(due);
      }
    };
    const check = () => { if (document.visibilityState === 'visible') void update('check'); };
    // Bootstrap, reload and focus inspect persisted deadlines, never renew them.
    void update('check');
    PLATFORM_ACTIVITY_EVENTS.forEach(name => window.addEventListener(name, activity, { passive: true, capture: true }));
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', check);
    const timer = window.setInterval(() => {
      if (!validated) return;
      const due = clock.due(performance.now());
      if (due) void update(due);
    }, 1000);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      PLATFORM_ACTIVITY_EVENTS.forEach(name => window.removeEventListener(name, activity, true));
      window.removeEventListener('focus', check);
      document.removeEventListener('visibilitychange', check);
    };
  }, [session, forgetSession]);

  const value = useMemo(
    () => ({ session, sessionVerified: !!session && verifiedToken === session.token, sessionNotice, clearSessionNotice, login, establishSession, logout, forgetSession }),
    [clearSessionNotice, establishSession, login, logout, forgetSession, session, sessionNotice, verifiedToken],
  );
  return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>;
}

export function usePlatformAuth(): PlatformAuthContextValue {
  const context = useContext(PlatformAuthContext);
  if (!context) throw new Error('usePlatformAuth must be used inside PlatformAuthProvider');
  return context;
}
