import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createTransfer,
  createTransferCommandFingerprint,
  dispatchTransferTask,
  getTransfer,
  getTransferReferences,
  getTransferWarehouseTask,
  listTransferBatches,
  listTransferTaskLocations,
  listTransfers,
  listTransferWarehouseTasks,
  receiveTransferTask,
  runTransferAction,
  updateTransfer,
  updateTransferPolicy,
  updateTransferWarehouseTask,
  type TransferActionRequest,
} from './transfers';

describe('warehouse transfer API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      new Response(JSON.stringify({ id: 1, lines: [] }), { status: 200 }));
  });

  it('uses transfer-scoped references and AVAILABLE batch candidates', async () => {
    await getTransferReferences();
    await listTransferBatches(2, 19);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfers/references', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfers/batches?warehouseId=2&productSkuId=19', expect.any(Object));
  });

  it('scopes retry fingerprints to the resource and exact versioned request body', () => {
    const payload = { version: 4, priority: 2, warehouseRemark: 'dock 2' };

    expect(createTransferCommandFingerprint(12, payload))
      .not.toBe(createTransferCommandFingerprint(13, payload));
    expect(createTransferCommandFingerprint(12, payload))
      .not.toBe(createTransferCommandFingerprint(12, { ...payload, version: 5 }));
  });

  it('uses the fixed tenant detail and warehouse task read routes', async () => {
    await listTransfers();
    await getTransfer(8);
    await getTransferWarehouseTask(12);
    await listTransferTaskLocations(12);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfers', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfers/8', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/transfer-warehouse/tasks/12', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(4, '/api/transfer-warehouse/tasks/12/locations', expect.any(Object));
  });

  it('creates and updates a draft with explicit caller-owned idempotency keys', async () => {
    const payload = {
      sourceWarehouseId: 1, destinationWarehouseId: 2, reason: 'Rebalance stock',
      lines: [{ productSkuId: 19, quantity: 4, preferredBatchId: 71 }],
    };
    await createTransfer(payload, 'create-key');
    await updateTransfer(8, { ...payload, version: 3 }, 'update-key');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfers', expect.objectContaining({ method: 'POST' }));
    expect(new Headers(vi.mocked(fetch).mock.calls[0]?.[1]?.headers).get('Idempotency-Key')).toBe('create-key');
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfers/8', expect.objectContaining({ method: 'PUT' }));
    expect(new Headers(vi.mocked(fetch).mock.calls[1]?.[1]?.headers).get('Idempotency-Key')).toBe('update-key');
    const body = String(vi.mocked(fetch).mock.calls[0]?.[1]?.body);
    expect(body).not.toContain('operator');
    expect(body).not.toContain('companyId');
  });

  it('sends every tenant workflow action through its fixed action route', async () => {
    const requests: TransferActionRequest[] = [
      { action: 'submit', payload: { version: 3, comment: 'submit evidence' } },
      { action: 'approve', payload: { version: 3, comment: 'approve evidence' } },
      { action: 'reject', payload: { version: 3, comment: 'reject evidence' } },
      { action: 'cancel', payload: { version: 3, reason: 'cancel evidence' } },
      { action: 'reallocate', payload: { version: 3, comment: 'reallocate evidence' } },
      { action: 'close-remainder', payload: { version: 3, reason: 'remainder evidence' } },
      { action: 'close-shortage', payload: { version: 3, reason: 'shortage evidence', confirmed: true } },
      { action: 'void', payload: { version: 3, reason: 'void evidence' } },
    ];
    for (const request of requests) {
      await runTransferAction(8, request.action, request.payload, `key-${request.action}`);
    }

    requests.forEach(({ action, payload }, index) => {
      expect(fetch).toHaveBeenNthCalledWith(index + 1, `/api/transfers/8/${action}`, expect.objectContaining({ method: 'POST' }));
      expect(new Headers(vi.mocked(fetch).mock.calls[index]?.[1]?.headers).get('Idempotency-Key')).toBe(`key-${action}`);
      expect(JSON.parse(String(vi.mocked(fetch).mock.calls[index]?.[1]?.body))).toEqual(payload);
    });
  });

  it('updates policy and warehouse task operations with explicit idempotency keys', async () => {
    await updateTransferPolicy({ approvalMode: 'DIRECT', receivingInspectionEnabled: false, reason: 'initial policy' }, 'policy-key');
    await updateTransferWarehouseTask(12, { version: 4, priority: 2, warehouseRemark: 'dock 2' }, 'task-key');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfers/policy', expect.objectContaining({ method: 'PUT' }));
    expect(new Headers(vi.mocked(fetch).mock.calls[0]?.[1]?.headers).get('Idempotency-Key')).toBe('policy-key');
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfer-warehouse/tasks/12', expect.objectContaining({ method: 'PUT' }));
    expect(new Headers(vi.mocked(fetch).mock.calls[1]?.[1]?.headers).get('Idempotency-Key')).toBe('task-key');
  });

  it('keeps dispatch and receipt atomic and server-derived actor only', async () => {
    await listTransferWarehouseTasks('OUTBOUND');
    await dispatchTransferTask(12, {
      version: 4,
      handoverReference: 'TH-1',
      lines: [{ allocationId: 91, quantity: 3 }],
    }, 'dispatch-key');
    await receiveTransferTask(13, {
      version: 5,
      lines: [{ dispatchLineId: 101, locationId: 22, disposition: 'DAMAGED', quantity: 1 }],
    }, 'receipt-key');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfer-warehouse/tasks?direction=OUTBOUND', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfer-warehouse/tasks/12/dispatches', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/transfer-warehouse/tasks/13/receipts', expect.objectContaining({ method: 'POST' }));
    expect(new Headers(vi.mocked(fetch).mock.calls[1]?.[1]?.headers).get('Idempotency-Key')).toBe('dispatch-key');
    expect(new Headers(vi.mocked(fetch).mock.calls[2]?.[1]?.headers).get('Idempotency-Key')).toBe('receipt-key');
    const bodies = vi.mocked(fetch).mock.calls.slice(1).map((call) => String(call[1]?.body)).join('\n');
    expect(bodies).not.toContain('operator');
    expect(bodies).not.toContain('customer');
    expect(bodies).not.toContain('supplier');
    expect(bodies).not.toContain('unitCost');
  });
});
