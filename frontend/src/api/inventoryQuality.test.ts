import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createInventoryQualityHold,
  createInventoryQualityRelease,
  previewInventoryQualityHold,
  updateInventoryQualityPolicy,
  type InventoryQualityHoldPayload,
} from './inventoryQuality';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' }, status: 200,
  });
}

describe('inventory quality API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 1 }));
  });

  const holdPayload: InventoryQualityHoldPayload = {
    holdType: 'RECALL',
    lines: [{ batchId: 7, quantity: 10 }],
    reasonCode: 'RECALL_NOTICE',
    recallNoticeNumber: 'RN-2026-01',
    confirmReservationReallocation: true,
  };

  it('previews reservation impact before the hold command', async () => {
    await previewInventoryQualityHold(holdPayload);

    expect(fetch).toHaveBeenCalledWith(
      '/api/inventory-quality/hold-preview',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body)))
      .toEqual(holdPayload);
  });

  it('sends a stable idempotency key and explicit reallocation confirmation', async () => {
    await createInventoryQualityHold(holdPayload, 'quality-hold-command-7');

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/inventory-quality/holds',
      expect.objectContaining({ method: 'POST' }),
    );
    expect((options?.headers as Headers).get('Idempotency-Key'))
      .toBe('quality-hold-command-7');
    expect(JSON.parse(String(options?.body)).confirmReservationReallocation).toBe(true);
  });

  it('uses a separate idempotent release command', async () => {
    await createInventoryQualityRelease({
      lines: [{ batchId: 9, quantity: 4 }],
      reasonCode: 'RISK_CLEARED',
      reasonDetail: 'passed offline verification',
    }, 'quality-release-command-9');

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/inventory-quality/releases',
      expect.objectContaining({ method: 'POST' }),
    );
    expect((options?.headers as Headers).get('Idempotency-Key'))
      .toBe('quality-release-command-9');
  });

  it('changes the tenant policy only with a reason and confirmation', async () => {
    await updateInventoryQualityPolicy('APPROVAL_REQUIRED', 'enable two-person release');

    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      releaseMode: 'APPROVAL_REQUIRED',
      reason: 'enable two-person release',
      confirmed: true,
    });
  });
});
