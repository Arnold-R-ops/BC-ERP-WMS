import type { components } from './schema';
import { apiBlobRequest, apiRawRequest, apiRequest } from './http';

export type SalesOrder = components['schemas']['SalesOrderResponse'];
export type SalesOrderItem = components['schemas']['SalesOrderItemResponse'];
export type SalesOrderPayload = components['schemas']['CreateSalesOrderRequest'];
export type SalesOrderItemPayload = components['schemas']['SalesOrderItemData'];
export type SalesApprovalPayload = components['schemas']['ApprovalRequest'];
export type SalesOrderStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED_AWAITING_SHIPMENT'
  | 'SHIPPED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'VOIDED';

interface SalesListParams {
  status?: SalesOrderStatus;
  customerId?: number;
}

export const SALES_ORDERS_QUERY_KEY = ['sales-orders'] as const;

export async function listSalesOrders(params: SalesListParams = {}): Promise<SalesOrder[]> {
  const search = new URLSearchParams();
  if (params.status) {
    search.set('status', params.status);
  }
  if (params.customerId !== undefined) {
    search.set('customerId', String(params.customerId));
  }
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<SalesOrder[]>(`/api/sales-orders${query}`);
}

export async function getSalesOrder(id: number): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}`);
}

export async function createSalesOrder(payload: SalesOrderPayload): Promise<SalesOrder> {
  return apiRequest<SalesOrder>('/api/sales-orders', { method: 'POST', body: payload });
}

export async function approveSalesOrder(
  id: number,
  payload: SalesApprovalPayload,
): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/approve`, {
    method: 'POST',
    body: payload,
  });
}

export async function rejectSalesOrder(id: number, reason: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/reject`, {
    method: 'POST',
    body: { reason },
  });
}

export async function cancelSalesOrder(id: number, reason: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/cancel`, {
    method: 'POST',
    body: { reason },
  });
}

export async function voidSalesOrder(id: number, reason: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/void`, {
    method: 'POST',
    body: { reason },
  });
}

export async function importSalesOrderItems(file: File): Promise<SalesOrderItemPayload[]> {
  const form = new FormData();
  form.append('file', file);
  return apiRawRequest<SalesOrderItemPayload[]>('/api/sales-orders/upload', {
    method: 'POST',
    body: form,
  });
}

export async function downloadSalesOrderTemplate(): Promise<Blob> {
  return apiBlobRequest('/api/sales-orders/template');
}
