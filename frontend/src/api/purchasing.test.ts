import { beforeEach, describe, expect, it, vi } from 'vitest';
import { updatePurchaseOrder } from './purchasing';

describe('purchasing API', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ id: 9 }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    }));
  });

  it('sends the optimistic-lock version when editing an ORDERING purchase order', async () => {
    await updatePurchaseOrder(9, {
      version: 4,
      supplierId: 3,
      items: [{ id: 21, productSkuId: 12, orderedQuantity: 7 }],
    });

    expect(fetch).toHaveBeenCalledWith(
      '/api/purchase-orders/9',
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      version: 4,
      supplierId: 3,
      items: [{ id: 21, productSkuId: 12, orderedQuantity: 7 }],
    });
  });
});
