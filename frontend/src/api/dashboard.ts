import type { components } from './schema';
import { apiRequest } from './http';

export type ReorderSuggestion = components['schemas']['ReorderSuggestion'] & {
  warehouseId?: number; baseUnit?: string; outstandingPurchaseQuantity?: number;
  targetStock?: number; thresholdConfigured?: boolean;
};

export interface UrgentReorderResponse {
  suggestions: ReorderSuggestion[];
  totalCount: number;
  totalCost: number;
}

export const URGENT_REORDER_QUERY_KEY = ['urgent-reorder-suggestions'] as const;

export function getUrgentReorderSuggestions(days = 30): Promise<UrgentReorderResponse> {
  const search = new URLSearchParams({ days: String(days) });
  return apiRequest<UrgentReorderResponse>(`/api/predictions/reorder/urgent?${search.toString()}`);
}
