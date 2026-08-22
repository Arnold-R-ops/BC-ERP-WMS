import { App as AntdApp, ConfigProvider, Spin, theme } from 'antd';
import { enUSIntl, ProConfigProvider, zhCNIntl } from '@ant-design/pro-components';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { HashRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { canAccessModule, type AppModule } from './access';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { LoginPage } from './pages/LoginPage';
import { SessionHandoffPage } from './pages/SessionHandoffPage';

const AppLayout = lazy(() =>
  import('./layouts/AppLayout').then((module) => ({ default: module.AppLayout })),
);
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
);
const ProductPage = lazy(() =>
  import('./pages/products/ProductPage').then((module) => ({ default: module.ProductPage })),
);
const CategoryPage = lazy(() =>
  import('./pages/categories/CategoryPage').then((module) => ({ default: module.CategoryPage })),
);
const InventoryPage = lazy(() =>
  import('./pages/inventory/InventoryPage').then((module) => ({ default: module.InventoryPage })),
);
const SalesOrderPage = lazy(() =>
  import('./pages/sales/SalesOrderPage').then((module) => ({ default: module.SalesOrderPage })),
);
const PurchaseOrderPage = lazy(() =>
  import('./pages/purchasing/PurchaseOrderPage').then((module) => ({ default: module.PurchaseOrderPage })),
);
const InboundOrderPage = lazy(() =>
  import('./pages/inbound/InboundOrderPage').then((module) => ({ default: module.InboundOrderPage })),
);
const OutboundTaskPage = lazy(() =>
  import('./pages/outbound/OutboundTaskPage').then((module) => ({ default: module.OutboundTaskPage })),
);
const StocktakePage = lazy(() =>
  import('./pages/stocktake/StocktakePage').then((module) => ({ default: module.StocktakePage })),
);
const EmergencyCorrectionPage = lazy(() =>
  import('./pages/corrections/EmergencyCorrectionPage').then((module) => ({ default: module.EmergencyCorrectionPage })),
);
const WarehousePage = lazy(() =>
  import('./pages/warehouseSetup/WarehousePage').then((module) => ({ default: module.WarehousePage })),
);
const LocationPage = lazy(() =>
  import('./pages/warehouseSetup/LocationPage').then((module) => ({ default: module.LocationPage })),
);
const UserManagementPage = lazy(() =>
  import('./pages/iam/UserManagementPage').then((module) => ({ default: module.UserManagementPage })),
);
const RolePermissionPage = lazy(() =>
  import('./pages/iam/RolePermissionPage').then((module) => ({ default: module.RolePermissionPage })),
);
const PermissionRequestPage = lazy(() =>
  import('./pages/iam/PermissionRequestPage').then((module) => ({ default: module.PermissionRequestPage })),
);
const SessionSecurityPage = lazy(() =>
  import('./pages/security/SessionSecurityPage').then((module) => ({ default: module.SessionSecurityPage })),
);
const IntegrationConfigPage = lazy(() =>
  import('./pages/integrations/IntegrationConfigPage').then((module) => ({ default: module.IntegrationConfigPage })),
);
const PendingSkuMappingPage = lazy(() =>
  import('./pages/integrations/PendingSkuMappingPage').then((module) => ({ default: module.PendingSkuMappingPage })),
);
const SkuMappingPage = lazy(() =>
  import('./pages/integrations/SkuMappingPage').then((module) => ({ default: module.SkuMappingPage })),
);
const RawEventPage = lazy(() =>
  import('./pages/integrations/RawEventPage').then((module) => ({ default: module.RawEventPage })),
);
const ClientPage = lazy(() =>
  import('./pages/customers/ClientPage').then((module) => ({ default: module.ClientPage })),
);
const ConsumerPage = lazy(() =>
  import('./pages/customers/ConsumerPage').then((module) => ({ default: module.ConsumerPage })),
);
const SupplierPage = lazy(() =>
  import('./pages/suppliers/SupplierPage').then((module) => ({ default: module.SupplierPage })),
);
const ReconciliationPage = lazy(() =>
  import('./pages/integrations/ReconciliationPage').then((module) => ({ default: module.ReconciliationPage })),
);
const SalesAnalyticsPage = lazy(() =>
  import('./pages/analytics/SalesAnalyticsPage').then((module) => ({ default: module.SalesAnalyticsPage })),
);
const CustomerAnalyticsPage = lazy(() =>
  import('./pages/analytics/CustomerAnalyticsPage').then((module) => ({ default: module.CustomerAnalyticsPage })),
);
const loadMobileReceivingPage = () => import('./pages/mobile/MobileReceivingPage');
const loadMobilePickingPage = () => import('./pages/mobile/MobilePickingPage');
const loadMobileStocktakePage = () => import('./pages/mobile/MobileStocktakePage');
const WarehouseMobileLayout = lazy(() =>
  import('./pages/mobile/WarehouseMobileLayout').then((module) => ({ default: module.WarehouseMobileLayout })),
);
const MobileReceivingPage = lazy(() =>
  loadMobileReceivingPage().then((module) => ({ default: module.MobileReceivingPage })),
);
const MobilePickingPage = lazy(() =>
  loadMobilePickingPage().then((module) => ({ default: module.MobilePickingPage })),
);
const MobileStocktakePage = lazy(() =>
  loadMobileStocktakePage().then((module) => ({ default: module.MobileStocktakePage })),
);
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
    },
    mutations: { retry: false },
  },
});

