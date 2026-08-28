import { apiRequest } from './http';

export type ReceivingTaskStatus = 'PENDING' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CLOSED' | 'CANCELLED';

export interface ReceivingTaskItem {
  id: number;
  productSkuId?: number;
  skuCode?: string;
  barcode?: string;
  productName?: string;
  expectedQuantity: number;
  receivedQuantity: number;
  closedQuantity: number;
  remainingQuantity: number;
  expiryManagementEnabled: boolean;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
  remark?: string;
}

/** Warehouse-safe task projection. It intentionally has no supplier, price or shipment fields. */
export interface ReceivingTask {
  id: number;
  taskNumber: string;
  status: ReceivingTaskStatus;
  purchaseOrderId?: number;
  purchaseOrderNumber?: string;
  warehouseId: number;
  warehouseCode?: string;
  warehouseName?: string;
  priority?: number;
  expectedWindowStart?: string;
  expectedWindowEnd?: string;
  assignedReceiverId?: number;
  assignedReceiverName?: string;
  assignedTeam?: string;
  dockArea?: string;
  workArea?: string;
  warehouseNotes?: string;
  createdAt?: string;
  completedAt?: string;
  closedAt?: string;
  closeReason?: string;
  items: ReceivingTaskItem[];
}

export interface ReceivingReceiptLineInput {
  receivingTaskItemId: number;
  locationId: number;
  quantity: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
}

/** Task-scoped, warehouse-safe location option for receiving execution. */
export interface ReceivingTaskLocation {
  id: number;
  code: string;
  name?: string;
}

export interface SubmitReceivingReceiptRequest {
  remark?: string;
  lines: ReceivingReceiptLineInput[];
}

export interface ReceiptEventLine {
  id?: number;
  receivingTaskItemId?: number;
  productSkuId?: number;
  skuCode?: string;
  locationId?: number;
  locationCode?: string;
  quantity?: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
}

export interface ReceiptEvent {
  id: number;
  eventNumber: string;
  receivingTaskId: number;
  receivingTaskNumber?: string;
  receivedAt?: string;
  receivedByName?: string;
  remark?: string;
  lines: ReceiptEventLine[];
}

export interface ReceivingPlanOperations {
  version: number;
  priority?: number;
  expectedWindowStart?: string;
  expectedWindowEnd?: string;
  assignedReceiverId?: number;
  assignedReceiverName?: string;
  assignedTeam?: string;
  dockArea?: string;
  workArea?: string;
  warehouseNotes?: string;
}

export type ReceivingPlanStatus = 'WAITING_SHIPMENT' | 'IN_TRANSIT' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CLOSED';

/** Warehouse-safe line summary for pre-arrival planning. */
export interface ReceivingPlanItem {
  allocationId?: number;
  purchaseOrderItemId?: number;
  productSkuId?: number;
  skuCode?: string;
  productName?: string;
  plannedQuantity: number;
  confirmedShippedQuantity?: number;
  remainingToShipQuantity?: number;
}

export interface ReceivingPlan extends Omit<ReceivingPlanOperations, 'version'> {
  id: number;
  version: number;
  planNumber: string;
  purchaseOrderId: number;
  purchaseOrderNumber?: string;
  warehouseId: number;
  warehouseCode?: string;
  warehouseName?: string;
  status: ReceivingPlanStatus;
  items?: ReceivingPlanItem[];
}

export const RECEIVING_TASKS_QUERY_KEY = ['receiving-tasks'] as const;
export const RECEIVING_PLANS_QUERY_KEY = ['receiving-plans'] as const;

export function createReceiptIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `receipt-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function listReceivingTasks(status?: ReceivingTaskStatus): Promise<ReceivingTask[]> {
  const search = new URLSearchParams();
  if (status) search.set('status', status);
  const query = search.size ? `?${search.toString()}` : '';
  return apiRequest<ReceivingTask[]>(`/api/receiving-tasks${query}`);
}

export async function getReceivingTask(id: number): Promise<ReceivingTask> {
  return apiRequest<ReceivingTask>(`/api/receiving-tasks/${id}`);
}

export async function listReceivingTaskLocations(id: number): Promise<ReceivingTaskLocation[]> {
  return apiRequest<ReceivingTaskLocation[]>(`/api/receiving-tasks/${id}/locations`);
}

export async function listReceivingReceipts(id: number): Promise<ReceiptEvent[]> {
  return apiRequest<ReceiptEvent[]>(`/api/receiving-tasks/${id}/receipts`);
}

export async function listPurchaseReceivingPlans(purchaseOrderId: number): Promise<ReceivingPlan[]> {
  return apiRequest<ReceivingPlan[]>(`/api/purchase-orders/${purchaseOrderId}/receiving-plans`);
}

export async function listReceivingPlans(status?: ReceivingPlanStatus | ReceivingPlanStatus[]): Promise<ReceivingPlan[]> {
  const search = new URLSearchParams();
  const statuses = Array.isArray(status) ? status : status ? [status] : [];
  statuses.forEach((value) => search.append('status', value));
  const query = search.size ? `?${search.toString()}` : '';
  return apiRequest<ReceivingPlan[]>(`/api/receiving-plans${query}`);
}

export async function updateReceivingPlanOperations(
  id: number,
  payload: ReceivingPlanOperations,
): Promise<ReceivingPlan> {
  return apiRequest<ReceivingPlan>(`/api/receiving-plans/${id}/operations`, { method: 'PUT', body: payload });
}

export async function submitReceivingReceipt(
  id: number,
  payload: SubmitReceivingReceiptRequest,
  idempotencyKey: string,
): Promise<ReceiptEvent> {
  return apiRequest<ReceiptEvent>(`/api/receiving-tasks/${id}/receipts`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: payload,
  });
}
