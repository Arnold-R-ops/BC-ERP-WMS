import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getCustomerFact, getSalesOverview, listCustomerFacts } from './reports';

describe('report API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{}'),
    }));
  });

  it('builds the sales overview date range', async () => {
    await getSalesOverview('2026-07-01', '2026-07-23');
    expect(fetch).toHaveBeenCalledWith(
      '/api/reports/sales/overview?startDate=2026-07-01&endDate=2026-07-23',
      expect.any(Object),
    );
  });

  it('builds server-side customer filters and pagination', async () => {
    await listCustomerFacts({ keyword: 'tea', customerType: 'CLIENT', page: 2, size: 20 });
    expect(fetch).toHaveBeenCalledWith(
      '/api/reports/customers?keyword=tea&customerType=CLIENT&page=2&size=20',
      expect.any(Object),
    );
  });

  it('requests a bounded top-product list for one customer', async () => {
    await getCustomerFact(7, 8);
    expect(fetch).toHaveBeenCalledWith('/api/reports/customer/7?topProducts=8', expect.any(Object));
  });
});
