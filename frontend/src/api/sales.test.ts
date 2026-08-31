import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  closeSalesOrder,
  createSalesOrder,
  listSalesOrders,
  submitSalesOrder,
  updateSalesLogisticsStatus,
  updateSalesOrderPolicy,
} from './sales';

describe('sales API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      new Response(JSON.stringify({}), { status: 200 }));
  });

  it('encodes the independent commercial, allocation and warehouse filters', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }));
    await listSalesOrders({
      commercialStatus: 'APPROVED',
      allocationStatus: 'WAITING_INBOUND',
      warehouseStatus: 'PENDING_PICK',
      customerId: 9,
    });
    expect(fetch).toHaveBeenCalledWith(
      '/api/sales-orders?commercialStatus=APPROVED&allocationStatus=WAITING_INBOUND&warehouseStatus=PENDING_PICK&customerId=9',
      expect.any(Object),
    );
  });

  it('sends draft intent and the caller-provided company command key', async () => {
    const payload = {
      customerId: 9,
      saveAsDraft: true,
      items: [{ productSkuId: 7, quantity: 2, unitPrice: 12 }],
    };
    await createSalesOrder(payload, 'sales-create-fixed-key');
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith('/api/sales-orders', expect.objectContaining({ method: 'POST' }));
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('sales-create-fixed-key');
    expect(JSON.parse(String(options?.body))).toEqual(payload);
  });

  it('uses the submit and close command routes with idempotency keys', async () => {
    await submitSalesOrder(25, 'sales-submit-fixed-key');
    let options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenNthCalledWith(1, '/api/sales-orders/25/submit', expect.objectContaining({ method: 'POST' }));
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('sales-submit-fixed-key');

    await closeSalesOrder(25, 'Customer accepted the partial shipment');
    options = vi.mocked(fetch).mock.calls[1]?.[1];
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/sales-orders/25/close', expect.objectContaining({ method: 'POST' }));
    expect(new Headers(options?.headers).get('Idempotency-Key')).toContain('sales-close-25-');
    expect(JSON.parse(String(options?.body))).toEqual({ reason: 'Customer accepted the partial shipment' });
  });

  it('updates the optional tenant policy through the protected command contract', async () => {
    const payload = {
      approvalMode: 'RISK_BASED' as const,
      approvalAmountThreshold: 50000,
      detailedLogisticsEnabled: true,
      reason: 'Enable high-value review and detailed tracking',
    };
    await updateSalesOrderPolicy(payload);
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith('/api/sales-orders/policy', expect.objectContaining({ method: 'PUT' }));
    expect(new Headers(options?.headers).get('Idempotency-Key')).toContain('sales-policy-new-');
    expect(JSON.parse(String(options?.body))).toEqual(payload);
  });

  it('posts an audited manual logistics transition to the shipment route', async () => {
    await updateSalesLogisticsStatus(25, 4, {
      status: 'IN_TRANSIT',
      reason: 'Carrier portal manually verified',
    });
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith(
      '/api/sales-orders/25/shipments/4/logistics-status',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(new Headers(options?.headers).get('Idempotency-Key')).toContain('sales-logistics-status-4-');
  });
});
