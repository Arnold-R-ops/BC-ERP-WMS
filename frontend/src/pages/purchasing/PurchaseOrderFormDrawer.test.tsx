import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import i18n from '../../locales/i18n';

vi.mock('../../api/productSkus', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/productSkus')>(),
  listProductSkus: vi.fn(),
}));
vi.mock('../../api/suppliers', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/suppliers')>(),
  listSuppliers: vi.fn(),
}));

import { listProductSkus } from '../../api/productSkus';
import { listSuppliers } from '../../api/suppliers';
import {
  applyPurchaseLineBulkPatch,
  getPurchaseLinePageRange,
  PurchaseOrderFormDrawer,
  summarizePurchaseLines,
  validatePurchaseLines,
} from './PurchaseOrderFormDrawer';

function renderDrawer(props: Partial<ComponentProps<typeof PurchaseOrderFormDrawer>> = {}): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <PurchaseOrderFormDrawer
            loading={false}
            onClose={vi.fn()}
            onSubmit={async () => true}
            open
            {...props}
          />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('purchase order entry', () => {
  afterEach(() => cleanup());

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listProductSkus).mockResolvedValue([{
      id: 12,
      productName: '茉莉绿茶',
      skuName: '500g/袋',
      skuCode: 'SKU0000012',
      enabled: true,
    }]);
    vi.mocked(listSuppliers).mockResolvedValue([{
      id: 3,
      code: 'SUP-003',
      name: '原料供应商',
      isActive: true,
    }]);
  });

  it('calculates quantity and estimated cost without mutating the lines', () => {
    const lines = [
      { orderedQuantity: 2, unitCost: 8.5 },
      { orderedQuantity: 3, unitCost: 10 },
    ];

    expect(summarizePurchaseLines(lines)).toEqual({
      lineCount: 2,
      totalCost: 47,
      totalQuantity: 5,
    });
    expect(lines).toHaveLength(2);
  });

  it('paginates large purchase orders without changing the full line count', () => {
    expect(getPurchaseLinePageRange(45, 3)).toEqual({
      currentPage: 3,
      endIndex: 45,
      startIndex: 40,
      totalPages: 3,
    });
    expect(getPurchaseLinePageRange(45, 9).currentPage).toBe(3);
  });

  it('bulk edits only selected lines without mutating the source data', () => {
    const lines = [
      { productSkuId: 1, orderedQuantity: 2, unitCost: 8 },
      { productSkuId: 2, orderedQuantity: 3, unitCost: 10 },
      { productSkuId: 3, orderedQuantity: 4, unitCost: 12 },
    ];

    const updated = applyPurchaseLineBulkPatch(lines, [0, 2], {
      expiryDate: '2027-08-01',
      unitCost: 9.5,
    });

    expect(updated[0]).toMatchObject({ expiryDate: '2027-08-01', unitCost: 9.5 });
    expect(updated[1]).toEqual(lines[1]);
    expect(updated[2]).toMatchObject({ expiryDate: '2027-08-01', unitCost: 9.5 });
    expect(lines[0]).not.toHaveProperty('expiryDate');
  });

  it('validates required data and duplicate SKUs across all pages', () => {
    expect(validatePurchaseLines([{ orderedQuantity: 1 }])).toEqual({
      field: 'productSkuId',
      index: 0,
      type: 'productRequired',
    });
    expect(validatePurchaseLines([
      { productSkuId: 1, orderedQuantity: 1 },
      { productSkuId: 1, orderedQuantity: 2 },
    ])).toEqual({
      field: 'productSkuId',
      index: 1,
      type: 'duplicateProduct',
    });
    expect(validatePurchaseLines([{ productSkuId: 1, orderedQuantity: 2 }])).toBeNull();
  });

  it('loads active master data and supports multiple purchase lines', async () => {
    renderDrawer();

    await waitFor(() => {
      expect(listProductSkus).toHaveBeenCalledWith({ enabledOnly: true });
      expect(listSuppliers).toHaveBeenCalledWith(true);
    });
    expect(screen.getAllByRole('combobox', { name: '商品' })).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: '添加商品' }));

    await waitFor(() => expect(screen.getAllByRole('combobox', { name: '商品' })).toHaveLength(2));
    expect(screen.getByText('2 行')).toBeVisible();
  });

  it('applies a bulk unit cost to selected lines', async () => {
    renderDrawer();
    await waitFor(() => expect(screen.getAllByRole('combobox', { name: '商品' })).toHaveLength(1));

    fireEvent.click(screen.getByRole('button', { name: '添加商品' }));
    await waitFor(() => expect(screen.getAllByRole('combobox', { name: '商品' })).toHaveLength(2));
    fireEvent.click(screen.getByRole('checkbox', { name: '选择本页' }));
    fireEvent.click(screen.getByRole('button', { name: '批量设置' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '批量采购单价' }), { target: { value: '8.5' } });
    fireEvent.click(screen.getByRole('button', { name: '应用到所选行' }));

    await waitFor(() => expect(screen.getByText('预计金额 ¥17.00')).toBeVisible());
  });

  it('prefills and submits existing item IDs in edit mode', async () => {
    const onSubmit = vi.fn(async () => true);
    renderDrawer({
      initialValues: {
        supplierId: 3,
        expectedDate: '2026-09-01',
        remark: 'existing remark',
        items: [{
          id: 99,
          productSkuId: 12,
          orderedQuantity: 7,
          unitCost: 8.5,
        }],
      },
      mode: 'edit',
      onSubmit,
    });

    expect(await screen.findByText('编辑采购单')).toBeVisible();
    expect(screen.getByRole('spinbutton', { name: '订购数量' })).toHaveValue('7');
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 3,
      items: [expect.objectContaining({ id: 99, productSkuId: 12, orderedQuantity: 7 })],
    })));
  });
});
