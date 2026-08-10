import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import i18n from '../locales/i18n';

vi.mock('../api/masterData', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/masterData')>(),
  listCustomers: vi.fn(),
}));
vi.mock('../api/productSkus', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/productSkus')>(),
  listProductSkus: vi.fn(),
}));
vi.mock('../api/suppliers', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/suppliers')>(),
  listSuppliers: vi.fn(),
}));
vi.mock('../api/integrations', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/integrations')>(),
  listIntegrationConfigs: vi.fn(),
}));

import { listIntegrationConfigs } from '../api/integrations';
import { listCustomers } from '../api/masterData';
import { listProductSkus } from '../api/productSkus';
import { listSuppliers } from '../api/suppliers';
import { ClientPage } from './customers/ClientPage';
import { ConsumerPage } from './customers/ConsumerPage';
import { ReconciliationPage } from './integrations/ReconciliationPage';
import { IntegrationConfigFormModal } from './integrations/IntegrationConfigFormModal';
import { SalesOrderFormDrawer } from './sales/SalesOrderFormDrawer';
import { SupplierPage } from './suppliers/SupplierPage';

function renderPage(element: ReactElement): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <AuthProvider>
            <QueryClientProvider client={queryClient}>{element}</QueryClientProvider>
          </AuthProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('P1.6 page boundaries', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listCustomers).mockResolvedValue([]);
    vi.mocked(listProductSkus).mockResolvedValue([]);
    vi.mocked(listSuppliers).mockResolvedValue([]);
    vi.mocked(listIntegrationConfigs).mockResolvedValue([]);
  });

  it('loads the client workspace through the CLIENT server-side filter', async () => {
    renderPage(<ClientPage />);
    expect(await screen.findByText('企业客户')).toBeVisible();
    await waitFor(() => expect(listCustomers).toHaveBeenCalledWith(false, 'CLIENT'));
  });

  it('loads a separate read-only consumer workspace', async () => {
    renderPage(<ConsumerPage />);
    expect(await screen.findByText('消费者档案')).toBeVisible();
    await waitFor(() => expect(listCustomers).toHaveBeenCalledWith(false, 'CONSUMER'));
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
  });

  it('loads supplier master data independently', async () => {
    renderPage(<SupplierPage />);
    expect(await screen.findByText('供应商')).toBeVisible();
    await waitFor(() => expect(listSuppliers).toHaveBeenCalledWith(false));
  });

  it('only requests active clients for a manual sales order', async () => {
    renderPage(
      <SalesOrderFormDrawer
        loading={false}
        onClose={() => undefined}
        onSubmit={async () => true}
        open
      />,
    );
    await waitFor(() => expect(listCustomers).toHaveBeenCalledWith(true, 'CLIENT'));
    expect(await screen.findByText('本次收货信息')).toBeVisible();
  });

  it('renders the reconciliation control without running an external operation', async () => {
    renderPage(<ReconciliationPage />);
    expect(await screen.findByText('Shopify 订单对账')).toBeVisible();
    await waitFor(() => expect(listIntegrationConfigs).toHaveBeenCalledTimes(1));
    expect(screen.getByText('外部渠道只读')).toBeVisible();
  });

  it('only exposes retail-mode configuration to super administrators', async () => {
    const { unmount } = render(
      <ConfigProvider>
        <AntdApp>
          <IntegrationConfigFormModal
            canManageRetailMode={false}
            loading={false}
            onClose={() => undefined}
            onSubmit={async () => true}
            open
          />
        </AntdApp>
      </ConfigProvider>,
    );
    expect(screen.queryByText('自动建立消费者档案')).not.toBeInTheDocument();
    unmount();

    render(
      <ConfigProvider>
        <AntdApp>
          <IntegrationConfigFormModal
            canManageRetailMode
            loading={false}
            onClose={() => undefined}
            onSubmit={async () => true}
            open
          />
        </AntdApp>
      </ConfigProvider>,
    );
    expect(await screen.findByText('自动建立消费者档案')).toBeInTheDocument();
  });
});
