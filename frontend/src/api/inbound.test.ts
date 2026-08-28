import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createOtherInboundApplication,
  getOtherInboundPolicy,
  listOtherInboundReferenceSkus,
  listOtherInboundReferenceWarehouses,
  runOtherInboundAction,
  updateOtherInboundApplication,
  updateOtherInboundPolicy,
} from './inbound';

describe('controlled other inbound API', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ enabled: false }), {
      headers: { 'Content-Type': 'application/json' }, status: 200,
    }));
  });

  it('reads the default-closed policy before using the application endpoint', async () => {
    await getOtherInboundPolicy();
    await createOtherInboundApplication({
      businessType: 'FREE_SAMPLE',
      sourceReference: 'SAMPLE-1',
      businessReason: 'Approved supplier sample',
      warehouseId: 2,
      items: [{ productSkuId: 3, plannedQuantity: 4 }],
    });

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/other-inbound-policy', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/other-inbound-applications', expect.objectContaining({ method: 'POST' }));
  });

  it('carries the optimistic-lock version for edits and state actions', async () => {
    const payload = {
      businessType: 'FREE_SAMPLE' as const,
      sourceReference: 'SAMPLE-2',
      businessReason: 'Updated reason',
      warehouseId: 2,
      items: [{ productSkuId: 3, plannedQuantity: 5 }],
    };
    await updateOtherInboundApplication(7, { ...payload, version: 4 });
    await runOtherInboundAction(7, 'close', 5, 'close the remaining quantity');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/other-inbound-applications/7', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toMatchObject({ version: 4 });
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/other-inbound-applications/7/close', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      version: 5,
      reason: 'close the remaining quantity',
    });
  });

  it('uses other-inbound-scoped SKU and warehouse reference options', async () => {
    await listOtherInboundReferenceSkus();
    await listOtherInboundReferenceWarehouses();

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/other-inbound-applications/reference/skus', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/other-inbound-applications/reference/warehouses', expect.any(Object));
  });

  it('updates the tenant-admin feature switch with an explicit confirmation', async () => {
    await updateOtherInboundPolicy({
      enabled: true,
      allowedBusinessTypes: ['FREE_SAMPLE', 'CUSTOMER_SUPPLIED'],
      reason: 'Enable controlled sample receipts',
      confirmed: true,
    });

    expect(fetch).toHaveBeenCalledWith('/api/other-inbound-policy', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      enabled: true,
      allowedBusinessTypes: ['FREE_SAMPLE', 'CUSTOMER_SUPPLIED'],
      reason: 'Enable controlled sample receipts',
      confirmed: true,
    });
  });
});
