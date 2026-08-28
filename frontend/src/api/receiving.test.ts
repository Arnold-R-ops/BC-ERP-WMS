import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listReceivingPlans,
  listReceivingTaskLocations,
  submitReceivingReceipt,
  updateReceivingPlanOperations,
} from './receiving';

describe('receiving task API', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ id: 44 }), {
      headers: { 'Content-Type': 'application/json' }, status: 200,
    }));
  });

  it('uses the caller-owned idempotency key for retry-safe partial receipts', async () => {
    const payload = {
      lines: [{ receivingTaskItemId: 7, locationId: 8, quantity: 3, externalBatchCode: 'LOT-1' }],
    };
    await submitReceivingReceipt(5, payload, 'stable-receipt-key');

    const request = vi.mocked(fetch).mock.calls[0];
    expect(request[0]).toBe('/api/receiving-tasks/5/receipts');
    expect(new Headers(request[1]?.headers).get('Idempotency-Key')).toBe('stable-receipt-key');
    expect(JSON.parse(String(request[1]?.body))).toEqual(payload);
  });

  it('carries the optimistic-lock version when editing receiving operations', async () => {
    await updateReceivingPlanOperations(12, { version: 3, priority: 2, dockArea: 'D-01' });
    const request = vi.mocked(fetch).mock.calls[0];
    expect(request[0]).toBe('/api/receiving-plans/12/operations');
    expect(JSON.parse(String(request[1]?.body))).toEqual({ version: 3, priority: 2, dockArea: 'D-01' });
  });

  it('loads only active statuses for the warehouse-scoped receiving plan work queue', async () => {
    await listReceivingPlans(['WAITING_SHIPMENT', 'IN_TRANSIT', 'PARTIALLY_RECEIVED']);
    const request = vi.mocked(fetch).mock.calls[0];
    expect(request[0]).toBe('/api/receiving-plans?status=WAITING_SHIPMENT&status=IN_TRANSIT&status=PARTIALLY_RECEIVED');
    expect(request[1]?.method).toBeUndefined();
  });

  it('loads receiving locations through the task-scoped warehouse endpoint', async () => {
    await listReceivingTaskLocations(5);
    expect(fetch).toHaveBeenCalledWith('/api/receiving-tasks/5/locations', expect.objectContaining({}));
  });
});