function ProtectedRoute(): JSX.Element {
  const { session } = useAuth();
  return session ? <Outlet /> : <Navigate replace to="/login" />;
}

function PublicOnlyRoute(): JSX.Element {
  const { session } = useAuth();
  return session ? <Navigate replace to="/" /> : <Outlet />;
}

function AccessDeniedRedirect(): JSX.Element {
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  useEffect(() => {
    message.warning(t('auth.pagePermissionDenied'));
  }, [message, t]);
  return <Navigate replace to="/" />;
}

function ModuleRoute({ module, children }: { module: AppModule; children: JSX.Element }): JSX.Element {
  const { session } = useAuth();
  return session && canAccessModule(session.currentRole, module, session.permissionCodes)
    ? children
    : <AccessDeniedRedirect />;
}

function WarehouseMobileEntry(): JSX.Element {
  useEffect(() => {
    void Promise.all([
      loadMobileReceivingPage(),
      loadMobilePickingPage(),
      loadMobileStocktakePage(),
    ]);
  }, []);
  return <WarehouseMobileLayout />;
}

function IntegrationEntry(): JSX.Element {
  const { session } = useAuth();
  if (!session) return <Navigate replace to="/login" />;
  return canAccessModule(session.currentRole, 'integrationAdmin', session.permissionCodes)
    ? <Navigate replace to="pending" />
    : <Navigate replace to="reconciliation" />;
}

