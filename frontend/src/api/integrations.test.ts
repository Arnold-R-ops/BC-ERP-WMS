import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listPendingSkuMappings,
  listRawEvents,
  syncShopifyOrders,
  updateIntegrationConfig,
} from './integrations';

describe('integration API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200 }),
    );
  });

  it('encodes pending mapping filters', async () => {
    await listPendingSkuMappings({ channel: 'SHOPIFY', status: 'PENDING' });
    expect(fetch).toHaveBeenCalledWith(
      '/api/integration/sku-mappings/pending?channel=SHOPIFY&status=PENDING',
      expect.any(Object),
    );
  });

  it('converts raw event pagination to backend parameters', async () => {
    await listRawEvents({ channel: 'SHOPIFY', status: 'FAILED', page: 2, size: 50 });
    expect(fetch).toHaveBeenCalledWith(
      '/api/integration/raw-events?channel=SHOPIFY&status=FAILED&page=2&size=50',
      expect.any(Object),
    );
  });

  it('keeps blank secrets in an update request so the backend can preserve stored values', async () => {
    await updateIntegrationConfig(7, {
      storeUrl: 'example.myshopify.com',
      clientSecret: '',
      accessToken: '',
      isActive: true,
    });
    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(options?.method).toBe('PUT');
    expect(JSON.parse(String(options?.body))).toMatchObject({ clientSecret: '', accessToken: '' });
  });

  it('uses an explicit POST for manual synchronization', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ success: 1, skipped: 2, failed: 0 }), { status: 200 }),
    );
    await syncShopifyOrders();
    expect(fetch).toHaveBeenCalledWith(
      '/api/integration/shopify/sync',
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
