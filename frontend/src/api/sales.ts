import type { components } from './schema';
import { apiBlobRequest, apiRawRequest, apiRequest } from './http';

export type SalesOrder = components['schemas']['SalesOrderResponse'];
export type SalesOrderItem = components['schemas']['SalesOrderItemResponse'];
export type SalesOrderPayload = components['schemas']['CreateSalesOrderRequest'];
export type SalesOrderItemPayload = components['schemas']['CreateSalesOrderItemRequest'];
export type SalesApprovalPayload = components['schemas']['ApprovalRequest'];
export type SalesOrderShipment = components['schemas']['SalesOrderShipmentResponse'];
export type SalesLogisticsEvent = components['schemas']['SalesLogisticsEventResponse'];
export type SalesOrderShipmentPayload = components['schemas']['SalesOrderShipmentRequest'];
export type SalesOrderPolicy = components['schemas']['SalesOrderPolicyResponse'];
export type UpdateSalesOrderPolicyPayload = components['schemas']['UpdateSalesOrderPolicyRequest'];
export type UpdateSalesLogisticsStatusPayload = components['schemas']['UpdateSalesLogisticsStatusRequest'];

export type SalesCommercialStatus =
  | 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'COMPLETED' | 'CLOSED'
  | 'REJECTED' | 'CANCELLED' | 'VOIDED';
export type SalesAllocationStatus =
  | 'UNALLOCATED' | 'FULLY_ALLOCATED' | 'PARTIALLY_ALLOCATED' | 'BACKORDERED' | 'WAITING_INBOUND';
export type SalesWarehouseStatus =
  | 'NOT_RELEASED' | 'PENDING_PICK' | 'PICKING' | 'PICKED' | 'PENDING_CHECK'
  | 'PACKING' | 'READY_TO_HANDOVER' | 'PARTIALLY_SHIPPED' | 'SHIPPED' | 'EXCEPTION';
export type SalesLogisticsStatus =
  | 'NOT_CREATED' | 'LABEL_CREATED' | 'HANDED_TO_CARRIER' | 'IN_TRANSIT'
  | 'DELIVERED' | 'EXCEPTION' | 'RETURNED';
export type SalesApprovalMode = 'DIRECT' | 'RISK_BASED' | 'ALWAYS';

interface SalesListParams {
  commercialStatus?: SalesCommercialStatus;
  allocationStatus?: SalesAllocationStatus;
  warehouseStatus?: SalesWarehouseStatus;
  customerId?: number;
}

export const SALES_ORDERS_QUERY_KEY = ['sales-orders'] as const;
export const SALES_SHIPMENTS_QUERY_KEY = ['sales-order-shipments'] as const;
export const SALES_POLICY_QUERY_KEY = ['sales-order-policy'] as const;

export function createSalesIdempotencyKey(operation: string, resource?: number | string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `sales-${operation}-${resource ?? 'new'}-${suffix}`;
}

function commandHeaders(operation: string, resource?: number | string, idempotencyKey?: string): HeadersInit {
  return { 'Idempotency-Key': idempotencyKey ?? createSalesIdempotencyKey(operation, resource) };
}

export async function listSalesOrders(params: SalesListParams = {}): Promise<SalesOrder[]> {
  const search = new URLSearchParams();
  if (params.commercialStatus) search.set('commercialStatus', params.commercialStatus);
  if (params.allocationStatus) search.set('allocationStatus', params.allocationStatus);
  if (params.warehouseStatus) search.set('warehouseStatus', params.warehouseStatus);
  if (params.customerId !== undefined) search.set('customerId', String(params.customerId));
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<SalesOrder[]>(`/api/sales-orders${query}`);
}

export function getSalesOrder(id: number): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}`);
}

export function createSalesOrder(payload: SalesOrderPayload, idempotencyKey?: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>('/api/sales-orders', {
    method: 'POST', headers: commandHeaders('create', undefined, idempotencyKey), body: payload,
  });
}

export function submitSalesOrder(id: number, idempotencyKey?: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/submit`, {
    method: 'POST', headers: commandHeaders('submit', id, idempotencyKey),
  });
}

