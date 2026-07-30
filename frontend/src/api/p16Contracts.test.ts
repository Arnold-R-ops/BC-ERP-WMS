import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listCustomers } from './masterData';
import { reconcileShopifyOrders, repairShopifyOrders } from './integrations';
import { downloadPurchaseOrderTemplate, importPurchaseOrder } from './purchasing';
import { createSalesOrderShipment, listSalesOrderShipments } from './sales';
import { createSupplier, listSuppliers, setSupplierActive } from './suppliers';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });
}

describe('P1.6 frontend API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse([])));
  });

  it('requests clients and consumers through separate server-side filters', async () => {
    await listCustomers(true, 'CLIENT');
    await listCustomers(false, 'CONSUMER');

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/customers?activeOnly=true&customerType=CLIENT',
      expect.any(Object),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/customers?activeOnly=false&customerType=CONSUMER',
      expect.any(Object),
    );
  });

  it('uses supplier master-data endpoints for purchasing', async () => {
    await listSuppliers(true);
    await createSupplier({ code: 'SUP-1', name: 'Supplier One' });
    await setSupplierActive(9, false);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/suppliers?activeOnly=true', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/suppliers', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/suppliers/9/deactivate', expect.objectContaining({ method: 'PUT' }));
  });

  it('downloads the purchase template and uploads with supplierId', async () => {
    await downloadPurchaseOrderTemplate();
    await importPurchaseOrder({
      file: new File(['xlsx'], 'purchase.xlsx'),
      operatorId: 2,
      operatorName: 'buyer',
      supplierId: 9,
    });

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/purchase-orders/template',
      expect.any(Object),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/purchase-orders/upload?supplierId=9&operatorId=2&operatorName=buyer',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('uses order-scoped shipment endpoints', async () => {
    await listSalesOrderShipments(25);
    await createSalesOrderShipment(25, { trackingNo: 'TRACK-1', carrier: 'DHL' });

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/sales-orders/25/shipments', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/sales-orders/25/shipments', expect.objectContaining({ method: 'POST' }));
  });

  it('keeps reconciliation and repair as explicit local control operations', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse({})));
    await reconcileShopifyOrders({ configId: 3, days: 7 });
    await repairShopifyOrders({ rawEventIds: [101, 102] });

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/integration/shopify/reconcile',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({ configId: 3, days: 7 });
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/integration/shopify/reconcile/repair',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({ rawEventIds: [101, 102] });
  });
});
