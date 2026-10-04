import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import i18n from '../../locales/i18n';

vi.mock('../../api/suppliers', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/suppliers')>(),
  listSuppliers: vi.fn(),
}));
vi.mock('../../api/purchasing', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/purchasing')>(),
  listPurchaseReferenceSkus: vi.fn(),
  listPurchaseReferenceWarehouses: vi.fn(),
}));

import { listPurchaseReferenceSkus, listPurchaseReferenceWarehouses } from '../../api/purchasing';
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
    vi.mocked(listPurchaseReferenceSkus).mockResolvedValue([{
      id: 12,
      name: '茉莉绿茶 500g/袋',
      skuCode: 'SKU0000012',
      barcode: '6900000012',
      expiryManagementEnabled: false,
    }]);
    vi.mocked(listSuppliers).mockResolvedValue([{
      id: 3,
      code: 'SUP-003',
      name: '原料供应商',
      isActive: true,
    }]);
    vi.mocked(listPurchaseReferenceWarehouses).mockResolvedValue([
      { id: 2, code: 'WH-02', name: '主仓' },
      { id: 3, code: 'WH-03', name: '备用仓' },
    ]);
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
    expect(validatePurchaseLines([{ productSkuId: 1, orderedQuantity: 100, allocations: [
      { warehouseId: 2, plannedQuantity: 60 },
      { warehouseId: 3, plannedQuantity: 30 },
    ] }])).toEqual({
      allocationIndex: 0, field: 'plannedQuantity', index: 0, type: 'allocationMismatch',
    });
    expect(validatePurchaseLines([{ productSkuId: 1, orderedQuantity: 100, allocations: [
      { warehouseId: 2, plannedQuantity: 60 },
      { warehouseId: 3, plannedQuantity: 40 },
    ] }])).toBeNull();
  });

  it('loads active master data and supports multiple purchase lines', async () => {
    renderDrawer();

    await waitFor(() => {
      expect(listPurchaseReferenceSkus).toHaveBeenCalled();
      expect(listSuppliers).toHaveBeenCalledWith(true);
      expect(listPurchaseReferenceWarehouses).toHaveBeenCalled();
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

  it('fully prefills and submits a 60/40 split across two warehouses in edit mode', async () => {
    const onSubmit = vi.fn(async () => true);
    renderDrawer({
      initialValues: {
        supplierId: 3,
        expectedDate: '2026-09-01',
        remark: 'existing remark',
        items: [{
          id: 99,
          productSkuId: 12,
          orderedQuantity: 100,
          allocations: [
            { warehouseId: 2, plannedQuantity: 60 },
            { warehouseId: 3, plannedQuantity: 40 },
          ],
          unitCost: 8.5,
        }],
      },
      mode: 'edit',
      onSubmit,
    });

    expect(await screen.findByText('编辑采购单')).toBeVisible();
    expect(screen.getByRole('spinbutton', { name: '订购数量' })).toHaveValue('100');
    expect(screen.getAllByRole('combobox', { name: '目标仓库' })).toHaveLength(2);
    expect(screen.getAllByRole('spinbutton', { name: '计划数量' }).map((input) => input.getAttribute('value'))).toEqual(['60', '40']);
    expect(screen.getByText('已分配 100 / 订购 100')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 3,
      items: [expect.objectContaining({
        id: 99,
        productSkuId: 12,
        orderedQuantity: 100,
        allocations: [
          { warehouseId: 2, plannedQuantity: 60 },
          { warehouseId: 3, plannedQuantity: 40 },
        ],
      })],
    })));
  });

  it('saves a draft while split allocation quantities are incomplete', async () => {
    const onSubmit = vi.fn(async () => true);
    renderDrawer({
      initialValues: {
        supplierId: 3,
        items: [{
          productSkuId: 12,
          orderedQuantity: 100,
          allocations: [
            { warehouseId: 2, plannedQuantity: 60 },
            { warehouseId: 3, plannedQuantity: 30 },
          ],
        }],
      },
      onSubmit,
    });

    await screen.findByText('已分配 90 / 订购 100');
    fireEvent.click(screen.getByRole('button', { name: /新\s*建/ }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      items: [expect.objectContaining({ orderedQuantity: 100, allocations: [
        { warehouseId: 2, plannedQuantity: 60 }, { warehouseId: 3, plannedQuantity: 30 },
      ] })],
    })));
  });
});
