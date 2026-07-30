import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  ControlOutlined,
  DatabaseOutlined,
  ExportOutlined,
  EnvironmentOutlined,
  HomeOutlined,
  InboxOutlined,
  LinkOutlined,
  LineChartOutlined,
  LogoutOutlined,
  MenuOutlined,
  ProductOutlined,
  SafetyCertificateOutlined,
  ScanOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  TruckOutlined,
  UserOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import { ProLayout, type MenuDataItem } from '@ant-design/pro-components';
import { Button, Drawer, Menu, Tag, Tooltip, type MenuProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { canAccessModule, type AppModule } from '../access';
import { useAuth } from '../auth/AuthProvider';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { PasswordChangeModal } from '../components/PasswordChangeModal';
import { RoleSwitcher } from '../components/RoleSwitcher';

interface MenuDefinition {
  path: string;
  module: AppModule;
  nameKey: string;
  icon: JSX.Element;
  delivered: boolean;
  children?: MenuDefinition[];
}

const menuDefinitions: MenuDefinition[] = [
  { path: '/', module: 'dashboard', nameKey: 'menu.dashboard', icon: <AppstoreOutlined />, delivered: true },
  {
    path: '/catalog', module: 'products', nameKey: 'menu.productOperations', icon: <ProductOutlined />, delivered: true,
    children: [
      { path: '/products', module: 'products', nameKey: 'menu.products', icon: <ProductOutlined />, delivered: true },
      { path: '/categories', module: 'products', nameKey: 'menu.categories', icon: <TagsOutlined />, delivered: true },
    ],
  },
  {
    path: '/master-data', module: 'masterData', nameKey: 'menu.masterData', icon: <TeamOutlined />, delivered: true,
    children: [
      { path: '/master-data/clients', module: 'customers', nameKey: 'menu.clients', icon: <TeamOutlined />, delivered: true },
      { path: '/master-data/consumers', module: 'customers', nameKey: 'menu.consumers', icon: <UserOutlined />, delivered: true },
      { path: '/master-data/suppliers', module: 'suppliers', nameKey: 'menu.suppliers', icon: <TruckOutlined />, delivered: true },
    ],
  },
  { path: '/inventory', module: 'inventory', nameKey: 'menu.inventory', icon: <DatabaseOutlined />, delivered: true },
  { path: '/sales', module: 'sales', nameKey: 'menu.sales', icon: <ShoppingCartOutlined />, delivered: true },
  {
    path: '/analytics', module: 'analytics', nameKey: 'menu.analytics', icon: <LineChartOutlined />, delivered: true,
    children: [
      { path: '/analytics/sales', module: 'analytics', nameKey: 'menu.salesAnalytics', icon: <LineChartOutlined />, delivered: true },
      { path: '/analytics/customers', module: 'analytics', nameKey: 'menu.customerAnalytics', icon: <UsergroupAddOutlined />, delivered: true },
    ],
  },
  { path: '/purchasing', module: 'purchasing', nameKey: 'menu.purchasing', icon: <TruckOutlined />, delivered: true },
  { path: '/inbound', module: 'inbound', nameKey: 'menu.inbound', icon: <InboxOutlined />, delivered: true },
  { path: '/outbound', module: 'outbound', nameKey: 'menu.outbound', icon: <ExportOutlined />, delivered: true },
  {
    path: '/mobile', module: 'warehouseMobile', nameKey: 'menu.mobileOperations', icon: <ScanOutlined />, delivered: true,
    children: [
      { path: '/mobile/receiving', module: 'warehouseMobile', nameKey: 'mobile.receiving.nav', icon: <InboxOutlined />, delivered: true },
      { path: '/mobile/picking', module: 'warehouseMobile', nameKey: 'mobile.picking.nav', icon: <ExportOutlined />, delivered: true },
      { path: '/mobile/stocktake', module: 'warehouseMobile', nameKey: 'mobile.stocktake.nav', icon: <AuditOutlined />, delivered: true },
    ],
  },
  {
    path: '/inventory-governance', module: 'stocktake', nameKey: 'menu.inventoryGovernance', icon: <SafetyCertificateOutlined />, delivered: true,
    children: [
      { path: '/inventory-governance/stocktake', module: 'stocktake', nameKey: 'menu.stocktake', icon: <AuditOutlined />, delivered: true },
      { path: '/inventory-governance/corrections', module: 'inventoryCorrection', nameKey: 'menu.emergencyCorrections', icon: <ThunderboltOutlined />, delivered: true },
    ],
  },
  {
    path: '/warehouse-setup', module: 'warehouseSetup', nameKey: 'menu.warehouseSetup', icon: <HomeOutlined />, delivered: true,
    children: [
      { path: '/warehouse-setup/warehouses', module: 'warehouseSetup', nameKey: 'menu.warehouses', icon: <HomeOutlined />, delivered: true },
      { path: '/warehouse-setup/locations', module: 'warehouseSetup', nameKey: 'menu.locations', icon: <EnvironmentOutlined />, delivered: true },
    ],
  },
  {
    path: '/system', module: 'iam', nameKey: 'menu.systemManagement', icon: <ControlOutlined />, delivered: true,
    children: [
      { path: '/system/users', module: 'iam', nameKey: 'menu.userManagement', icon: <TeamOutlined />, delivered: true },
      { path: '/system/roles', module: 'iam', nameKey: 'menu.rolePermissions', icon: <SafetyCertificateOutlined />, delivered: true },
    ],
  },
  {
    path: '/integrations', module: 'integrations', nameKey: 'menu.integrations', icon: <SettingOutlined />, delivered: true,
    children: [
      { path: '/integrations/configs', module: 'integrations', nameKey: 'menu.integrationConfigs', icon: <ShopOutlined />, delivered: true },
      { path: '/integrations/pending', module: 'integrations', nameKey: 'menu.pendingMappings', icon: <ApartmentOutlined />, delivered: true },
      { path: '/integrations/mappings', module: 'integrations', nameKey: 'menu.skuMappings', icon: <LinkOutlined />, delivered: true },
      { path: '/integrations/reviews', module: 'integrations', nameKey: 'menu.manualReviews', icon: <AuditOutlined />, delivered: true },
      { path: '/integrations/reconciliation', module: 'integrations', nameKey: 'menu.reconciliation', icon: <SafetyCertificateOutlined />, delivered: true },
    ],
  },
];

function buildMobileMenuItems(routes: MenuDataItem[]): MenuProps['items'] {
  return routes.map((route) => ({
    key: route.path ?? route.name ?? '',
    icon: route.icon,
    label: route.name,
    children: route.children?.length ? buildMobileMenuItems(route.children) : undefined,
  }));
}

function useMobileViewport(): boolean {
  const query = '(max-width: 767px)';
  const [mobile, setMobile] = useState(() => (
    typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia(query).matches
  ));

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const mediaQuery = window.matchMedia(query);
    const update = (event: MediaQueryListEvent): void => setMobile(event.matches);
    setMobile(mediaQuery.matches);
    mediaQuery.addEventListener('change', update);
    return () => mediaQuery.removeEventListener('change', update);
  }, []);

  return mobile;
}

