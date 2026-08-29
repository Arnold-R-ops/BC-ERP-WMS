import { apiRequest } from './http';

export type InventoryQualityStatus =
  | 'AVAILABLE' | 'PENDING_INSPECTION' | 'QUARANTINED' | 'DAMAGED'
  | 'EXPIRED' | 'RECALL_HOLD' | 'RETURN_INSPECTION';
export type InventoryQualityActionType = 'HOLD' | 'RELEASE';
export type InventoryQualityActionStatus =
  | 'COMPLETED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type InventoryQualityHoldType = 'QUARANTINE' | 'RECALL';
export type InventoryQualityReleaseMode = 'DIRECT_WITH_REASON' | 'APPROVAL_REQUIRED';

export interface InventoryQualityQuantity { batchId: number; quantity: number }
export interface InventoryQualityCandidate {
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
  availableQuantity: number;
  qualityStatus: InventoryQualityStatus;
  productionDate?: string;
  expiryDate?: string;
}

export interface InventoryQualityAffectedOrder {
  salesOrderId: number;
  salesOrderItemId: number;
  quantity: number;
}

export interface InventoryQualityHoldPreviewLine {
  batchId: number;
  batchCode: string;
  productSkuCode: string;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  locationCode: string;
  onHandQuantity: number;
  reservedQuantity: number;
  requestedHoldQuantity: number;
  reservationQuantityToReallocate: number;
  alternateSaleableQuantity: number;
  shortageQuantity: number;
  affectedSalesOrders: InventoryQualityAffectedOrder[];
}

export interface InventoryQualityHoldPreview {
  executable: boolean;
  confirmationRequired: boolean;
  totalReservationQuantityToReallocate: number;
  totalShortageQuantity: number;
  lines: InventoryQualityHoldPreviewLine[];
}

export interface InventoryQualityActionLine {
  id: number;
  sourceBatchId: number;
  resultBatchId?: number;
  sourceBatchCode: string;
  resultBatchCode?: string;
  productSkuId: number;
  productSkuCode: string;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  locationId: number;
  locationCode: string;
  quantity: number;
  reallocatedReservedQuantity: number;
  fromStatus: InventoryQualityStatus;
  toStatus: InventoryQualityStatus;
}

export interface InventoryQualityAction {
  id: number;
  version: number;
  actionNumber: string;
  actionType: InventoryQualityActionType;
  holdType?: InventoryQualityHoldType;
  status: InventoryQualityActionStatus;
  releaseModeSnapshot?: InventoryQualityReleaseMode;
  reasonCode: string;
  reasonDetail?: string;
  recallNoticeNumber?: string;
  externalReference?: string;
  requestedBy: number;
  requestedByName: string;
  requestedAt: string;
  reviewedBy?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  reviewComment?: string;
  lines: InventoryQualityActionLine[];
}

export interface InventoryQualityPolicy {
  releaseMode: InventoryQualityReleaseMode;
  availableModes: InventoryQualityReleaseMode[];
}

export interface InventoryQualityHoldPayload {
  holdType: InventoryQualityHoldType;
  lines: InventoryQualityQuantity[];
  reasonCode: string;
  reasonDetail?: string;
  recallNoticeNumber?: string;
  externalReference?: string;
  confirmReservationReallocation: boolean;
}

export interface InventoryQualityReleasePayload {
  lines: InventoryQualityQuantity[];
  reasonCode: string;
  reasonDetail?: string;
  externalReference?: string;
}

export const INVENTORY_QUALITY_QUERY_KEY = ['inventory-quality'] as const;

export function createInventoryQualityIdempotencyKey(prefix: 'hold' | 'release'): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `inventory-quality-${prefix}-${random}`;
}

export async function listInventoryQualityCandidates(search = ''): Promise<InventoryQualityCandidate[]> {
  const query = new URLSearchParams({ search: search.trim() });
  return apiRequest<InventoryQualityCandidate[]>(`/api/inventory-quality/candidates?${query.toString()}`);
}

export async function previewInventoryQualityHold(
  payload: InventoryQualityHoldPayload,
): Promise<InventoryQualityHoldPreview> {
  return apiRequest<InventoryQualityHoldPreview>('/api/inventory-quality/hold-preview', {
    method: 'POST', body: payload,
  });
}

export async function createInventoryQualityHold(
  payload: InventoryQualityHoldPayload,
  idempotencyKey: string,
): Promise<InventoryQualityAction> {
  return apiRequest<InventoryQualityAction>('/api/inventory-quality/holds', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export async function createInventoryQualityRelease(
  payload: InventoryQualityReleasePayload,
  idempotencyKey: string,
): Promise<InventoryQualityAction> {
  return apiRequest<InventoryQualityAction>('/api/inventory-quality/releases', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export async function listInventoryQualityActions(
  type?: InventoryQualityActionType,
  status?: InventoryQualityActionStatus,
): Promise<InventoryQualityAction[]> {
  const query = new URLSearchParams();
  if (type) query.set('type', type);
  if (status) query.set('status', status);
  const suffix = query.size ? `?${query.toString()}` : '';
  return apiRequest<InventoryQualityAction[]>(`/api/inventory-quality/actions${suffix}`);
}

export async function reviewInventoryQualityAction(
  id: number,
  decision: 'approve' | 'reject' | 'withdraw',
  comment: string,
): Promise<InventoryQualityAction> {
  return apiRequest<InventoryQualityAction>(`/api/inventory-quality/actions/${id}/${decision}`, {
    method: 'POST', body: { comment },
  });
}

export async function getInventoryQualityPolicy(): Promise<InventoryQualityPolicy> {
  return apiRequest<InventoryQualityPolicy>('/api/inventory-quality/policy');
}

export async function updateInventoryQualityPolicy(
  releaseMode: InventoryQualityReleaseMode,
  reason: string,
): Promise<InventoryQualityPolicy> {
  return apiRequest<InventoryQualityPolicy>('/api/inventory-quality/policy', {
    method: 'PUT', body: { releaseMode, reason, confirmed: true },
  });
}
