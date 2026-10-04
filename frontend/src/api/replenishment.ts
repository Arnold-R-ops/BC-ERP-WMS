import { apiRequest } from './http';
import type { ReorderSuggestion } from './dashboard';

export function listReplenishment(warehouseId?: number, includeHealthy = false): Promise<ReorderSuggestion[]> {
  const params = new URLSearchParams({ days: '30', includeHealthy: String(includeHealthy) });
  if (warehouseId !== undefined) params.set('warehouseId', String(warehouseId));
  return apiRequest('/api/inventory/replenishment?' + params);
}
export function listReplenishmentWarehouses(): Promise<{ id: number; name: string }[]> {
  return apiRequest('/api/inventory/replenishment/warehouses');
}
export function saveWarehouseMinimum(productSkuId: number, warehouseId: number, minStock: number): Promise<void> {
  return apiRequest('/api/product-skus/' + productSkuId + '/warehouse-minimum', {
    method: 'PUT', body: { warehouseId, minStock },
  });
}
