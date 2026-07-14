import { describe, expect, it } from 'vitest';
import type { SalesOrder } from '../api/sales';
import { getRecentOrders, getUrgentReorderCount } from './dashboardUtils';

describe('dashboard data helpers', () => {
  it('keeps the five newest pending orders', () => {
    const orders = [
      { id: 1, createdAt: '2026-07-10T08:00:00Z' },
      { id: 2, createdAt: '2026-07-12T08:00:00Z' },
      { id: 3, createdAt: '2026-07-11T08:00:00Z' },
    ] as SalesOrder[];

    expect(getRecentOrders(orders, 2).map((order) => order.id)).toEqual([2, 3]);
  });

  it('uses the response total for the urgent reorder badge', () => {
    expect(getUrgentReorderCount({ suggestions: [], totalCount: 7, totalCost: 0 })).toBe(7);
  });
});
