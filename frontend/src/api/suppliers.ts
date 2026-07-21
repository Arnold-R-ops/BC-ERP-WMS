import type { components } from './schema';
import { apiRequest } from './http';

export type Supplier = components['schemas']['SupplierResponse'];
export type CreateSupplierPayload = components['schemas']['CreateSupplierRequest'];
export type UpdateSupplierPayload = components['schemas']['UpdateSupplierRequest'];

export const SUPPLIERS_QUERY_KEY = ['suppliers'] as const;

export function listSuppliers(activeOnly = false): Promise<Supplier[]> {
  return apiRequest<Supplier[]>(`/api/suppliers?activeOnly=${String(activeOnly)}`);
}

export function createSupplier(payload: CreateSupplierPayload): Promise<Supplier> {
  return apiRequest<Supplier>('/api/suppliers', { method: 'POST', body: payload });
}

export function updateSupplier(id: number, payload: UpdateSupplierPayload): Promise<Supplier> {
  return apiRequest<Supplier>(`/api/suppliers/${id}`, { method: 'PUT', body: payload });
}

export function setSupplierActive(id: number, active: boolean): Promise<Supplier> {
  return apiRequest<Supplier>(`/api/suppliers/${id}/${active ? 'activate' : 'deactivate'}`, {
    method: 'PUT',
  });
}

export function deleteSupplier(id: number): Promise<void> {
  return apiRequest<void>(`/api/suppliers/${id}`, { method: 'DELETE' });
}
