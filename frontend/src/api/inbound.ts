import type { components } from './schema';
import { apiRequest } from './http';

export type InboundOrder = components['schemas']['InboundOrderResponse'];
export type InboundOrderItem = components['schemas']['InboundOrderItemResponse'];
export type InboundOrderPayload = components['schemas']['CreateInboundOrderRequest'];
export type InboundReceiptPayload = components['schemas']['ReceiveGoodsRequest'];
export type InboundOrderStatus = NonNullable<InboundOrder['status']>;

export interface InboundConfirmationItem {
  itemId: number;
  confirmedQty: number;
  expiryDate: string;
  productionDate?: string;
  externalBatchCode?: string;
  targetWarehouseId: number;
  targetLocationId: number;
}

export interface InboundConfirmationPayload {
  comment?: string;
  confirmations: InboundConfirmationItem[];
}

export const INBOUND_ORDERS_QUERY_KEY = ['inbound-orders'] as const;

export async function listInboundOrders(status: InboundOrderStatus): Promise<InboundOrder[]> {
  return apiRequest<InboundOrder[]>(`/api/inbound-orders/by-status/${status}`);
}

export async function getInboundOrder(id: number): Promise<InboundOrder> {
  return apiRequest<InboundOrder>(`/api/inbound-orders/${id}`);
}

export async function createInboundOrder(payload: InboundOrderPayload): Promise<InboundOrder> {
  return apiRequest<InboundOrder>('/api/inbound-orders', { method: 'POST', body: payload });
}

export async function approveInboundOrder(id: number, comment?: string): Promise<InboundOrder> {
  return apiRequest<InboundOrder>(`/api/inbound-orders/${id}/approve-plan`, {
    method: 'POST',
    body: { comment },
  });
}

export async function rejectInboundOrder(id: number, reason: string): Promise<InboundOrder> {
  return apiRequest<InboundOrder>(`/api/inbound-orders/${id}/reject`, {
    method: 'POST',
    body: { reason },
  });
}

export async function confirmInboundOrder(
  id: number,
  payload: InboundConfirmationPayload,
): Promise<InboundOrder> {
  return apiRequest<InboundOrder>(`/api/inbound-orders/${id}/confirm-order`, {
    method: 'POST',
    body: payload,
  });
}

export async function receiveInboundOrder(
  id: number,
  payload: InboundReceiptPayload,
): Promise<InboundOrder> {
  return apiRequest<InboundOrder>(`/api/inbound-orders/${id}/receive-goods`, {
    method: 'POST',
    body: payload,
  });
}
