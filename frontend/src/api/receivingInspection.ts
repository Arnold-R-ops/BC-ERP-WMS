import { apiRequest } from './http';

export type ReceivingInspectionRequirement = 'INHERIT' | 'REQUIRED' | 'EXEMPT';
export type ReceivingInspectionReviewMode = 'DIRECT' | 'REVIEW_REQUIRED';
export type ReceivingInspectionTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'COMPLETED';
export type ReceivingInspectionResultStatus = 'COMPLETED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
export type ReceivingInspectionMethod = 'FULL' | 'SAMPLING';

export interface ReceivingInspectionResultLine {
  id: number;
  inspectionTaskLineId: number;
  sourceBatchId: number;
  passedQuantity: number;
  quarantinedQuantity: number;
  passedBatchId?: number;
  quarantinedBatchId?: number;
}

export interface ReceivingInspectionResult {
  id: number;
  resultNumber: string;
  inspectionTaskId: number;
  status: ReceivingInspectionResultStatus;
  inspectionMethod: ReceivingInspectionMethod;
  sampleQuantity?: number;
  defectQuantity: number;
  reasonCode: string;
  reasonDetail?: string;
  submittedBy: number;
  submittedByName: string;
  submittedAt: string;
  reviewedBy?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  reviewComment?: string;
  lines: ReceivingInspectionResultLine[];
}

export interface ReceivingInspectionTaskLine {
  id: number;
  productSkuId: number;
  skuCode: string;
  productName: string;
  sourceBatchId: number;
  batchCode: string;
  locationId: number;
  locationCode: string;
  plannedQuantity: number;
  pendingQuantity: number;
  pendingReviewQuantity: number;
  passedQuantity: number;
  quarantinedQuantity: number;
}

export interface ReceivingInspectionTask {
  id: number;
  taskNumber: string;
  sourceType: 'RECEIPT' | 'REINSPECTION';
  sourceReference: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  status: ReceivingInspectionTaskStatus;
  reviewMode: ReceivingInspectionReviewMode;
  createdAt: string;
  completedAt?: string;
  correctionOfResultId?: number;
  lines: ReceivingInspectionTaskLine[];
  results: ReceivingInspectionResult[];
}

export interface ReceivingInspectionPolicy {
  defaultInspectionRequired: boolean;
  reviewMode: ReceivingInspectionReviewMode;
  skuRequirements: ReceivingInspectionRequirement[];
  reviewModes: ReceivingInspectionReviewMode[];
}

export interface SubmitReceivingInspectionPayload {
  inspectionMethod: ReceivingInspectionMethod;
  sampleQuantity?: number;
  defectQuantity: number;
  reasonCode: string;
  reasonDetail?: string;
  lines: Array<{
    inspectionTaskLineId: number;
    passedQuantity: number;
    quarantinedQuantity: number;
  }>;
}

export interface CreateReinspectionPayload {
  originalResultId: number;
  inventoryBatchId: number;
  quantity: number;
  reason: string;
  confirmReservationReallocation: boolean;
}

export type ArrivalExceptionType =
  | 'SHORT_RECEIPT' | 'OVER_RECEIPT' | 'WRONG_ITEM'
  | 'DAMAGED' | 'EXPIRY_FAILED' | 'OTHER';
export type ArrivalDisposition =
  | 'AWAITING_REDELIVERY' | 'REJECTED_AT_DOCK'
  | 'RECEIVED_FOR_INSPECTION' | 'RECORDED_ONLY';

export interface ArrivalException {
  id: number;
  exceptionNumber: string;
  receivingTaskId: number;
  productSkuId?: number;
  skuCode?: string;
  exceptionType: ArrivalExceptionType;
  disposition: ArrivalDisposition;
  observedQuantity: number;
  reasonDetail?: string;
  recordedByName: string;
  recordedAt: string;
}

export const RECEIVING_INSPECTION_QUERY_KEY = ['receiving-inspections'] as const;

export function inspectionIdempotencyKey(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `receiving-inspection-${prefix}-${suffix}`;
}

export async function listReceivingInspections(
  status?: ReceivingInspectionTaskStatus,
): Promise<ReceivingInspectionTask[]> {
  const query = status ? `?status=${status}` : '';
  return apiRequest<ReceivingInspectionTask[]>(`/api/receiving-inspections${query}`);
}

export async function getReceivingInspection(id: number): Promise<ReceivingInspectionTask> {
  return apiRequest<ReceivingInspectionTask>(`/api/receiving-inspections/${id}`);
}

export async function submitReceivingInspection(
  id: number,
  payload: SubmitReceivingInspectionPayload,
  key: string,
): Promise<ReceivingInspectionResult> {
  return apiRequest<ReceivingInspectionResult>(`/api/receiving-inspections/${id}/results`, {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: payload,
  });
}

export async function reviewReceivingInspection(
  id: number,
  decision: 'approve' | 'reject',
  comment: string,
  key: string,
): Promise<ReceivingInspectionResult> {
  return apiRequest<ReceivingInspectionResult>(
    `/api/receiving-inspections/results/${id}/${decision}`,
    { method: 'POST', headers: { 'Idempotency-Key': key }, body: { comment } },
  );
}

export async function getReceivingInspectionPolicy(): Promise<ReceivingInspectionPolicy> {
  return apiRequest<ReceivingInspectionPolicy>('/api/receiving-inspections/policy');
}

export async function updateReceivingInspectionPolicy(
  defaultInspectionRequired: boolean,
  reviewMode: ReceivingInspectionReviewMode,
  reason: string,
): Promise<ReceivingInspectionPolicy> {
  return apiRequest<ReceivingInspectionPolicy>('/api/receiving-inspections/policy', {
    method: 'PUT', body: { defaultInspectionRequired, reviewMode, reason },
  });
}

export async function createReceivingReinspection(
  payload: CreateReinspectionPayload,
  key: string,
): Promise<ReceivingInspectionTask> {
  return apiRequest<ReceivingInspectionTask>('/api/receiving-inspections/reinspections', {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: payload,
  });
}

export async function listArrivalExceptions(receivingTaskId: number): Promise<ArrivalException[]> {
  return apiRequest<ArrivalException[]>(
    `/api/receiving-tasks/${receivingTaskId}/arrival-exceptions`,
  );
}

export async function recordArrivalException(
  receivingTaskId: number,
  payload: {
    productSkuId?: number;
    exceptionType: ArrivalExceptionType;
    disposition: ArrivalDisposition;
    observedQuantity: number;
    reasonDetail?: string;
  },
  key: string,
): Promise<ArrivalException> {
  return apiRequest<ArrivalException>(
    `/api/receiving-tasks/${receivingTaskId}/arrival-exceptions`,
    { method: 'POST', headers: { 'Idempotency-Key': key }, body: payload },
  );
}
