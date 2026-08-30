import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createInventoryScrapOrder,
  executeInventoryDisposition,
  previewInventoryDamage,
  submitInventoryDisposition,
  updateInventoryDispositionPolicy,
} from './inventoryDisposition';

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' }, status: 200,
  });
}

describe('inventory disposition API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      response({ id: 1, lines: [] }));
  });

  it('previews sales impact before marking stock damaged', async () => {
    const payload = {
      lines: [{ batchId: 7, quantity: 10 }],
      reasonCode: 'PHYSICAL_DAMAGE',
      reasonDetail: 'carton crushed',
      confirmSalesImpact: false,
    };

    await previewInventoryDamage(payload);

    expect(fetch).toHaveBeenCalledWith(
      '/api/inventory-dispositions/damage-preview',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body)))
      .toEqual(payload);
  });

  it('can save a real draft and submit it with separate idempotency keys', async () => {
    await createInventoryScrapOrder({
      lines: [{ batchId: 7, quantity: 4 }],
      reasonCode: 'DAMAGED_SCRAP',
      saveAsDraft: true,
    }, 'draft-7');

    let options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect((options?.headers as Headers).get('Idempotency-Key')).toBe('draft-7');
    expect(JSON.parse(String(options?.body)).saveAsDraft).toBe(true);

    await submitInventoryDisposition(1, 'submit-1');
    options = vi.mocked(fetch).mock.calls[1]?.[1];
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/inventory-dispositions/orders/1/submit',
      expect.objectContaining({ method: 'POST' }),
    );
    expect((options?.headers as Headers).get('Idempotency-Key')).toBe('submit-1');
  });

  it('submits one atomic partial execution with server-derived operator identity', async () => {
    await executeInventoryDisposition(
      9, [{ lineId: 11, quantity: 3 }], 'destroyed at dock 2', 'execute-9');

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/inventory-dispositions/orders/9/executions',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual({
      lines: [{ lineId: 11, quantity: 3 }],
      executionNote: 'destroyed at dock 2',
    });
    expect(String(options?.body)).not.toContain('operator');
  });

  it('persists the optional approval mode and IANA business timezone', async () => {
    await updateInventoryDispositionPolicy(
      'APPROVAL_REQUIRED', true, 'Europe/London', 'enable governed disposal');

    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      reviewMode: 'APPROVAL_REQUIRED',
      automaticExpiryEnabled: true,
      businessTimezone: 'Europe/London',
      reason: 'enable governed disposal',
    });
  });
});
