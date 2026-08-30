import { apiRequest } from './http';
import type { InventoryQualityStatus } from './inventoryQuality';

export type InventoryDispositionType = 'DAMAGE_REPORT' | 'EXPIRY_MARK' | 'SCRAP_DESTROY';
export type InventoryDispositionStatus =
  | 'DRAFT' | 'COMPLETED' | 'PENDING_REVIEW' | 'READY_TO_EXECUTE'
  | 'PARTIALLY_EXECUTED' | 'REJECTED' | 'CANCELLED';
export type InventoryDispositionReviewMode = 'DIRECT_WITH_REASON' | 'APPROVAL_REQUIRED';

export interface InventoryDispositionQuantity { batchId: number; quantity: number }
export interface InventoryDispositionExecutionQuantity { lineId: number; quantity: number }

export interface InventoryDispositionCandidate {
  batchId: number;
  batchCode: string;
  externalBatchCode?: string;
  qualitySegmentNo?: string;
  qualityParentBatchId?: number;
  productSkuId: number;
  productSkuCode: string;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  locationId: number;
  locationCode: string;
  quantity: number;
  reservedQuantity: number;
  qualityStatus: InventoryQualityStatus;
  productionDate?: string;
  expiryDate?: string;
  dateExpired: boolean;
  daysUntilExpiry?: number;
  outstandingDispositionQuantity: number;
  disposableQuantity: number;
}

export interface InventoryDamageAffectedOrder {
  salesOrderId: number;
  salesOrderItemId: number;
  quantity: number;
  impact: 'REALLOCATED' | 'BACKORDERED' | 'QUALITY_EXCEPTION';
}

export interface InventoryDamagePreviewLine {
  batchId: number;
  batchCode: string;
  productSkuCode: string;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  locationCode: string;
  onHandQuantity: number;
  reservedQuantity: number;
  requestedDamageQuantity: number;
  reallocatedQuantity: number;
  backorderedQuantity: number;
  qualityExceptionQuantity: number;
  affectedSalesOrders: InventoryDamageAffectedOrder[];
}

export interface InventoryDamagePreview {
  confirmationRequired: boolean;
  totalReallocatedQuantity: number;
  totalBackorderedQuantity: number;
  totalQualityExceptionQuantity: number;
  lines: InventoryDamagePreviewLine[];
}

export interface InventoryDispositionLine {
  id: number;
  sourceBatchId: number;
  resultBatchId?: number;
  productSkuId: number;
  productSkuCode: string;
  productName: string;
  sourceBatchCode: string;
  resultBatchCode?: string;
  warehouseId: number;
  warehouseName: string;
  locationId: number;
  locationCode: string;
  quantity: number;
  executedQuantity: number;
  outstandingQuantity: number;
  reallocatedReservedQuantity: number;
  backorderedQuantity: number;
  qualityExceptionQuantity: number;
  fromQualityStatus: InventoryQualityStatus;
  toQualityStatus: InventoryQualityStatus;
  expiryDate?: string;
}

export interface InventoryDispositionExecutionLine {
  id: number;
  dispositionLineId: number;
  inventoryBatchId: number;
  quantity: number;
  quantityBefore: number;
  quantityAfter: number;
  stockTransactionId: number;
  postingId: string;
}

export interface InventoryDispositionExecution {
  id: number;
  executionNumber: string;
  executedBy: number;
  executedByName: string;
  executedAt: string;
  executionNote?: string;
  lines: InventoryDispositionExecutionLine[];
}

export interface InventoryDisposition {
  id: number;
  dispositionNumber: string;
  dispositionType: InventoryDispositionType;
  status: InventoryDispositionStatus;
  reviewModeSnapshot?: InventoryDispositionReviewMode;
  reasonCode: string;
  reasonDetail?: string;
  externalReference?: string;
  recallNoticeNumber?: string;
  requestedBy: number;
  requestedByName: string;
  requestedAt: string;
  reviewedBy?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  reviewComment?: string;
  cancelledBy?: number;
  cancelledByName?: string;
  cancelledAt?: string;
  cancelReason?: string;
  lines: InventoryDispositionLine[];
  executions: InventoryDispositionExecution[];
}

