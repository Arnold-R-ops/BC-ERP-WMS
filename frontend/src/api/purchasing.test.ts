import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  cancelPurchaseAsn,
  closePurchaseAsnRemainder,
  confirmPurchaseAsn,
  correctPurchaseAsn,
  createPurchaseAsnDraft,
  listPurchaseReferenceSkus,
  listPurchaseReferenceWarehouses,
  savePurchaseOrderAllocations,
  submitPurchaseOrder,
  updatePurchaseAsnDraft,
  updatePurchaseGovernancePolicy,
  updatePurchaseOrder,
} from './purchasing';

describe('purchasing API', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ id: 9 }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    }));
  });

  it('sends the optimistic-lock version when editing a draft purchase order', async () => {
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

  it('saves complete per-line warehouse allocations before workflow submission', async () => {
    await savePurchaseOrderAllocations(9, 5, [{ purchaseOrderItemId: 21, warehouseId: 4, plannedQuantity: 7 }]);
    await submitPurchaseOrder(9, { version: 5 });

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/purchase-orders/9/allocations', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      purchaseOrderVersion: 5,
      allocations: [{ purchaseOrderItemId: 21, warehouseId: 4, plannedQuantity: 7 }],
    });
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/purchase-orders/9/submit', expect.objectContaining({ method: 'POST' }));
  });

  it('routes remainder closure according to the tenant governance mode', async () => {
    await closePurchaseAsnRemainder(31, 'supplier short shipped', 'DIRECT_WITH_REASON');
    await closePurchaseAsnRemainder(32, 'buyer requests closure', 'APPROVAL_REQUIRED');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/purchase-asns/31/close', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/purchase-asns/32/remainder-closure-requests', expect.objectContaining({ method: 'POST' }));
  });

  it('supports the complete ASN draft and correction lifecycle', async () => {
    const draft = {
      warehouseId: 4,
      trackingNo: 'TRACK-9',
      items: [{ purchaseOrderItemId: 21, shippedQuantity: 7 }],
    };
    await createPurchaseAsnDraft(9, draft);
    await updatePurchaseAsnDraft(31, { ...draft, version: 3, trackingNo: 'TRACK-10' });
    await confirmPurchaseAsn(31);
    await correctPurchaseAsn(31, 'wrong shipment quantity');
    await cancelPurchaseAsn(32, 'duplicate draft');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/purchase-orders/9/asns', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/purchase-asns/31', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      ...draft, version: 3, trackingNo: 'TRACK-10',
    });
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/purchase-asns/31/confirm', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(4, '/api/purchase-asns/31/correct', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[3]?.[1]?.body))).toEqual({ reason: 'wrong shipment quantity' });
    expect(fetch).toHaveBeenNthCalledWith(5, '/api/purchase-asns/32/cancel', expect.objectContaining({ method: 'POST' }));
  });

  it('uses purchase-scoped SKU and warehouse options without master-data endpoints', async () => {
    await listPurchaseReferenceSkus();
    await listPurchaseReferenceWarehouses();

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/purchase-orders/reference/skus', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/purchase-orders/reference/warehouses', expect.any(Object));
  });

  it('updates independent purchase and short-receipt governance modes', async () => {
    await updatePurchaseGovernancePolicy({
      purchaseApprovalMode: 'SIMPLE',
      shortReceiptClosureMode: 'APPROVAL_REQUIRED',
      reason: 'Enable enterprise approvals',
      confirmed: true,
    });

    expect(fetch).toHaveBeenCalledWith('/api/purchase-governance-policy', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      purchaseApprovalMode: 'SIMPLE',
      shortReceiptClosureMode: 'APPROVAL_REQUIRED',
      reason: 'Enable enterprise approvals',
      confirmed: true,
    });
  });
});
