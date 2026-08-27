import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyEmergencyCorrection,
  approveEmergencyCorrection,
  createEmergencyCorrection,
  getEmergencyCorrectionImpact,
  getEmergencyCorrectionPolicy,
  listEmergencyCorrectionBatchOptions,
  listEmergencyCorrections,
  updateEmergencyCorrectionPolicy,
} from './emergencyCorrections';
import {
  listStocktakeReviewItems,
  listStocktakeTasks,
  reviewStocktake,
  submitStocktakeCount,
} from './stocktake';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });
}

describe('inventory governance API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse([])));
  });

  it('encodes stocktake queue filters', async () => {
    await listStocktakeTasks({ warehouseId: 15, status: 'COUNTING' });

    expect(fetch).toHaveBeenCalledWith(
      '/api/stocktake/tasks?warehouseId=15&status=COUNTING',
      expect.any(Object),
    );
  });

  it('submits a blind count without a book quantity', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ id: 13, countedQty: 48 }));
    await submitStocktakeCount(8, 13, { countedQty: 48, remark: 'physical count' });

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/stocktake/tasks/8/items/13/count',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual({
      countedQty: 48,
      remark: 'physical count',
    });
    expect(String(options?.body)).not.toContain('snapshotQty');
  });

  it('uses the separate review endpoint before posting the review decision', async () => {
    await listStocktakeReviewItems(8);
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ id: 8, status: 'COMPLETED' }));
    await reviewStocktake(8, { approved: true, comment: 'approved variance' });

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/stocktake/tasks/8/review-items',
      expect.any(Object),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/stocktake/tasks/8/review',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      approved: true,
      comment: 'approved variance',
    });
  });

  it('filters the correction approval queue by status', async () => {
    await listEmergencyCorrections('PENDING_APPROVAL');

    expect(fetch).toHaveBeenCalledWith(
      '/api/emergency-stock-corrections?status=PENDING_APPROVAL',
      expect.any(Object),
    );
  });

  it('creates a correction against an existing inventory batch', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ id: 9, status: 'DRAFT' }));
    await createEmergencyCorrection({
      productSkuId: 12,
      locationId: 13,
      inventoryBatchId: 21,
      countedQty: 45,
      reasonCode: 'PHYSICAL_COUNT_MISMATCH',
      reasonDetail: 'manual recount',
    });

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/emergency-stock-corrections',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual(expect.objectContaining({
      inventoryBatchId: 21,
      countedQty: 45,
    }));
  });

  it('reads correction policy and impact from dedicated endpoints', async () => {
    await getEmergencyCorrectionPolicy();
    await getEmergencyCorrectionImpact(9);

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/emergency-stock-corrections/policy',
      expect.any(Object),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/emergency-stock-corrections/9/impact',
      expect.any(Object),
    );
  });

  it('loads existing-batch choices from the correction-specific endpoint', async () => {
    await listEmergencyCorrectionBatchOptions(42);

    expect(fetch).toHaveBeenCalledWith(
      '/api/emergency-stock-corrections/batch-options?productSkuId=42',
      expect.any(Object),
    );
  });

  it('requires a reason and explicit confirmation when changing correction policy', async () => {
    await updateEmergencyCorrectionPolicy({
      mode: 'TWO_PERSON',
      reason: 'separate warehouse approval duties',
      confirmed: true,
    });

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/emergency-stock-corrections/policy',
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(JSON.parse(String(options?.body))).toEqual({
      mode: 'TWO_PERSON',
      reason: 'separate warehouse approval duties',
      confirmed: true,
    });
  });

  it('keeps approval and idempotent inventory application as separate commands', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse({ id: 9 })));
    await approveEmergencyCorrection(9, { comment: 'manager approved' });
    await applyEmergencyCorrection(9, 'esc-9-command-1', 'preview-token-9');

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/emergency-stock-corrections/9/approve',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/emergency-stock-corrections/9/apply',
      expect.objectContaining({ method: 'POST' }),
    );
    const applyOptions = vi.mocked(fetch).mock.calls[1]?.[1];
    expect(JSON.parse(String(applyOptions?.body))).toEqual({
      confirmed: true,
      previewToken: 'preview-token-9',
    });
    expect((applyOptions?.headers as Headers).get('Idempotency-Key')).toBe('esc-9-command-1');
  });

  it('does not call apply when the current impact has no preview token', async () => {
    await expect(applyEmergencyCorrection(9, 'esc-9-command-2', '   ')).rejects.toThrow(
      'Emergency correction preview token is required',
    );

    expect(fetch).not.toHaveBeenCalled();
  });
});
