import {
  AuditOutlined,
  DesktopOutlined,
  DisconnectOutlined,
  ExportOutlined,
  InboxOutlined,
  LogoutOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { Alert, Button, Tooltip } from 'antd';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';

interface MobileOperationContextValue {
  online: boolean;
}

const MobileOperationContext = createContext<MobileOperationContextValue>({ online: true });

export function useMobileOperation(): MobileOperationContextValue {
  return useContext(MobileOperationContext);
}

export function WarehouseMobileLayout(): JSX.Element {
  const { logout, session } = useAuth();
  const [online, setOnline] = useState(() => navigator.onLine);
  const navigate = useNavigate();
  const { t } = useTranslation();

  useEffect(() => {
    let active = true;
    let currentCheck: AbortController | undefined;

    const checkBackendReachability = async (): Promise<void> => {
      currentCheck?.abort();
      if (!navigator.onLine) {
        setOnline(false);
        return;
      }

      const controller = new AbortController();
      currentCheck = controller;
      const timeoutId = window.setTimeout(() => controller.abort(), 2_500);
      try {
        const response = await fetch(`/api/auth/health?connectivity=${Date.now()}`, {
          cache: 'no-store',
          credentials: 'same-origin',
          signal: controller.signal,
        });
        if (active && currentCheck === controller) setOnline(response.ok);
      } catch {
        if (active && currentCheck === controller) setOnline(false);
      } finally {
        window.clearTimeout(timeoutId);
        if (currentCheck === controller) currentCheck = undefined;
      }
    };

    const handleOnline = () => void checkBackendReachability();
    const handleOffline = () => {
      currentCheck?.abort();
      setOnline(false);
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') void checkBackendReachability();
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('focus', handleOnline);
    window.addEventListener('pageshow', handleOnline);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    const intervalId = window.setInterval(() => void checkBackendReachability(), 3_000);
    void checkBackendReachability();

    return () => {
      active = false;
      currentCheck?.abort();
      window.clearInterval(intervalId);
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('focus', handleOnline);
      window.removeEventListener('pageshow', handleOnline);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  const context = useMemo(() => ({ online }), [online]);
  const exit = (): void => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <MobileOperationContext.Provider value={context}>
      <div className="mobile-warehouse-shell">
        <header className="mobile-warehouse-header">
          <div className="mobile-warehouse-brand">
            <img alt="" src="./warehouse-mark.svg" />
            <div>
              <strong>{t('mobile.title')}</strong>
              <span>{session?.username} · {t(`roles.${session?.currentRole}`, { defaultValue: session?.currentRole })}</span>
            </div>
          </div>
          <div className="mobile-warehouse-actions">
            <span className={online ? 'mobile-online-state is-online' : 'mobile-online-state'}>
              {online ? <WifiOutlined /> : <DisconnectOutlined />}
              {online ? t('mobile.online') : t('mobile.offline')}
            </span>
            <Tooltip title={t('mobile.desktop')}>
              <Button aria-label={t('mobile.desktop')} icon={<DesktopOutlined />} onClick={() => navigate('/')} type="text" />
            </Tooltip>
            <Tooltip title={t('common.logout')}>
              <Button aria-label={t('common.logout')} danger icon={<LogoutOutlined />} onClick={exit} type="text" />
            </Tooltip>
          </div>
        </header>

        {!online ? (
          <Alert className="mobile-offline-alert" message={t('mobile.offlineNotice')} showIcon type="warning" />
        ) : null}

        <main className="mobile-warehouse-main">
          <Outlet />
        </main>

        <nav aria-label={t('mobile.navigation')} className="mobile-warehouse-nav">
          <NavLink to="/mobile/receiving"><InboxOutlined /><span>{t('mobile.receiving.nav')}</span></NavLink>
          <NavLink to="/mobile/picking"><ExportOutlined /><span>{t('mobile.picking.nav')}</span></NavLink>
          <NavLink to="/mobile/stocktake"><AuditOutlined /><span>{t('mobile.stocktake.nav')}</span></NavLink>
        </nav>
      </div>
    </MobileOperationContext.Provider>
  );
}
