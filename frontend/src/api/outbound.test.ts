import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  batchDispatchOutboundTasks,
  dispatchOutboundTask,
  listOutboundTasks,
  resolveOutboundCommandAttempt,
} from './outbound';

describe('outbound API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200 }),
    );
  });

  it('encodes route filters for the picking queue', async () => {
    await listOutboundTasks({ salesOrderId: 25, status: 'PENDING' });
    expect(fetch).toHaveBeenCalledWith(
      '/api/outbound-tasks?salesOrderId=25&status=PENDING',
      expect.any(Object),
    );
  });

  it('sends the scanned identity when confirming one task', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 17, status: 'COMPLETED' }), { status: 200 }),
    );
    await dispatchOutboundTask(17, { actualQty: 50, batchCode: 'BATCH-001' }, 'outbound-17-fixed');
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/outbound-tasks/17/dispatch',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual({ actualQty: 50, batchCode: 'BATCH-001' });
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('outbound-17-fixed');
  });

  it('sends only task ids to the atomic batch confirmation endpoint', async () => {
    await batchDispatchOutboundTasks([17, 18], 'outbound-batch-fixed');
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/outbound-tasks/batch-dispatch',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual([17, 18]);
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('outbound-batch-fixed');
  });

  it('reuses an outbound key for the same failed intent and rotates when the payload changes', () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222');
    const first = resolveOutboundCommandAttempt(undefined, 'dispatch', 17, { actualQty: 5 });
    const retry = resolveOutboundCommandAttempt(first, 'dispatch', 17, { actualQty: 5 });
    const changed = resolveOutboundCommandAttempt(retry, 'dispatch', 17, { actualQty: 4 });

    expect(retry).toBe(first);
    expect(changed.key).not.toBe(first.key);
  });
});
