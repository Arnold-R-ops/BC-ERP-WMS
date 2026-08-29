import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createReceivingReinspection, recordArrivalException, reviewReceivingInspection,
  submitReceivingInspection, updateReceivingInspectionPolicy,
} from './receivingInspection';

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' }, status: 200,
  });
}

describe('receiving inspection API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      response({ id: 1, lines: [] }));
  });

  it('submits an atomic decision with the caller idempotency key', async () => {
    await submitReceivingInspection(7, {
      inspectionMethod: 'SAMPLING', sampleQuantity: 5, defectQuantity: 1,
      reasonCode: 'INSPECTION_PARTIAL',
      lines: [{ inspectionTaskLineId: 8, passedQuantity: 9, quarantinedQuantity: 1 }],
    }, 'inspect-7');

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/receiving-inspections/7/results',
      expect.objectContaining({ method: 'POST' }),
    );
    expect((options?.headers as Headers).get('Idempotency-Key')).toBe('inspect-7');
    expect(JSON.parse(String(options?.body)).lines[0]).toEqual({
      inspectionTaskLineId: 8, passedQuantity: 9, quarantinedQuantity: 1,
    });
  });

  it('uses idempotency for release review and correction', async () => {
    await reviewReceivingInspection(9, 'approve', 'verified', 'review-9');
    expect((vi.mocked(fetch).mock.calls[0]?.[1]?.headers as Headers)
      .get('Idempotency-Key')).toBe('review-9');

    await createReceivingReinspection({
      originalResultId: 9, inventoryBatchId: 10, quantity: 2,
      reason: 'wrong result', confirmReservationReallocation: true,
    }, 'correct-9');
    expect((vi.mocked(fetch).mock.calls[1]?.[1]?.headers as Headers)
      .get('Idempotency-Key')).toBe('correct-9');
  });

  it('records a dock rejection separately from inventory receipt', async () => {
    await recordArrivalException(3, {
      productSkuId: 4, exceptionType: 'DAMAGED', disposition: 'REJECTED_AT_DOCK',
      observedQuantity: 2, reasonDetail: 'carton crushed',
    }, 'dock-3');

    expect(fetch).toHaveBeenCalledWith(
      '/api/receiving-tasks/3/arrival-exceptions',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('keeps tenant inspection and review settings independent of packages', async () => {
    await updateReceivingInspectionPolicy(true, 'REVIEW_REQUIRED', 'regulated goods');

    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      defaultInspectionRequired: true,
      reviewMode: 'REVIEW_REQUIRED',
      reason: 'regulated goods',
    });
  });
});
