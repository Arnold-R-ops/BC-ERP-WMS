import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../api/emergencyCorrections', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/emergencyCorrections')>(),
  listEmergencyCorrectionBatchOptions: vi.fn(),
}));
vi.mock('../../api/masterData', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/masterData')>(),
  listActiveWarehouses: vi.fn(),
  listLocationsByWarehouse: vi.fn(),
}));
vi.mock('../../api/productSkus', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/productSkus')>(),
  listProductSkus: vi.fn(),
}));

import { listEmergencyCorrectionBatchOptions } from '../../api/emergencyCorrections';
import { listActiveWarehouses, listLocationsByWarehouse } from '../../api/masterData';
import { listProductSkus } from '../../api/productSkus';
import { EmergencyCorrectionCreateModal } from './EmergencyCorrectionCreateModal';

function renderModal(): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <EmergencyCorrectionCreateModal
            loading={false}
            onCancel={vi.fn()}
            onConfirm={vi.fn().mockResolvedValue(undefined)}
            open
          />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('emergency correction creation', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listProductSkus).mockResolvedValue([{
      id: 12,
      skuCode: 'SKU0000012',
      name: '茉莉绿茶',
      enabled: true,
    }]);
    vi.mocked(listActiveWarehouses).mockResolvedValue([]);
    vi.mocked(listLocationsByWarehouse).mockResolvedValue([]);
    vi.mocked(listEmergencyCorrectionBatchOptions).mockResolvedValue([{
      id: 21,
      batchCode: 'OLD-ZERO-21',
      productSkuId: 12,
      quantity: 0,
      reservedQuantity: 0,
      availableQuantity: 0,
      locationId: 31,
      locationCode: 'WH01-A-01',
      active: false,
    }]);
  });

  afterEach(() => {
    cleanup();
    document.body.innerHTML = '';
  });

  it('loads existing batches through the correction-specific option API', async () => {
    renderModal();

    const productSelect = await screen.findByRole('combobox', { name: '商品 / SKU' });
    fireEvent.mouseDown(productSelect);
    fireEvent.click((await screen.findAllByText('SKU0000012 · 茉莉绿茶')).at(-1) as HTMLElement);

    await waitFor(() => expect(listEmergencyCorrectionBatchOptions).toHaveBeenCalledWith(12));

    const batchSelect = screen.getByRole('combobox', { name: '批次码' });
    fireEvent.mouseDown(batchSelect);
    expect((await screen.findAllByText(/OLD-ZERO-21.*已停用，仅允许正向恢复/)).length)
      .toBeGreaterThan(0);
  });
});
