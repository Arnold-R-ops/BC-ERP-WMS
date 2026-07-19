import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DatabaseOutlined,
  InboxOutlined,
  LinkOutlined,
  LogoutOutlined,
  ProductOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TruckOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { ProLayout, type MenuDataItem } from '@ant-design/pro-components';
import { Button, Tag, Tooltip } from 'antd';
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
  { path: '/inventory', module: 'inventory', nameKey: 'menu.inventory', icon: <DatabaseOutlined />, delivered: true },
  { path: '/sales', module: 'sales', nameKey: 'menu.sales', icon: <ShoppingCartOutlined />, delivered: true },
  { path: '/purchasing', module: 'purchasing', nameKey: 'menu.purchasing', icon: <TruckOutlined />, delivered: true },
  { path: '/inbound', module: 'inbound', nameKey: 'menu.inbound', icon: <InboxOutlined />, delivered: true },
  {
    path: '/integrations', module: 'integrations', nameKey: 'menu.integrations', icon: <SettingOutlined />, delivered: true,
    children: [
      { path: '/integrations/configs', module: 'integrations', nameKey: 'menu.integrationConfigs', icon: <ShopOutlined />, delivered: true },
      { path: '/integrations/pending', module: 'integrations', nameKey: 'menu.pendingMappings', icon: <ApartmentOutlined />, delivered: true },
      { path: '/integrations/mappings', module: 'integrations', nameKey: 'menu.skuMappings', icon: <LinkOutlined />, delivered: true },
      { path: '/integrations/reviews', module: 'integrations', nameKey: 'menu.manualReviews', icon: <AuditOutlined />, delivered: true },
    ],
  },
];

export function AppLayout(): JSX.Element {
  const { session, logout } = useAuth();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [openMenuKeys, setOpenMenuKeys] = useState<string[]>([]);
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

  useEffect(() => {
    const activeRoot = menuDefinitions.find((item) =>
      item.children?.some((child) => location.pathname === child.path || location.pathname.startsWith(`${child.path}/`)),
    );
    setOpenMenuKeys(activeRoot ? [activeRoot.path] : []);
  }, [location.pathname]);

  if (!session) {
    return <Outlet />;
  }

  const handleLogout = (): void => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
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
        menuItemRender={(item, dom) => item.path ? <Link to={item.path}>{dom}</Link> : dom}
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

      <PasswordChangeModal
        forced={false}
        onClose={() => setPasswordModalOpen(false)}
        open={passwordModalOpen}
      />
      <PasswordChangeModal forced open={session.mustChangePassword} />
    </>
  );
}
