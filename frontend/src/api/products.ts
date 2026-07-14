import type { components } from './schema';
import { apiRequest } from './http';

export type Product = components['schemas']['ProductResponse'];
export type ProductPayload = components['schemas']['CreateProductRequest'];

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

export async function createProduct(payload: ProductPayload): Promise<Product> {
  return apiRequest<Product>('/api/products', {
    method: 'POST',
    body: payload,
  });
}

export async function updateProduct(id: number, payload: ProductPayload): Promise<Product> {
  return apiRequest<Product>(`/api/products/${id}`, {
    method: 'PUT',
    body: payload,
  });
}
