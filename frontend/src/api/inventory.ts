import type { components } from './schema';
import { apiRequest } from './http';

export type InventorySummary = components['schemas']['InventorySummaryDto'];
export type InventorySummaryPage = components['schemas']['PageInventorySummaryDto'];
export type InventoryDetail = components['schemas']['InventoryDetailDto'];
export type LocationInventory = components['schemas']['LocationViewDto'];

interface InventorySummaryParams {
  page: number;
  size: number;
  search?: string;
}

export async function getInventorySummary(
  params: InventorySummaryParams,
): Promise<InventorySummaryPage> {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  });
  const keyword = params.search?.trim();
  if (keyword) {
    search.set('search', keyword);
  }

  return apiRequest<InventorySummaryPage>(`/api/inventory/summary?${search.toString()}`);
}

export async function getInventoryDetails(productSkuId: number): Promise<InventoryDetail[]> {
  return apiRequest<InventoryDetail[]>(`/api/inventory/details/${productSkuId}`);
}

export async function getLocationInventory(locationCode: string): Promise<LocationInventory> {
  return apiRequest<LocationInventory>(
    `/api/inventory/location/${encodeURIComponent(locationCode.trim())}`,
  );
}
