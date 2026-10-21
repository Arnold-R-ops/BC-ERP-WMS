import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

const authState = vi.hoisted(() => ({
  currentRole: 'WAREHOUSE_STAFF',
  permissionCodes: ['transfer:dispatch', 'transfer:receive'] as string[],
}));
const operationState = vi.hoisted(() => ({ online: true }));

vi.mock('../../auth/AuthProvider', () => ({ useAuth: () => ({ session: authState }) }));
vi.mock('./WarehouseMobileLayout', () => ({ useMobileOperation: () => operationState }));
vi.mock('../../api/transfers', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/transfers')>(),
  dispatchTransferTask: vi.fn(),
  listTransferTaskLocations: vi.fn(),
  listTransferWarehouseTasks: vi.fn(),
  receiveTransferTask: vi.fn(),
  updateTransferWarehouseTask: vi.fn(),
}));

import {
  dispatchTransferTask,
  listTransferTaskLocations,
  listTransferWarehouseTasks,
  receiveTransferTask,
  type TransferWarehouseTask,
  updateTransferWarehouseTask,
} from '../../api/transfers';
import { MobileTransferPage } from './MobileTransferPage';
vi.mock('../../api/packaging', async importOriginal => ({
  ...await importOriginal<typeof import('../../api/packaging')>(),
  getPackaging: vi.fn(async (batchId: number) => ({
    batchId, batchCode: 'BATCH-71', productSkuId: 101, warehouseId: 1, locationId: 7,
    locationCode: 'A-01-01', quantity: 5, version: 0, reservedQuantity: 5, standardPackSize: 1,
  })),
}));

const outboundTask: TransferWarehouseTask = {
  id: 12,
  version: 4,
  direction: 'OUTBOUND',
  taskNumber: 'TT-OUT-12',
  transferNumber: 'TR-20260901-12',
  transferId: 112,
  status: 'PENDING',
  businessStatus: 'APPROVED',
  outboundStatus: 'PENDING',
  inboundStatus: 'NOT_IN_TRANSIT',
  sourceWarehouseId: 1,
  sourceWarehouseName: '北仓',
  destinationWarehouseId: 2,
  destinationWarehouseName: '南仓',
  priority: 2,
  warehouseRemark: '从一号作业区交接',
  lines: [{
    transferLineId: 21,
    productSkuId: 101,
    skuCode: 'SKU-101',
    productName: '防护手套',
    requestedQuantity: 5,
    allocatedQuantity: 5,
    dispatchedQuantity: 0,
    receivedQuantity: 0,
    inTransitQuantity: 0,
    allocations: [{
      allocationId: 91,
      sourceBatchId: 71,
      sourceBatchCode: 'BATCH-71',
      sourceLocationId: 7,
      sourceLocationCode: 'A-01-01',
      productionDate: '2026-08-01',
      expiryDate: '2027-08-01',
      remainingQuantity: 5,
    }],
  }],
  dispatchLines: [],
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } });
  const tree = () => (
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={client}><MobileTransferPage /></QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>
  );
  const view = render(tree());
  return { ...view, rerenderPage: () => view.rerender(tree()) };
}

async function selectTask(label: string): Promise<void> {
  const combobox = await screen.findByRole('combobox');
  fireEvent.mouseDown(combobox);
  fireEvent.click(await screen.findByText(label));
}

