import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import { getPurchaseOrderAllocations, listPurchaseReferenceSkus, type PurchaseOrder } from '../../api/purchasing';
import i18n from '../../locales/i18n';
import { PurchaseConfirmModal } from './PurchaseConfirmModal';

vi.mock('../../api/purchasing', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/purchasing')>(),
  getPurchaseOrderAllocations: vi.fn(),
  listPurchaseReferenceSkus: vi.fn(),
}));

const order: PurchaseOrder = {
  id: 9,
  version: 3,
  poNumber: 'PO-20260828-09',
  status: 'AWAITING_SHIPMENT',
  items: [{ id: 21, productSkuId: 12, productName: '茉莉绿茶', productBarcode: 'SKU-12', orderedQuantity: 7 }],
};

function renderModal(props: Partial<ComponentProps<typeof PurchaseConfirmModal>> = {}): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <PurchaseConfirmModal
            intent="draft"
            loading={false}
            onCancel={vi.fn()}
            onConfirm={vi.fn()}
            open
            order={order}
            {...props}
          />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('purchase ASN draft editor', () => {
  afterEach(() => cleanup());

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(getPurchaseOrderAllocations).mockResolvedValue([{
      id: 31,
      purchaseOrderItemId: 21,
      warehouseId: 4,
      warehouseCode: 'WH-04',
      plannedQuantity: 7,
      availableToShipQuantity: 7,
      shippedQuantity: 0,
      receivedQuantity: 0,
    }]);
    vi.mocked(listPurchaseReferenceSkus).mockResolvedValue([{
      id: 12,
      name: '茉莉绿茶 500g/袋',
      skuCode: 'SKU-12',
      barcode: '6900000012',
      expiryManagementEnabled: false,
    }]);
  });

  it('saves an advanced ASN as a draft without using the quick-confirm action', async () => {
    const onConfirm = vi.fn();
    renderModal({ onConfirm });

    expect(await screen.findByText('新建 ASN 草稿 · PO-20260828-09')).toBeInTheDocument();
    expect(screen.getByText(/保存只会生成或更新草稿/)).toBeInTheDocument();
    expect(await screen.findByRole('spinbutton', { name: '发货数量' })).toHaveValue('7');
    fireEvent.click(screen.getByRole('button', { name: /保\s*存\s*草\s*稿/ }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith(expect.objectContaining({
      warehouseId: 4,
      items: [expect.objectContaining({ purchaseOrderItemId: 21, shippedQuantity: 7 })],
    })));
    expect(screen.queryByRole('button', { name: /确\s*认\s*发\s*货/ })).not.toBeInTheDocument();
  });
});
