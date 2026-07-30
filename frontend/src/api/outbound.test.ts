import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  batchConfirmOutboundTasks,
  confirmOutboundTask,
  listOutboundTasks,
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
    await confirmOutboundTask(17, { actualQty: 50, batchCode: 'BATCH-001' });
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/outbound-tasks/17/confirm',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual({ actualQty: 50, batchCode: 'BATCH-001' });
  });

  it('sends only task ids to the atomic batch confirmation endpoint', async () => {
    await batchConfirmOutboundTasks([17, 18]);
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/outbound-tasks/batch-confirm',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual([17, 18]);
  });
});
