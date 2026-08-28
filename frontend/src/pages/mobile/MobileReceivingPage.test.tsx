import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

const authState = vi.hoisted(() => ({
  currentRole: 'WAREHOUSE_STAFF',
  permissionCodes: [] as string[],
}));

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({ session: authState }),
}));

vi.mock('./WarehouseMobileLayout', () => ({
  useMobileOperation: () => ({ online: true }),
}));

vi.mock('../../api/receiving', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/receiving')>(),
  getReceivingTask: vi.fn(),
  listReceivingPlans: vi.fn(),
  listReceivingTaskLocations: vi.fn(),
  listReceivingTasks: vi.fn(),
  submitReceivingReceipt: vi.fn(),
  updateReceivingPlanOperations: vi.fn(),
}));

import {
  getReceivingTask,
  listReceivingPlans,
  listReceivingTaskLocations,
  listReceivingTasks,
  type ReceivingPlan,
  type ReceivingTask,
  submitReceivingReceipt,
  updateReceivingPlanOperations,
} from '../../api/receiving';
import { MobileReceivingPage } from './MobileReceivingPage';

const plan: ReceivingPlan = {
  id: 31,
  version: 7,
  planNumber: 'RP-20260828-01',
  purchaseOrderId: 18,
  purchaseOrderNumber: 'PO-20260828-01',
  warehouseId: 2,
  warehouseCode: 'WH-02',
  warehouseName: '华东成品仓',
  status: 'WAITING_SHIPMENT',
  priority: 2,
  expectedWindowStart: '2026-08-29T09:00',
  expectedWindowEnd: '2026-08-29T12:00',
  assignedReceiverId: 9,
  assignedReceiverName: '仓库收货员',
  assignedTeam: '早班',
  dockArea: '月台 D2',
  workArea: '收货区 A',
  warehouseNotes: '到货前准备两个托盘位',
  items: [{ productSkuId: 101, skuCode: 'SKU-0101', productName: '防护手套', plannedQuantity: 30 }],
};

const task: ReceivingTask = {
  id: 5,
  taskNumber: 'RT-20260828-05',
  status: 'PENDING',
  purchaseOrderId: 18,
  purchaseOrderNumber: 'PO-20260828-01',
  warehouseId: 2,
  warehouseCode: 'WH-02',
  warehouseName: '华东成品仓',
  items: [{
    id: 7,
    productSkuId: 101,
    skuCode: 'SKU-0101',
    barcode: '6900000101',
    productName: '防护手套',
    expectedQuantity: 30,
    receivedQuantity: 0,
    closedQuantity: 0,
    remainingQuantity: 30,
    expiryManagementEnabled: false,
  }],
};

function renderPage(path = '/mobile/receiving'): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <MemoryRouter initialEntries={[path]}>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <MobileReceivingPage />
          </QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('warehouse receiving plans', () => {
  afterEach(() => cleanup());

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    authState.currentRole = 'WAREHOUSE_STAFF';
    authState.permissionCodes = [];
    vi.mocked(listReceivingPlans).mockResolvedValue([{
      ...plan,
      supplier: '不应展示的供应商',
      unitCost: 999,
      carrier: '不应展示的承运商',
      trackingNo: 'TRACK-SECRET',
      asnNumber: 'ASN-SECRET',
    } as ReceivingPlan]);
    vi.mocked(listReceivingTasks).mockResolvedValue([]);
    vi.mocked(getReceivingTask).mockResolvedValue(task);
    vi.mocked(listReceivingTaskLocations).mockResolvedValue([{ id: 8, code: 'LOC-A-01', name: '收货暂存位' }]);
    vi.mocked(submitReceivingReceipt).mockResolvedValue({
      id: 44,
      eventNumber: 'RE-44',
      receivingTaskId: 5,
      lines: [],
    });
    vi.mocked(updateReceivingPlanOperations).mockResolvedValue(plan);
  });

  it('shows a warehouse-safe pre-arrival plan and switches explicitly to receiving tasks', async () => {
    renderPage();

    expect(await screen.findByText('RP-20260828-01')).toBeVisible();
    expect(screen.getByText(/PO-20260828-01/)).toBeVisible();
    expect(screen.getByText('华东成品仓')).toBeVisible();
    expect(screen.getByText('防护手套')).toBeVisible();
    expect(screen.getByText('计划 30 件')).toBeVisible();
    expect(screen.getByText('仓库收货员')).toBeVisible();
    expect(screen.getByText('早班')).toBeVisible();
    expect(screen.getByText('月台 D2 / 收货区 A')).toBeVisible();
    expect(screen.getByText('到货前准备两个托盘位')).toBeVisible();
    expect(screen.queryByText('不应展示的供应商')).not.toBeInTheDocument();
    expect(screen.queryByText('不应展示的承运商')).not.toBeInTheDocument();
    expect(screen.queryByText('TRACK-SECRET')).not.toBeInTheDocument();
    expect(screen.queryByText('ASN-SECRET')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑运营信息' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '收货任务' }));
    await waitFor(() => expect(listReceivingTasks).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('当前没有待处理收货任务')).toBeVisible();
  });

  it('edits operating fields only with permission and sends the plan version', async () => {
    authState.permissionCodes = ['receiving:plan:edit'];
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '编辑运营信息' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '优先级' }), { target: { value: '4' } });
    fireEvent.change(screen.getByRole('textbox', { name: '仓库备注' }), { target: { value: '调整到三号月台' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(updateReceivingPlanOperations).toHaveBeenCalledWith(31, expect.objectContaining({
      version: 7,
      priority: 4,
      warehouseNotes: '调整到三号月台',
    })));
  });

  it('lets warehouse staff receive through task-scoped data without master-data permissions', async () => {
    vi.mocked(listReceivingTasks).mockResolvedValue([task]);
    renderPage('/mobile/receiving?taskId=5');

    expect(await screen.findByText('RT-20260828-05')).toBeVisible();
    await waitFor(() => expect(listReceivingTaskLocations).toHaveBeenCalledWith(5));

    const productScan = screen.getByPlaceholderText('扫描条码、批次码后按回车');
    fireEvent.change(productScan, { target: { value: '6900000101' } });
    fireEvent.keyDown(productScan, { code: 'Enter', key: 'Enter' });

    const locationScan = screen.getByPlaceholderText('扫描库位码后按回车');
    await waitFor(() => expect(locationScan).toBeEnabled());
    fireEvent.change(locationScan, { target: { value: 'LOC-A-01' } });
    fireEvent.keyDown(locationScan, { code: 'Enter', key: 'Enter' });

    const submit = screen.getByRole('button', { name: '提交本次收货' });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);

    await waitFor(() => expect(submitReceivingReceipt).toHaveBeenCalledWith(5, {
      lines: [{ receivingTaskItemId: 7, locationId: 8, quantity: 30 }],
    }, expect.any(String)));
    expect(authState.permissionCodes).toEqual([]);
  });
});
