import type { components } from './schema';
import { apiRequest } from './http';

export type Customer = components['schemas']['CustomerResponse'];
export type Warehouse = components['schemas']['WarehouseResponse'];
export type Location = components['schemas']['LocationResponse'];
export type UserSummary = components['schemas']['UserWithRolesDTO'];

export async function listCustomers(activeOnly = true): Promise<Customer[]> {
  return apiRequest<Customer[]>(`/api/customers?activeOnly=${String(activeOnly)}`);
}

export async function listActiveWarehouses(): Promise<Warehouse[]> {
  return apiRequest<Warehouse[]>('/api/warehouses/active');
}

export async function listLocationsByWarehouse(warehouseId: number): Promise<Location[]> {
  return apiRequest<Location[]>(`/api/locations/warehouse/${warehouseId}`);
}

export async function listUsers(): Promise<UserSummary[]> {
  return apiRequest<UserSummary[]>('/api/users');
}

export async function resolveCurrentUser(username: string): Promise<UserSummary> {
  const users = await listUsers();
  const currentUser = users.find((user) => user.username === username);
  if (!currentUser?.id) {
    throw new Error(`Current user ID is unavailable for ${username}`);
  }
  return currentUser;
}
