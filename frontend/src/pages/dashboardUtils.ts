import type { UrgentReorderResponse } from '../api/dashboard';
import type { SalesOrder } from '../api/sales';

function timestamp(value?: string): number {
  const parsed = value ? new Date(value).getTime() : Number.NaN;
  return Number.isNaN(parsed) ? 0 : parsed;
}

export function getRecentOrders(orders: SalesOrder[], limit = 5): SalesOrder[] {
  return [...orders]
    .sort((left, right) => timestamp(right.createdAt) - timestamp(left.createdAt))
    .slice(0, limit);
}

export function getUrgentReorderCount(response?: UrgentReorderResponse): number {
  return response?.totalCount ?? response?.suggestions?.length ?? 0;
}
