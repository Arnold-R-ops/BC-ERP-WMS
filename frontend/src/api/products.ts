import type { components } from './schema';
import { apiRequest } from './http';

export type Product = components['schemas']['ProductResponse'];
export type CreateProductPayload = components['schemas']['CreateProductRequest'];
export type UpdateProductPayload = components['schemas']['UpdateProductRequest'];

export const PRODUCTS_QUERY_KEY = ['products'] as const;

export async function listProducts(enabledOnly?: boolean): Promise<Product[]> {
  const search = new URLSearchParams();
  if (enabledOnly !== undefined) {
    search.set('enabledOnly', String(enabledOnly));
  }

  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<Product[]>(`/api/products${query}`);
}

export async function getProduct(id: number): Promise<Product> {
  return apiRequest<Product>(`/api/products/${id}`);
}

export async function createProduct(payload: CreateProductPayload): Promise<Product> {
  return apiRequest<Product>('/api/products', {
    method: 'POST',
    body: payload,
  });
}

export async function updateProduct(id: number, payload: UpdateProductPayload): Promise<Product> {
  return apiRequest<Product>(`/api/products/${id}`, {
    method: 'PUT',
    body: payload,
  });
}

export async function setProductEnabled(id: number, enabled: boolean): Promise<Product> {
  return apiRequest<Product>(`/api/products/${id}/${enabled ? 'activate' : 'deactivate'}`, {
    method: 'PUT',
  });
}