export interface InventoryDispositionPolicy {
  reviewMode: InventoryDispositionReviewMode;
  automaticExpiryEnabled: boolean;
  businessTimezone: string;
  supportedReviewModes: InventoryDispositionReviewMode[];
}

export interface ReportInventoryDamagePayload {
  lines: InventoryDispositionQuantity[];
  reasonCode: string;
  reasonDetail?: string;
  externalReference?: string;
  recallNoticeNumber?: string;
  confirmSalesImpact: boolean;
}

export interface CreateInventoryScrapPayload {
  lines: InventoryDispositionQuantity[];
  reasonCode: string;
  reasonDetail?: string;
  externalReference?: string;
  recallNoticeNumber?: string;
  saveAsDraft: boolean;
}

export const INVENTORY_DISPOSITION_QUERY_KEY = ['inventory-disposition'] as const;

export function inventoryDispositionIdempotencyKey(prefix: string): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `inventory-disposition-${prefix}-${random}`;
}

export async function listInventoryDispositionCandidates(search = ''): Promise<InventoryDispositionCandidate[]> {
  const query = new URLSearchParams({ search: search.trim() });
  return apiRequest<InventoryDispositionCandidate[]>(`/api/inventory-dispositions/candidates?${query}`);
}

export async function previewInventoryDamage(payload: ReportInventoryDamagePayload): Promise<InventoryDamagePreview> {
  return apiRequest<InventoryDamagePreview>('/api/inventory-dispositions/damage-preview', {
    method: 'POST', body: payload,
  });
}

export async function reportInventoryDamage(
  payload: ReportInventoryDamagePayload, idempotencyKey: string,
): Promise<InventoryDisposition> {
  return apiRequest<InventoryDisposition>('/api/inventory-dispositions/damage-reports', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export async function createInventoryScrapOrder(
  payload: CreateInventoryScrapPayload, idempotencyKey: string,
): Promise<InventoryDisposition> {
  return apiRequest<InventoryDisposition>('/api/inventory-dispositions/scrap-orders', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export async function listInventoryDispositions(
  type?: InventoryDispositionType, status?: InventoryDispositionStatus,
): Promise<InventoryDisposition[]> {
  const query = new URLSearchParams();
  if (type) query.set('type', type);
  if (status) query.set('status', status);
  return apiRequest<InventoryDisposition[]>(`/api/inventory-dispositions/orders${query.size ? `?${query}` : ''}`);
}

export async function decideInventoryDisposition(
  id: number, action: 'approve' | 'reject' | 'cancel', comment: string, idempotencyKey: string,
): Promise<InventoryDisposition> {
  return apiRequest<InventoryDisposition>(`/api/inventory-dispositions/orders/${id}/${action}`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: { comment },
  });
}

export async function submitInventoryDisposition(
  id: number, idempotencyKey: string,
): Promise<InventoryDisposition> {
  return apiRequest<InventoryDisposition>(`/api/inventory-dispositions/orders/${id}/submit`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export async function executeInventoryDisposition(
  id: number, lines: InventoryDispositionExecutionQuantity[], executionNote: string | undefined,
  idempotencyKey: string,
): Promise<InventoryDisposition> {
  return apiRequest<InventoryDisposition>(`/api/inventory-dispositions/orders/${id}/executions`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: { lines, executionNote },
  });
}

export async function getInventoryDispositionPolicy(): Promise<InventoryDispositionPolicy> {
  return apiRequest<InventoryDispositionPolicy>('/api/inventory-dispositions/policy');
}

export async function updateInventoryDispositionPolicy(
  reviewMode: InventoryDispositionReviewMode, automaticExpiryEnabled: boolean,
  businessTimezone: string, reason: string,
): Promise<InventoryDispositionPolicy> {
  return apiRequest<InventoryDispositionPolicy>('/api/inventory-dispositions/policy', {
    method: 'PUT', body: { reviewMode, automaticExpiryEnabled, businessTimezone, reason },
  });
}
