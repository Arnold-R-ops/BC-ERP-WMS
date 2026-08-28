import type { components } from './schema';
import { apiBlobRequest, apiRawRequest, apiRequest } from './http';

type GeneratedPurchaseOrder = components['schemas']['PurchaseOrderResponse'];
type GeneratedPurchaseOrderItem = components['schemas']['PurchaseOrderItemResponse'];
type GeneratedPurchaseOrderPayload = components['schemas']['CreatePurchaseOrderRequest'];

export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'REJECTED'
  | 'AWAITING_SHIPMENT'
  | 'PARTIALLY_SHIPPED'
  | 'IN_TRANSIT'
  | 'PARTIALLY_RECEIVED'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'VOIDED';

export type PurchaseOrderItem = GeneratedPurchaseOrderItem;
export type PurchaseOrder = Omit<GeneratedPurchaseOrder, 'status'> & { status?: PurchaseOrderStatus };
export type PurchaseOrderPayload = GeneratedPurchaseOrderPayload;
export type PurchaseOrderUpdatePayload = components['schemas']['UpdatePurchaseOrderRequest'];

export interface PurchaseOrderWorkflowActionPayload { version: number; reason?: string }
export interface PurchaseOrderWorkflowResponse {
  id: number;
  version: number;
  poNumber: string;
  status: PurchaseOrderStatus;
}

export interface PurchaseOrderAllocationInput {
  purchaseOrderItemId: number;
  warehouseId: number;
  plannedQuantity: number;
}

export interface PurchaseOrderAllocation extends PurchaseOrderAllocationInput {
  id?: number;
  productSkuId?: number;
  skuCode?: string;
  warehouseCode?: string;
  shippedQuantity?: number;
  receivedQuantity?: number;
  closedQuantity?: number;
  availableToShipQuantity?: number;
  status?: string;
}

export interface PurchaseReferenceSku {
  id: number;
  skuCode: string;
  name: string;
  barcode?: string;
  expiryManagementEnabled: boolean;
}

export interface PurchaseReferenceWarehouse {
  id: number;
  code: string;
  name: string;
}

export interface PurchaseAsnItemInput {
  purchaseOrderItemId: number;
  shippedQuantity: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
  remark?: string;
}

export interface CreatePurchaseAsnRequest {
  warehouseId: number;
  supplierShipmentNo?: string;
  carrier?: string;
  trackingNo?: string;
  expectedArrivalDate?: string;
  remark?: string;
  items: PurchaseAsnItemInput[];
}

export interface UpdatePurchaseAsnDraftRequest extends CreatePurchaseAsnRequest {
  version: number;
}

export interface PurchaseAsnItem extends PurchaseAsnItemInput {
  id?: number;
  productSkuId?: number;
  skuCode?: string;
  productName?: string;
  receivedQuantity?: number;
  closedQuantity?: number;
  remainingQuantity?: number;
}

export interface PurchaseAsn {
  id: number;
  version: number;
  asnNumber: string;
  purchaseOrderId: number;
  purchaseOrderNumber?: string;
  warehouseId: number;
  warehouseCode?: string;
  status: 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED' | 'CORRECTED';
  correctionOfId?: number;
  supplierShipmentNo?: string;
  carrier?: string;
  trackingNo?: string;
  expectedArrivalDate?: string;
  remark?: string;
  confirmedAt?: string;
  closedAt?: string;
  closeReason?: string;
  cancelledAt?: string;
  cancelReason?: string;
  items: PurchaseAsnItem[];
}

