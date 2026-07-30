import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../api/outbound', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/outbound')>(),
  batchConfirmOutboundTasks: vi.fn(),
  confirmOutboundTask: vi.fn(),
  listOutboundTasks: vi.fn(),
}));

vi.mock('../../api/productSkus', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/productSkus')>(),
  getProductSku: vi.fn(),
}));

import { confirmOutboundTask, listOutboundTasks } from '../../api/outbound';
import { getProductSku } from '../../api/productSkus';
import { OutboundTaskPage } from './OutboundTaskPage';

function renderPage(): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <OutboundTaskPage />
          </QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('outbound task workspace', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listOutboundTasks).mockResolvedValue([{
      id: 17,
      salesOrderId: 25,
      salesOrderNo: 'SO20260613002',
      productSkuId: 12,
      productName: 'V4.4 测试茶叶',
      productBarcode: 'TEA-001',
      batchCode: 'BATCH-001',
      locationId: 13,
      locationCode: 'WH-A-01',
      planQty: 50,
      actualQty: 0,
      status: 'PENDING',
      statusDescription: '待拣货',
    }]);
    vi.mocked(getProductSku).mockResolvedValue({
      id: 12,
      skuCode: 'SKU0000012',
      batchTrackingMode: 'PRINTED_LABEL',
    });
  });

  it('loads the route-ordered pending queue and blocks confirmation behind identity verification', async () => {
    renderPage();

    expect(await screen.findByText('SO20260613002')).toBeVisible();
    await waitFor(() => expect(listOutboundTasks).toHaveBeenCalledWith({ status: 'PENDING' }));

    const rowConfirmButton = screen
      .getAllByRole('button', { name: /确认出库/ })
      .find((button) => button.closest('td'));
    expect(rowConfirmButton).toBeDefined();
    fireEvent.click(rowConfirmButton as HTMLButtonElement);
    expect(await screen.findByText('该 SKU 使用批次标签追踪。扫描标签后才能确认扣减。')).toBeInTheDocument();
    expect(await screen.findByPlaceholderText('扫描或输入实物批次码')).toBeInTheDocument();
    expect(confirmOutboundTask).not.toHaveBeenCalled();
  });
});