export function AppLayout(): JSX.Element {
  const { session, logout } = useAuth();
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [openMenuKeys, setOpenMenuKeys] = useState<string[]>([]);
  const mobileViewport = useMobileViewport();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const routes = useMemo<MenuDataItem[]>(() => {
    if (!session) {
      return [];
    }
    return menuDefinitions
      .filter((item) => item.delivered && canAccessModule(session.currentRole, item.module))
      .map((item) => ({
        path: item.path,
        name: t(item.nameKey),
        icon: item.icon,
        children: item.children
          ?.filter((child) => child.delivered && canAccessModule(session.currentRole, child.module))
          .map((child) => ({ path: child.path, name: t(child.nameKey), icon: child.icon })),
      }));
  }, [session, t]);
  const mobileMenuItems = useMemo(() => buildMobileMenuItems(routes), [routes]);

  useEffect(() => {
    const activeRoot = menuDefinitions.find((item) =>
      item.children?.some((child) => location.pathname === child.path || location.pathname.startsWith(`${child.path}/`)),
    );
    setOpenMenuKeys(activeRoot ? [activeRoot.path] : []);
    setMobileNavigationOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!mobileViewport) setMobileNavigationOpen(false);
  }, [mobileViewport]);

  if (!session) {
    return <Outlet />;
  }

  const handleLogout = (): void => {
    setMobileNavigationOpen(false);
    logout();
    navigate('/login', { replace: true });
  };
  const currentRoleLabel = t(`roles.${session.currentRole}`, { defaultValue: session.currentRole });

  return (
    <>
      {mobileViewport ? (
        <div className="mobile-admin-shell">
          <header className="mobile-app-header">
            <Button
              aria-label={t('common.openNavigation')}
              className="mobile-navigation-trigger"
              icon={<MenuOutlined />}
              onClick={() => setMobileNavigationOpen(true)}
              type="text"
            />
            <strong>{t('app.shortName')}</strong>
            <span className="mobile-app-role" title={currentRoleLabel}>
              <SafetyCertificateOutlined />
              <span>{currentRoleLabel}</span>
            </span>
          </header>
          <main className="mobile-admin-content">
            <Outlet />
          </main>
        </div>
      ) : (
        <ProLayout
          actionsRender={() => [
            <LanguageSwitcher key="language" />,
            <RoleSwitcher key="role" />,
            <Tag bordered={false} className="header-user-tag" icon={<UserOutlined />} key="user">
              {session.username}
            </Tag>,
            <Tooltip key="change-password" title={t('common.changePassword')}>
              <Button
                aria-label={t('common.changePassword')}
                className="header-action-button"
                icon={<SafetyCertificateOutlined />}
                onClick={() => setPasswordModalOpen(true)}
                type="text"
              >
                <span className="header-action-label">{t('common.changePassword')}</span>
              </Button>
            </Tooltip>,
            <Tooltip key="logout" title={t('common.logout')}>
              <Button
                aria-label={t('common.logout')}
                className="header-logout-button"
                danger
                icon={<LogoutOutlined />}
                onClick={handleLogout}
                type="text"
              >
                <span className="header-action-label">{t('common.logout')}</span>
              </Button>
            </Tooltip>,
          ]}
          contentStyle={{ padding: 20 }}
          fixSiderbar
          fixedHeader
          layout="mix"
          location={{ pathname: location.pathname }}
          logo={false}
          menuItemRender={(item, dom) => item.children?.length ? dom : item.path ? <Link to={item.path}>{dom}</Link> : dom}
          menuProps={{
            openKeys: openMenuKeys,
            onOpenChange: (keys) => {
              const nextKeys = keys.map(String);
              const newlyOpened = nextKeys.find((key) => !openMenuKeys.includes(key));
              setOpenMenuKeys(newlyOpened ? [newlyOpened] : []);
            },
          }}
          navTheme="realDark"
          route={{ path: '/', routes }}
          siderWidth={232}
          title={t('app.shortName')}
          token={{
            header: {
              colorBgHeader: '#ffffff',
              colorBgRightActionsItemHover: '#e8f3f1',
              colorTextRightActionsItem: '#183b3a',
            },
            sider: {
              colorBgMenuItemHover: '#174445',
              colorBgMenuItemSelected: '#0f766e',
              colorMenuBackground: '#102a2b',
              colorTextMenu: '#d8e7e5',
              colorTextMenuActive: '#ffffff',
              colorTextMenuSelected: '#ffffff',
            },
          }}
        >
          <Outlet />
        </ProLayout>
      )}

      <Drawer
        className="mobile-navigation-drawer"
        extra={<LanguageSwitcher />}
        onClose={() => setMobileNavigationOpen(false)}
        open={mobileViewport && mobileNavigationOpen}
        placement="left"
        title={(
          <div className="mobile-navigation-title">
            <strong>{t('app.shortName')}</strong>
            <span>{session.username} · {currentRoleLabel}</span>
          </div>
        )}
        width="min(86vw, 360px)"
      >
        <div className="mobile-navigation-content">
          <Menu
            items={mobileMenuItems}
            mode="inline"
            onClick={({ key }) => {
              setMobileNavigationOpen(false);
              navigate(key);
            }}
            onOpenChange={(keys) => {
              const nextKeys = keys.map(String);
              const newlyOpened = nextKeys.find((key) => !openMenuKeys.includes(key));
              setOpenMenuKeys(newlyOpened ? [newlyOpened] : []);
            }}
            openKeys={openMenuKeys}
            selectedKeys={[location.pathname]}
          />
          <div className="mobile-navigation-footer">
            <RoleSwitcher />
            <Button
              icon={<SafetyCertificateOutlined />}
              onClick={() => {
                setMobileNavigationOpen(false);
                setPasswordModalOpen(true);
              }}
              type="text"
            >
              {t('common.changePassword')}
            </Button>
            <Button danger icon={<LogoutOutlined />} onClick={handleLogout} type="text">
              {t('common.logout')}
            </Button>
          </div>
        </div>
      </Drawer>

      <PasswordChangeModal
        forced={false}
        onClose={() => setPasswordModalOpen(false)}
        open={passwordModalOpen}
      />
      <PasswordChangeModal forced open={session.mustChangePassword} />
    </>
  );
}