export type PurchaseApprovalMode = 'NONE' | 'SIMPLE';
export type ShortReceiptClosureMode = 'DIRECT_WITH_REASON' | 'APPROVAL_REQUIRED';
export interface PurchaseGovernancePolicy {
  configuredPurchaseApprovalMode: PurchaseApprovalMode;
  effectivePurchaseApprovalMode: PurchaseApprovalMode;
  configuredShortReceiptClosureMode: ShortReceiptClosureMode;
  effectiveShortReceiptClosureMode: ShortReceiptClosureMode;
  approvalEntitled: boolean;
  availablePurchaseApprovalModes: PurchaseApprovalMode[];
  availableShortReceiptClosureModes: ShortReceiptClosureMode[];
}
export interface PurchaseGovernancePolicyUpdatePayload {
  purchaseApprovalMode: PurchaseApprovalMode;
  shortReceiptClosureMode: ShortReceiptClosureMode;
  reason: string;
  confirmed: true;
}
export type PurchaseRemainderClosureStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export interface PurchaseRemainderClosureRequest {
  id: number;
  version: number;
  purchaseAsnId: number;
  asnNumber?: string;
  purchaseOrderId: number;
  purchaseOrderNumber?: string;
  warehouseId: number;
  status: PurchaseRemainderClosureStatus;
  reason: string;
  requestedBy?: number;
  requestedByName?: string;
  requestedAt?: string;
  reviewedBy?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  reviewComment?: string;
}

export interface PurchaseImportParams { file: File; supplierId: number; expectedDate?: string }

export const PURCHASE_ORDERS_QUERY_KEY = ['purchase-orders'] as const;

export async function listPurchaseOrders(status?: PurchaseOrderStatus): Promise<PurchaseOrder[]> {
  const search = new URLSearchParams({ page: '0', size: '500' });
  if (status) search.set('status', status);
  return apiRequest<PurchaseOrder[]>(`/api/purchase-orders?${search.toString()}`);
}

export async function getPurchaseOrder(id: number): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}`);
}

export async function listPurchaseReferenceSkus(): Promise<PurchaseReferenceSku[]> {
  return apiRequest<PurchaseReferenceSku[]>('/api/purchase-orders/reference/skus');
}

export async function listPurchaseReferenceWarehouses(): Promise<PurchaseReferenceWarehouse[]> {
  return apiRequest<PurchaseReferenceWarehouse[]>('/api/purchase-orders/reference/warehouses');
}

export async function createPurchaseOrder(payload: PurchaseOrderPayload): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>('/api/purchase-orders', { method: 'POST', body: payload });
}

export async function updatePurchaseOrder(id: number, payload: PurchaseOrderUpdatePayload): Promise<PurchaseOrder> {
  return apiRequest<PurchaseOrder>(`/api/purchase-orders/${id}`, { method: 'PUT', body: payload });
}

export async function getPurchaseOrderAllocations(id: number): Promise<PurchaseOrderAllocation[]> {
  return apiRequest<PurchaseOrderAllocation[]>(`/api/purchase-orders/${id}/allocations`);
}

export async function savePurchaseOrderAllocations(
  id: number,
  purchaseOrderVersion: number,
  allocations: PurchaseOrderAllocationInput[],
): Promise<PurchaseOrderAllocation[]> {
  return apiRequest<PurchaseOrderAllocation[]>(`/api/purchase-orders/${id}/allocations`, {
    method: 'PUT', body: { purchaseOrderVersion, allocations },
  });
}

async function runWorkflowAction(
  id: number,
  action: 'submit' | 'approve' | 'reject' | 'withdraw' | 'cancel' | 'void',
  payload: PurchaseOrderWorkflowActionPayload,
): Promise<PurchaseOrderWorkflowResponse> {
  return apiRequest<PurchaseOrderWorkflowResponse>(`/api/purchase-orders/${id}/${action}`, {
    method: 'POST', body: payload,
  });
}

export const submitPurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'submit', payload);
export const approvePurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'approve', payload);
export const rejectPurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'reject', payload);
export const withdrawPurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'withdraw', payload);
export const cancelPurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'cancel', payload);
export const voidPurchaseOrder = (id: number, payload: PurchaseOrderWorkflowActionPayload) => runWorkflowAction(id, 'void', payload);

export async function listPurchaseAsns(id: number): Promise<PurchaseAsn[]> {
  return apiRequest<PurchaseAsn[]>(`/api/purchase-orders/${id}/asns`);
}

export async function createPurchaseAsnDraft(id: number, payload: CreatePurchaseAsnRequest): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-orders/${id}/asns`, { method: 'POST', body: payload });
}

