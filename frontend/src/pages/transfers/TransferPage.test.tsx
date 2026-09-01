import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({
    session: {
      currentRole: 'CUSTOM_ROLE',
      permissionCodes: ['transfer:create'],
      username: 'transfer-author',
    },
  }),
}));

vi.mock('../../api/transfers', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/transfers')>();
  return {
    ...original,
    createTransferCommandFingerprint: vi.fn(original.createTransferCommandFingerprint),
    getTransfer: vi.fn(),
    getTransferPolicy: vi.fn(),
    getTransferReferences: vi.fn(),
    listTransfers: vi.fn(),
    updateTransfer: vi.fn(),
  };
});

vi.mock('./TransferDetailDrawer', () => ({
  TransferDetailDrawer: ({ onEdit, open, transfer }: {
    onEdit: (item: import('../../api/transfers').Transfer) => void;
    open: boolean;
    transfer?: import('../../api/transfers').Transfer;
  }) => open && transfer
    ? <button onClick={() => onEdit(transfer)} type="button">edit selected transfer</button>
    : null,
}));

const savedDraft = {
  sourceWarehouseId: 1,
  destinationWarehouseId: 2,
  reason: 'Rebalance stock',
  lines: [{ productSkuId: 101, quantity: 5 }],
};

vi.mock('./TransferFormDrawer', () => ({
  TransferFormDrawer: ({ onSubmit, open }: {
    onSubmit: (payload: typeof savedDraft) => Promise<void>;
    open: boolean;
  }) => open
    ? <button onClick={() => void onSubmit(savedDraft)} type="button">save transfer draft</button>
    : null,
}));

vi.mock('./TransferPolicyModal', () => ({ TransferPolicyModal: () => null }));

import {
  createTransferCommandFingerprint,
  getTransfer,
  getTransferPolicy,
  getTransferReferences,
  listTransfers,
  type Transfer,
  updateTransfer,
} from '../../api/transfers';
import { TransferPage } from './TransferPage';

const transfer: Transfer = {
  id: 8,
  version: 4,
  transferNumber: 'TR-8',
  sourceWarehouseId: 1,
  sourceWarehouseCode: 'A',
  sourceWarehouseName: 'North',
  destinationWarehouseId: 2,
  destinationWarehouseCode: 'B',
  destinationWarehouseName: 'South',
  businessStatus: 'DRAFT',
  outboundStatus: 'NOT_RELEASED',
  inboundStatus: 'NOT_IN_TRANSIT',
  approvalModeSnapshot: 'DIRECT',
  receivingInspectionSnapshot: false,
  reason: 'Rebalance stock',
  requestedQuantity: 5,
  allocatedQuantity: 0,
  dispatchedQuantity: 0,
  inTransitQuantity: 0,
  receivedQuantity: 0,
  damagedQuantity: 0,
  quarantinedQuantity: 0,
  closedQuantity: 0,
  requestedBy: 7,
  requestedByName: 'transfer-author',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  lines: [],
  dispatches: [],
  receipts: [],
  events: [],
};

describe('tenant transfer page idempotency', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listTransfers).mockResolvedValue([transfer]);
    vi.mocked(getTransfer).mockResolvedValue(transfer);
    vi.mocked(getTransferReferences).mockResolvedValue({ warehouses: [], skus: [], backorders: [] });
    vi.mocked(getTransferPolicy).mockResolvedValue({ approvalMode: 'DIRECT', receivingInspectionEnabled: false });
    vi.mocked(updateTransfer).mockResolvedValue(transfer);
  });

  afterEach(() => cleanup());

  it('fingerprints a draft update with the final request body including version', async () => {
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } });
    render(
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={client}><TransferPage /></QueryClientProvider>
        </AntdApp>
      </ConfigProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /查看/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'edit selected transfer' }));
    fireEvent.click(await screen.findByRole('button', { name: 'save transfer draft' }));

    const finalRequest = { ...savedDraft, version: 4 };
    await waitFor(() => expect(updateTransfer).toHaveBeenCalledWith(
      8,
      finalRequest,
      expect.any(String),
    ));
    expect(createTransferCommandFingerprint).toHaveBeenCalledWith(8, finalRequest);
  });
});