describe('mobile warehouse transfer operations', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    authState.permissionCodes = ['transfer:dispatch', 'transfer:receive'];
    operationState.online = true;
    vi.mocked(listTransferWarehouseTasks).mockResolvedValue([{
      ...outboundTask,
      customerName: '不应展示的客户', supplierName: '不应展示的供应商', unitCost: 999,
      approvalComment: '不应展示的审批意见', carrier: 'SECRET-CARRIER', trackingNo: 'SECRET-TRACKING',
    } as TransferWarehouseTask]);
    vi.mocked(listTransferTaskLocations).mockResolvedValue([{ id: 22, locationCode: 'B-01-01', locationName: '调拨收货位' }]);
    vi.mocked(dispatchTransferTask).mockResolvedValue(outboundTask);
    vi.mocked(receiveTransferTask).mockResolvedValue(outboundTask);
    vi.mocked(updateTransferWarehouseTask).mockResolvedValue(outboundTask);
  });

  afterEach(() => cleanup());

  it('shows warehouse-safe outbound data and hides commercial fields', async () => {
    renderPage();

    expect(await screen.findByText('待调出')).toBeVisible();
    expect(screen.getByText('待调入')).toBeVisible();
    await selectTask('TT-OUT-12 · 北仓 → 南仓');

    expect(await screen.findByText('防护手套')).toBeVisible();
    expect(screen.getAllByText(/北仓 → 南仓/).some((node) => node.textContent === '北仓 → 南仓')).toBe(true);
    expect(screen.queryByText('不应展示的客户')).not.toBeInTheDocument();
    expect(screen.queryByText('不应展示的供应商')).not.toBeInTheDocument();
    expect(screen.queryByText('不应展示的审批意见')).not.toBeInTheDocument();
    expect(screen.queryByText('SECRET-CARRIER')).not.toBeInTheDocument();
    expect(screen.queryByText('SECRET-TRACKING')).not.toBeInTheDocument();
  });

  it('reuses the same idempotency key when an uncertain dispatch is retried unchanged', async () => {
    vi.mocked(dispatchTransferTask).mockRejectedValue(new Error('network result unknown'));
    renderPage();
    await selectTask('TT-OUT-12 · 北仓 → 南仓');
    fireEvent.click(await screen.findByRole('button', { name: '人工核验' }));

    const submit = screen.getByRole('button', { name: '提交本次调出' });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);
    await waitFor(() => expect(dispatchTransferTask).toHaveBeenCalledTimes(1));
    fireEvent.click(submit);
    await waitFor(() => expect(dispatchTransferTask).toHaveBeenCalledTimes(2));

    const firstKey = vi.mocked(dispatchTransferTask).mock.calls[0]?.[2];
    const secondKey = vi.mocked(dispatchTransferTask).mock.calls[1]?.[2];
    expect(firstKey).toBe(secondKey);
    expect(vi.mocked(dispatchTransferTask).mock.calls[0]?.[1]).toEqual({
      version: 4,
      lines: [{ allocationId: 91, quantity: 5 }],
    });
  });

  it('loads only task-scoped destination locations for inbound receipt', async () => {
    authState.permissionCodes = ['transfer:receive'];
    vi.mocked(listTransferWarehouseTasks).mockResolvedValue([{
      ...outboundTask,
      id: 13,
      direction: 'INBOUND',
      taskNumber: 'TT-IN-13',
      status: 'PENDING',
      outboundStatus: 'DISPATCHED',
      inboundStatus: 'IN_TRANSIT',
      lines: [{ ...outboundTask.lines[0], dispatchedQuantity: 5, inTransitQuantity: 5, allocations: [] }],
      dispatchLines: [{
        dispatchLineId: 101,
        dispatchNumber: 'TD-101',
        transferLineId: 21,
        productSkuId: 101,
        skuCode: 'SKU-101',
        productName: '防护手套',
        sourceBatchId: 71,
        sourceBatchCode: 'BATCH-71',
        productionDate: '2026-08-01',
        expiryDate: '2027-08-01',
        dispatchedQuantity: 5,
        receivedQuantity: 0,
        openQuantity: 5,
      }],
    }]);
    renderPage();
    await selectTask('TT-IN-13 · 北仓 → 南仓');

    await waitFor(() => expect(listTransferTaskLocations).toHaveBeenCalledWith(13));
    fireEvent.click(await screen.findByRole('button', { name: /登记本次调入/ }));
    expect(await screen.findByRole('dialog')).toHaveTextContent('目标库位');
    expect(screen.getByText(/少收数量继续保留为在途差异/)).toBeVisible();
  });

  it('disables warehouse inventory writes while offline', async () => {
    operationState.online = false;
    renderPage();
    await selectTask('TT-OUT-12 · 北仓 → 南仓');
    expect(await screen.findByRole('button', { name: '提交本次调出' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /作业安排/ })).toBeDisabled();
  });

  it('blocks a task update when connectivity drops after the modal opens', async () => {
    const view = renderPage();
    await selectTask('TT-OUT-12 · 北仓 → 南仓');
    fireEvent.click(await screen.findByRole('button', { name: /作业安排/ }));

    const dialog = await screen.findByRole('dialog');
    operationState.online = false;
    view.rerenderPage();
    const confirm = within(dialog).getAllByRole('button')
      .find((button) => button.classList.contains('ant-btn-primary'));
    expect(confirm).toBeDefined();
    fireEvent.click(confirm as HTMLButtonElement);

    expect(updateTransferWarehouseTask).not.toHaveBeenCalled();
  });

  it('does not reuse an uncertain task-update key for a different task', async () => {
    const secondTask = {
      ...outboundTask,
      id: 13,
      taskNumber: 'TT-OUT-13',
      transferId: 113,
      transferNumber: 'TR-20260901-13',
    };
    vi.mocked(listTransferWarehouseTasks).mockResolvedValue([outboundTask, secondTask]);
    vi.mocked(updateTransferWarehouseTask).mockRejectedValue(new Error('network result unknown'));
    renderPage();

    await selectTask('TT-OUT-12 · 北仓 → 南仓');
    fireEvent.click(await screen.findByRole('button', { name: /作业安排/ }));
    let dialog = await screen.findByRole('dialog');
    let confirm = within(dialog).getAllByRole('button')
      .find((button) => button.classList.contains('ant-btn-primary'));
    fireEvent.click(confirm as HTMLButtonElement);
    await waitFor(() => expect(updateTransferWarehouseTask).toHaveBeenCalledTimes(1));

    fireEvent.click(dialog.querySelector('.ant-modal-close') as HTMLButtonElement);
    await selectTask('TT-OUT-13 · 北仓 → 南仓');
    fireEvent.click(await screen.findByRole('button', { name: /作业安排/ }));
    dialog = screen.getByRole('dialog');
    confirm = within(dialog).getAllByRole('button')
      .find((button) => button.classList.contains('ant-btn-primary'));
    fireEvent.click(confirm as HTMLButtonElement);
    await waitFor(() => expect(updateTransferWarehouseTask).toHaveBeenCalledTimes(2));

    expect(vi.mocked(updateTransferWarehouseTask).mock.calls[0]?.[2])
      .not.toBe(vi.mocked(updateTransferWarehouseTask).mock.calls[1]?.[2]);
  });
});
