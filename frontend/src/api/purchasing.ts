import type { components } from './schema';
import { apiBlobRequest, apiRawRequest, apiRequest } from './http';

export type PurchaseOrder = components['schemas']['PurchaseOrderResponse'];
export type PurchaseOrderItem = components['schemas']['PurchaseOrderItemResponse'];
export type PurchaseOrderPayload = components['schemas']['CreatePurchaseOrderRequest'];
export type PurchaseOrderUpdatePayload = components['schemas']['UpdatePurchaseOrderRequest'];
export type PurchaseConfirmPayload = components['schemas']['ConfirmOrderRequest'];
export type PurchaseReceiptPayload = components['schemas']['ConfirmReceiptRequest'];
export type PurchaseOrderStatus = NonNullable<PurchaseOrder['status']>;

export const PURCHASE_ORDERS_QUERY_KEY = ['purchase-orders'] as const;

export async function listPurchaseOrders(status?: PurchaseOrderStatus): Promise<PurchaseOrder[]> {
  const search = new URLSearchParams({ page: '0', size: '500' });
  if (status) {
    search.set('status', status);
  }
  return apiRequest<PurchaseOrder[]>(`/api/purchase-orders?${search.toString()}`);
}

export async function getPurchaseOrder(id: number): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}`);
}

export async function createPurchaseOrder(payload: PurchaseOrderPayload): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>('/api/purchase-orders', { method: 'POST', body: payload });
}

export async function updatePurchaseOrder(
  id: number,
  payload: PurchaseOrderUpdatePayload,
): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}`, { method: 'PUT', body: payload });
}

export async function confirmPurchaseOrder(
  id: number,
  payload: PurchaseConfirmPayload,
): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}/confirm`, {
    method: 'PUT',
    body: payload,
  });
}

export async function receivePurchaseOrder(
  id: number,
  payload: PurchaseReceiptPayload,
): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}/receive`, {
    method: 'PUT',
    body: payload,
  });
}

export async function rollbackPurchaseOrder(id: number, reason: string): Promise<PurchaseOrder> {
  const search = new URLSearchParams({ reason });
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}/rollback?${search.toString()}`, {
    method: 'PUT',
  });
}

interface PurchaseImportParams {
  file: File;
  supplierId: number;
  operatorId: number;
  operatorName: string;
  expectedDate?: string;
}

export async function importPurchaseOrder(params: PurchaseImportParams): Promise<PurchaseOrder> {
  const search = new URLSearchParams({
    supplierId: String(params.supplierId),
    operatorId: String(params.operatorId),
    operatorName: params.operatorName,
  });
  if (params.expectedDate) {
    search.set('expectedDate', params.expectedDate);
  }
  const form = new FormData();
  form.append('file', params.file);
  return apiRawRequest<PurchaseOrder>(`/api/purchase-orders/upload?${search.toString()}`, {
    method: 'POST',
    body: form,
  });
}

export async function downloadPurchaseOrderTemplate(): Promise<Blob> {
  return apiBlobRequest('/api/purchase-orders/template');
}