function AppRoutes(): JSX.Element {
  return (
    <Suspense fallback={<Spin fullscreen size="large" />}>
      <Routes>
        <Route element={<SessionHandoffPage />} path="/session/handoff" />
        <Route element={<PublicOnlyRoute />}>
          <Route element={<LoginPage />} path="/login" />
        </Route>
        <Route element={<ProtectedRoute />}>
          <Route element={<ModuleRoute module="warehouseMobile"><WarehouseMobileEntry /></ModuleRoute>} path="mobile">
            <Route element={<Navigate replace to="picking" />} index />
            <Route element={<MobileReceivingPage />} path="receiving" />
            <Route element={<MobilePickingPage />} path="picking" />
            <Route element={<MobileStocktakePage />} path="stocktake" />
          </Route>
          <Route element={<AppLayout />}>
            <Route element={<DashboardPage />} index />
            <Route
              element={<ModuleRoute module="products"><ProductPage /></ModuleRoute>}
              path="products"
            />
            <Route element={<ModuleRoute module="products"><Navigate replace to="/products" /></ModuleRoute>} path="catalog" />
            <Route
              element={<ModuleRoute module="products"><CategoryPage /></ModuleRoute>}
              path="categories"
            />
            <Route
              element={<ModuleRoute module="inventory"><InventoryPage /></ModuleRoute>}
              path="inventory"
            />
            <Route element={<ModuleRoute module="masterData"><Navigate replace to="clients" /></ModuleRoute>} path="master-data" />
            <Route element={<ModuleRoute module="customers"><ClientPage /></ModuleRoute>} path="master-data/clients" />
            <Route element={<ModuleRoute module="customers"><ConsumerPage /></ModuleRoute>} path="master-data/consumers" />
            <Route element={<ModuleRoute module="suppliers"><SupplierPage /></ModuleRoute>} path="master-data/suppliers" />
            <Route
              element={<ModuleRoute module="sales"><SalesOrderPage /></ModuleRoute>}
              path="sales"
            />
            <Route element={<ModuleRoute module="analytics"><Navigate replace to="sales" /></ModuleRoute>} path="analytics" />
            <Route element={<ModuleRoute module="analytics"><SalesAnalyticsPage /></ModuleRoute>} path="analytics/sales" />
            <Route element={<ModuleRoute module="analytics"><CustomerAnalyticsPage /></ModuleRoute>} path="analytics/customers" />
            <Route
              element={<ModuleRoute module="purchasing"><PurchaseOrderPage /></ModuleRoute>}
              path="purchasing"
            />
            <Route
              element={<ModuleRoute module="inbound"><InboundOrderPage /></ModuleRoute>}
              path="inbound"
            />
            <Route
              element={<ModuleRoute module="outbound"><OutboundTaskPage /></ModuleRoute>}
              path="outbound"
            />
            <Route element={<ModuleRoute module="stocktake"><Navigate replace to="stocktake" /></ModuleRoute>} path="inventory-governance" />
            <Route element={<ModuleRoute module="stocktake"><StocktakePage /></ModuleRoute>} path="inventory-governance/stocktake" />
            <Route element={<ModuleRoute module="inventoryCorrection"><EmergencyCorrectionPage /></ModuleRoute>} path="inventory-governance/corrections" />
            <Route element={<ModuleRoute module="warehouseSetup"><Navigate replace to="warehouses" /></ModuleRoute>} path="warehouse-setup" />
            <Route element={<ModuleRoute module="warehouseSetup"><WarehousePage /></ModuleRoute>} path="warehouse-setup/warehouses" />
            <Route element={<ModuleRoute module="warehouseSetup"><LocationPage /></ModuleRoute>} path="warehouse-setup/locations" />
            <Route element={<ModuleRoute module="security"><Navigate replace to="security" /></ModuleRoute>} path="system" />
            <Route element={<ModuleRoute module="security"><SessionSecurityPage /></ModuleRoute>} path="system/security" />
            <Route element={<ModuleRoute module="iam"><UserManagementPage /></ModuleRoute>} path="system/users" />
            <Route element={<ModuleRoute module="iam"><RolePermissionPage /></ModuleRoute>} path="system/roles" />
            <Route element={<ModuleRoute module="iam"><PermissionRequestPage /></ModuleRoute>} path="system/permission-requests" />
            <Route element={<ModuleRoute module="integrations"><IntegrationEntry /></ModuleRoute>} path="integrations" />
            <Route element={<ModuleRoute module="integrationAdmin"><IntegrationConfigPage /></ModuleRoute>} path="integrations/configs" />
            <Route element={<ModuleRoute module="integrationAdmin"><PendingSkuMappingPage /></ModuleRoute>} path="integrations/pending" />
            <Route element={<ModuleRoute module="integrationAdmin"><SkuMappingPage /></ModuleRoute>} path="integrations/mappings" />
            <Route element={<ModuleRoute module="integrationAdmin"><RawEventPage /></ModuleRoute>} path="integrations/reviews" />
            <Route element={<ModuleRoute module="reconciliation"><ReconciliationPage /></ModuleRoute>} path="integrations/reconciliation" />
          </Route>
        </Route>
        <Route element={<Navigate replace to="/" />} path="*" />
      </Routes>
    </Suspense>
  );
}

export function App(): JSX.Element {
  const { i18n, t } = useTranslation();
  const locale = i18n.language === 'en-US' ? enUS : zhCN;
  const proLocale = i18n.language === 'en-US' ? enUSIntl : zhCNIntl;

  useEffect(() => {
    document.documentElement.lang = i18n.language;
    document.title = t('app.name');
  }, [i18n.language, t]);

  return (
    <ConfigProvider
      locale={locale}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          borderRadius: 6,
          colorBgBase: '#ffffff',
          colorBgContainer: '#ffffff',
          colorBgElevated: '#ffffff',
          colorBgLayout: '#ffffff',
          colorBorder: '#d9d9d9',
          colorBorderSecondary: '#e5e7eb',
          colorPrimary: '#115095',
          colorInfo: '#2563eb',
          colorText: '#172525',
          colorTextSecondary: '#526866',
          fontFamily: 'Inter, "Segoe UI", "Microsoft YaHei", sans-serif',
        },
        components: {
          Card: {
            colorBorderSecondary: '#d9d9d9',
            colorBgContainer: '#ffffff',
          },
          Table: {
            borderColor: '#e5e7eb',
            colorBgContainer: '#ffffff',
            headerBg: '#f5f7fa',
            headerColor: '#172525',
            rowHoverBg: '#f3f7fb',
          },
        },
      }}
    >
      <ProConfigProvider intl={proLocale}>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <HashRouter
              future={{
                v7_relativeSplatPath: true,
                v7_startTransition: true,
              }}
            >
              <AuthProvider>
                <AppRoutes />
              </AuthProvider>
            </HashRouter>
          </QueryClientProvider>
        </AntdApp>
      </ProConfigProvider>
    </ConfigProvider>
  );
}