export function deleteDraftSalesOrder(id: number, idempotencyKey?: string): Promise<void> {
  return apiRequest<void>(`/api/sales-orders/${id}`, {
    method: 'DELETE', headers: commandHeaders('delete-draft', id, idempotencyKey),
  });
}

export function approveSalesOrder(id: number, payload: SalesApprovalPayload, idempotencyKey?: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/approve`, {
    method: 'POST', headers: commandHeaders('approve', id, idempotencyKey), body: payload,
  });
}

function reasonCommand(id: number, action: 'reject' | 'cancel' | 'void' | 'close', reason: string): Promise<SalesOrder> {
  return apiRequest<SalesOrder>(`/api/sales-orders/${id}/${action}`, {
    method: 'POST', headers: commandHeaders(action, id), body: { reason },
  });
}

export const rejectSalesOrder = (id: number, reason: string): Promise<SalesOrder> => reasonCommand(id, 'reject', reason);
export const cancelSalesOrder = (id: number, reason: string): Promise<SalesOrder> => reasonCommand(id, 'cancel', reason);
export const voidSalesOrder = (id: number, reason: string): Promise<SalesOrder> => reasonCommand(id, 'void', reason);
export const closeSalesOrder = (id: number, reason: string): Promise<SalesOrder> => reasonCommand(id, 'close', reason);

export function getSalesOrderPolicy(): Promise<SalesOrderPolicy> {
  return apiRequest<SalesOrderPolicy>('/api/sales-orders/policy');
}

export function updateSalesOrderPolicy(payload: UpdateSalesOrderPolicyPayload): Promise<SalesOrderPolicy> {
  return apiRequest<SalesOrderPolicy>('/api/sales-orders/policy', {
    method: 'PUT', headers: commandHeaders('policy'), body: payload,
  });
}

export async function importSalesOrderItems(file: File): Promise<SalesOrderItemPayload[]> {
  const form = new FormData();
  form.append('file', file);
  return apiRawRequest<SalesOrderItemPayload[]>('/api/sales-orders/upload', { method: 'POST', body: form });
}

export function downloadSalesOrderTemplate(): Promise<Blob> {
  return apiBlobRequest('/api/sales-orders/template');
}

export function listSalesOrderShipments(salesOrderId: number): Promise<SalesOrderShipment[]> {
  return apiRequest<SalesOrderShipment[]>(`/api/sales-orders/${salesOrderId}/shipments`);
}

export function createSalesOrderShipment(salesOrderId: number, payload: SalesOrderShipmentPayload): Promise<SalesOrderShipment> {
  return apiRequest<SalesOrderShipment>(`/api/sales-orders/${salesOrderId}/shipments`, {
    method: 'POST', headers: commandHeaders('shipment-create', salesOrderId), body: payload,
  });
}

export function updateSalesOrderShipment(salesOrderId: number, shipmentId: number, payload: SalesOrderShipmentPayload): Promise<SalesOrderShipment> {
  return apiRequest<SalesOrderShipment>(`/api/sales-orders/${salesOrderId}/shipments/${shipmentId}`, {
    method: 'PUT', headers: commandHeaders('shipment-update', shipmentId), body: payload,
  });
}

export function voidSalesOrderShipment(salesOrderId: number, shipmentId: number): Promise<SalesOrderShipment> {
  return apiRequest<SalesOrderShipment>(`/api/sales-orders/${salesOrderId}/shipments/${shipmentId}/void`, {
    method: 'POST', headers: commandHeaders('shipment-void', shipmentId),
  });
}

export function updateSalesLogisticsStatus(
  salesOrderId: number,
  shipmentId: number,
  payload: UpdateSalesLogisticsStatusPayload,
): Promise<SalesOrderShipment> {
  return apiRequest<SalesOrderShipment>(`/api/sales-orders/${salesOrderId}/shipments/${shipmentId}/logistics-status`, {
    method: 'POST', headers: commandHeaders('logistics-status', shipmentId), body: payload,
  });
}
