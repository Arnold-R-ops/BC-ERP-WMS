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

function ModuleRoute({ module, children }: { module: AppModule; children: JSX.Element }): JSX.Element {
  const { session } = useAuth();
  return session && canAccessModule(session.currentRole, module)
    ? children
    : <Navigate replace to="/" />;
}

function AppRoutes(): JSX.Element {
  return (
    <Suspense fallback={<Spin fullscreen size="large" />}>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route element={<LoginPage />} path="/login" />
        </Route>
        <Route element={<ProtectedRoute />}>
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
            <Route
              element={<ModuleRoute module="sales"><SalesOrderPage /></ModuleRoute>}
              path="sales"
            />
            <Route
              element={<ModuleRoute module="purchasing"><PurchaseOrderPage /></ModuleRoute>}
              path="purchasing"
            />
            <Route
              element={<ModuleRoute module="inbound"><InboundOrderPage /></ModuleRoute>}
              path="inbound"
            />
            <Route element={<ModuleRoute module="integrations"><Navigate replace to="pending" /></ModuleRoute>} path="integrations" />
            <Route element={<ModuleRoute module="integrations"><IntegrationConfigPage /></ModuleRoute>} path="integrations/configs" />
            <Route element={<ModuleRoute module="integrations"><PendingSkuMappingPage /></ModuleRoute>} path="integrations/pending" />
            <Route element={<ModuleRoute module="integrations"><SkuMappingPage /></ModuleRoute>} path="integrations/mappings" />
            <Route element={<ModuleRoute module="integrations"><RawEventPage /></ModuleRoute>} path="integrations/reviews" />
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
          colorPrimary: '#0f766e',
          colorInfo: '#2563eb',
          fontFamily: 'Inter, "Segoe UI", "Microsoft YaHei", sans-serif',
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
