import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyEmergencyCorrection,
  approveEmergencyCorrection,
  createEmergencyCorrection,
  listEmergencyCorrections,
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
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));
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

  it('keeps approval and inventory application as separate commands', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse({ id: 9 })));
    await approveEmergencyCorrection(9, { comment: 'manager approved' });
    await applyEmergencyCorrection(9);

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
    expect(vi.mocked(fetch).mock.calls[1]?.[1]?.body).toBeUndefined();
  });
});