export async function updatePurchaseAsnDraft(id: number, payload: UpdatePurchaseAsnDraftRequest): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-asns/${id}`, { method: 'PUT', body: payload });
}

export async function confirmPurchaseAsn(id: number): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-asns/${id}/confirm`, { method: 'POST' });
}

export async function correctPurchaseAsn(id: number, reason: string): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-asns/${id}/correct`, { method: 'POST', body: { reason } });
}

export async function cancelPurchaseAsn(id: number, reason: string): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-asns/${id}/cancel`, { method: 'POST', body: { reason } });
}

export async function quickConfirmPurchaseAsn(id: number, payload: CreatePurchaseAsnRequest): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-orders/${id}/asns/quick-confirm`, {
    method: 'POST', body: payload,
  });
}

export async function getPurchaseGovernancePolicy(): Promise<PurchaseGovernancePolicy> {
  return apiRequest<PurchaseGovernancePolicy>('/api/purchase-governance-policy');
}

export async function updatePurchaseGovernancePolicy(
  payload: PurchaseGovernancePolicyUpdatePayload,
): Promise<PurchaseGovernancePolicy> {
  return apiRequest<PurchaseGovernancePolicy>('/api/purchase-governance-policy', {
    method: 'PUT', body: payload,
  });
}

export async function closePurchaseAsn(id: number, reason: string): Promise<PurchaseAsn> {
  return apiRequest<PurchaseAsn>(`/api/purchase-asns/${id}/close`, { method: 'POST', body: { reason } });
}

export async function requestPurchaseAsnRemainderClosure(id: number, reason: string): Promise<PurchaseRemainderClosureRequest> {
  return apiRequest<PurchaseRemainderClosureRequest>(`/api/purchase-asns/${id}/remainder-closure-requests`, {
    method: 'POST', body: { reason },
  });
}

export async function closePurchaseAsnRemainder(
  id: number,
  reason: string,
  mode: ShortReceiptClosureMode,
): Promise<PurchaseAsn | PurchaseRemainderClosureRequest> {
  return mode === 'APPROVAL_REQUIRED'
    ? requestPurchaseAsnRemainderClosure(id, reason)
    : closePurchaseAsn(id, reason);
}

export async function listPurchaseRemainderClosureRequests(status?: PurchaseRemainderClosureStatus): Promise<PurchaseRemainderClosureRequest[]> {
  const search = new URLSearchParams();
  if (status) search.set('status', status);
  const query = search.size ? `?${search.toString()}` : '';
  return apiRequest<PurchaseRemainderClosureRequest[]>(`/api/purchase-remainder-closure-requests${query}`);
}

export async function decidePurchaseRemainderClosure(
  id: number,
  action: 'approve' | 'reject' | 'cancel',
  comment: string,
): Promise<PurchaseRemainderClosureRequest> {
  return apiRequest<PurchaseRemainderClosureRequest>(`/api/purchase-remainder-closure-requests/${id}/${action}`, {
    method: 'POST', body: { comment },
  });
}

export async function importPurchaseOrder(params: PurchaseImportParams): Promise<PurchaseOrder> {
  const search = new URLSearchParams({ supplierId: String(params.supplierId) });
  if (params.expectedDate) search.set('expectedDate', params.expectedDate);
  const form = new FormData();
  form.append('file', params.file);
  return apiRawRequest<PurchaseOrder>(`/api/purchase-orders/upload?${search.toString()}`, {
    method: 'POST', body: form,
  });
}

export async function downloadPurchaseOrderTemplate(): Promise<Blob> {
  return apiBlobRequest('/api/purchase-orders/template');
}
