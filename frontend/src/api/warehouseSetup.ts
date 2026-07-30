import type { components } from './schema';
import { apiRequest } from './http';

export type Warehouse = components['schemas']['WarehouseResponse'];
export type Location = components['schemas']['LocationResponse'];
export type LocationZone = NonNullable<Location['zone']>;

export type CreateWarehousePayload = components['schemas']['CreateWarehouseRequest'];
export type UpdateWarehousePayload = components['schemas']['UpdateWarehouseRequest'] & {
  phone?: string;
};
export type CreateLocationPayload = components['schemas']['CreateLocationRequest'] & {
  posX?: number;
  posY?: number;
};
export type UpdateLocationPayload = components['schemas']['UpdateLocationRequest'] & {
  posX?: number;
  posY?: number;
};

export const WAREHOUSES_QUERY_KEY = ['warehouse-setup', 'warehouses'] as const;
export const LOCATIONS_QUERY_KEY = ['warehouse-setup', 'locations'] as const;

export function listWarehouses(): Promise<Warehouse[]> {
  return apiRequest<Warehouse[]>('/api/warehouses');
}

export function createWarehouse(payload: CreateWarehousePayload): Promise<Warehouse> {
  return apiRequest<Warehouse>('/api/warehouses', { method: 'POST', body: payload });
}

export function updateWarehouse(id: number, payload: UpdateWarehousePayload): Promise<Warehouse> {
  return apiRequest<Warehouse>(`/api/warehouses/${id}`, { method: 'PUT', body: payload });
}

export function setWarehouseActive(id: number, active: boolean): Promise<Warehouse> {
  return apiRequest<Warehouse>(`/api/warehouses/${id}/${active ? 'activate' : 'deactivate'}`, {
    method: 'PUT',
  });
}

export function listLocations(warehouseId: number): Promise<Location[]> {
  return apiRequest<Location[]>(`/api/locations/warehouse/${warehouseId}`);
}

export function listEmptyLocations(warehouseId: number): Promise<Location[]> {
  return apiRequest<Location[]>(`/api/locations/warehouse/${warehouseId}/empty`);
}

export function createLocation(payload: CreateLocationPayload): Promise<Location> {
  return apiRequest<Location>('/api/locations', { method: 'POST', body: payload });
}

export function updateLocation(id: number, payload: UpdateLocationPayload): Promise<Location> {
  return apiRequest<Location>(`/api/locations/${id}`, { method: 'PUT', body: payload });
}

export function setLocationEnabled(id: number, enabled: boolean): Promise<Location> {
  return apiRequest<Location>(`/api/locations/${id}/${enabled ? 'enable' : 'disable'}`, {
    method: 'PUT',
  });
}
