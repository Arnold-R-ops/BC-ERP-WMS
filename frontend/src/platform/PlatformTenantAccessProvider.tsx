import { createContext, type PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';
import { getMyPlatformEffectiveAccess, type PlatformEffectiveAccess } from './api';
import { usePlatformAuth } from './PlatformAuthProvider';

interface PlatformTenantAccessContextValue {
  access: PlatformEffectiveAccess | null;
  loading: boolean;
  error: boolean;
  hasTenantAccess: boolean;
}

const PlatformTenantAccessContext = createContext<PlatformTenantAccessContextValue | null>(null);
const SUPER_ADMIN_ACCESS: PlatformEffectiveAccess = { superAdmin: true, scopes: [] };

export function canAccessTenantManagement(access: PlatformEffectiveAccess | null): boolean {
  return Boolean(access?.superAdmin || access?.scopes.length);
}

export function PlatformTenantAccessProvider({ children }: PropsWithChildren): JSX.Element {
  const { session } = usePlatformAuth();
  const [access, setAccess] = useState<PlatformEffectiveAccess | null>(null);
  const [resolvedToken, setResolvedToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const superAdmin = Boolean(session?.roles.includes('PLATFORM_SUPER_ADMIN'));

  useEffect(() => {
    if (!session) {
      setAccess(null);
      setResolvedToken(null);
      setLoading(false);
      setError(false);
      return undefined;
    }
    if (superAdmin) {
      setAccess(null);
      setResolvedToken(session.token);
      setLoading(false);
      setError(false);
      return undefined;
    }

    let active = true;
    setAccess(null);
    setResolvedToken(session.token);
    setLoading(true);
    setError(false);
    void getMyPlatformEffectiveAccess()
      .then((result) => {
        if (active) setAccess(result);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [session, superAdmin]);

  const accessMatchesSession = Boolean(session && resolvedToken === session.token);
  const effectiveAccess = superAdmin ? SUPER_ADMIN_ACCESS : accessMatchesSession ? access : null;
  const effectiveLoading = Boolean(session && !superAdmin && (!accessMatchesSession || loading));
  const effectiveError = Boolean(!superAdmin && accessMatchesSession && error);
  const value = useMemo<PlatformTenantAccessContextValue>(() => ({
    access: effectiveAccess,
    loading: effectiveLoading,
    error: effectiveError,
    hasTenantAccess: canAccessTenantManagement(effectiveAccess),
  }), [effectiveAccess, effectiveError, effectiveLoading]);

  return <PlatformTenantAccessContext.Provider value={value}>{children}</PlatformTenantAccessContext.Provider>;
}

export function usePlatformTenantAccess(): PlatformTenantAccessContextValue {
  const context = useContext(PlatformTenantAccessContext);
  if (!context) throw new Error('usePlatformTenantAccess must be used inside PlatformTenantAccessProvider');
  return context;
}
