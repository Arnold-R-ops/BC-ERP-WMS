import type { components } from './schema';
import { apiRequest } from './http';

export type ProductSku = components['schemas']['ProductSkuResponse'];
export type ProductSkuPayload = components['schemas']['CreateProductSkuRequest'];

export interface ProductSkuListParams {
  enabledOnly?: boolean;
  productId?: number;
}

export const PRODUCT_SKUS_QUERY_KEY = ['product-skus'] as const;

export async function listProductSkus(params: ProductSkuListParams = {}): Promise<ProductSku[]> {
  const search = new URLSearchParams();
  if (params.enabledOnly !== undefined) {
    search.set('enabledOnly', String(params.enabledOnly));
  }
  if (params.productId !== undefined) {
    search.set('productId', String(params.productId));
  }
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<ProductSku[]>(`/api/product-skus${query}`);
}

export async function getProductSku(id: number): Promise<ProductSku> {
  return apiRequest<ProductSku>(`/api/product-skus/${id}`);
}

export async function createProductSku(payload: ProductSkuPayload): Promise<ProductSku> {
  return apiRequest<ProductSku>('/api/product-skus', { method: 'POST', body: payload });
}

export async function updateProductSku(id: number, payload: ProductSkuPayload): Promise<ProductSku> {
  return apiRequest<ProductSku>(`/api/product-skus/${id}`, { method: 'PUT', body: payload });
}

export async function setProductSkuEnabled(id: number, enabled: boolean): Promise<ProductSku> {
  return apiRequest<ProductSku>(`/api/product-skus/${id}/${enabled ? 'activate' : 'deactivate'}`, {
    method: 'PUT',
  });
}
