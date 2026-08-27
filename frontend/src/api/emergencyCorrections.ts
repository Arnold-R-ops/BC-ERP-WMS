import type { components } from './schema';
import { apiRequest } from './http';

type GeneratedEmergencyCorrection = components['schemas']['EmergencyStockCorrectionResponse'];
export type EmergencyCorrectionBatchOption = components['schemas']['InventoryBatchResponse'];

export type EmergencyCorrectionApprovalMode = 'NONE' | 'TWO_PERSON' | 'THREE_PERSON';

export type EmergencyCorrection = GeneratedEmergencyCorrection & {
  productSkuCode?: string;
  productSkuName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  locationCode?: string;
  createdBy?: number;
  approvalModeSnapshot?: EmergencyCorrectionApprovalMode;
  appliedQuantityBefore?: number;
  appliedQuantityAfter?: number;
  postingId?: string;
  rejectedBy?: number;
  rejectedAt?: string;
  rejectionComment?: string;
  submissionComment?: string;
};
export type CreateEmergencyCorrectionPayload = components['schemas']['EmergencyStockCorrectionRequest'];
export interface EmergencyCorrectionActionPayload { comment: string }
export type EmergencyCorrectionStatus = NonNullable<EmergencyCorrection['status']>;

export interface EmergencyCorrectionPolicy {
  configuredMode: EmergencyCorrectionApprovalMode;
  effectiveMode: EmergencyCorrectionApprovalMode;
  approvalEntitled: boolean;
  configurable: boolean;
  availableModes: EmergencyCorrectionApprovalMode[];
}

export interface UpdateEmergencyCorrectionPolicyPayload {
  mode: EmergencyCorrectionApprovalMode;
  reason: string;
  confirmed: true;
}

export interface EmergencyCorrectionImpact {
  correctionId: number;
  snapshotSystemQty: number;
  countedQty: number;
  adjustmentQty: number;
  currentQty: number;
  reservedQty: number;
  projectedQty: number;
  canApply: boolean;
  affectedSalesOrderIds: number[];
  previewToken: string;
  previewExpiresAt: string;
}

export const EMERGENCY_CORRECTIONS_QUERY_KEY = ['emergency-stock-corrections'] as const;
export const EMERGENCY_CORRECTION_POLICY_QUERY_KEY = ['emergency-stock-corrections', 'policy'] as const;

export async function listEmergencyCorrections(
  status?: EmergencyCorrectionStatus,
): Promise<EmergencyCorrection[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest<EmergencyCorrection[]>(`/api/emergency-stock-corrections${query}`);
}

export async function getEmergencyCorrection(id: number): Promise<EmergencyCorrection> {
  return apiRequest<EmergencyCorrection>(`/api/emergency-stock-corrections/${id}`);
}

export async function getEmergencyCorrectionPolicy(): Promise<EmergencyCorrectionPolicy> {
  return apiRequest<EmergencyCorrectionPolicy>('/api/emergency-stock-corrections/policy');
}

export async function listEmergencyCorrectionBatchOptions(
  productSkuId: number,
): Promise<EmergencyCorrectionBatchOption[]> {
  return apiRequest<EmergencyCorrectionBatchOption[]>(
    `/api/emergency-stock-corrections/batch-options?productSkuId=${encodeURIComponent(String(productSkuId))}`,
  );
}

export async function updateEmergencyCorrectionPolicy(
  payload: UpdateEmergencyCorrectionPolicyPayload,
): Promise<EmergencyCorrectionPolicy> {
  return apiRequest<EmergencyCorrectionPolicy>('/api/emergency-stock-corrections/policy', {
    method: 'PUT',
    body: payload,
  });
}

export async function getEmergencyCorrectionImpact(id: number): Promise<EmergencyCorrectionImpact> {
  return apiRequest<EmergencyCorrectionImpact>(`/api/emergency-stock-corrections/${id}/impact`);
}

export async function createEmergencyCorrection(
  payload: CreateEmergencyCorrectionPayload,
): Promise<EmergencyCorrection> {
  return apiRequest<EmergencyCorrection>('/api/emergency-stock-corrections', {
    method: 'POST',
    body: payload,
  });
}

async function runCorrectionAction(
  id: number,
  action: 'submit' | 'review' | 'approve' | 'reject',
  payload: EmergencyCorrectionActionPayload,
): Promise<EmergencyCorrection> {
  return apiRequest<EmergencyCorrection>(`/api/emergency-stock-corrections/${id}/${action}`, {
    method: 'POST',
    body: payload,
  });
}

export const submitEmergencyCorrection = (
  id: number,
  payload: EmergencyCorrectionActionPayload,
): Promise<EmergencyCorrection> => runCorrectionAction(id, 'submit', payload);

export const reviewEmergencyCorrection = (
  id: number,
  payload: EmergencyCorrectionActionPayload,
): Promise<EmergencyCorrection> => runCorrectionAction(id, 'review', payload);

export const approveEmergencyCorrection = (
  id: number,
  payload: EmergencyCorrectionActionPayload,
): Promise<EmergencyCorrection> => runCorrectionAction(id, 'approve', payload);

export const rejectEmergencyCorrection = (
  id: number,
  payload: EmergencyCorrectionActionPayload,
): Promise<EmergencyCorrection> => runCorrectionAction(id, 'reject', payload);

export async function applyEmergencyCorrection(
  id: number,
  idempotencyKey: string,
  previewToken: string,
): Promise<EmergencyCorrection> {
  const normalizedPreviewToken = previewToken.trim();
  if (!normalizedPreviewToken) {
    throw new Error('Emergency correction preview token is required');
  }
  return apiRequest<EmergencyCorrection>(`/api/emergency-stock-corrections/${id}/apply`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { confirmed: true, previewToken: normalizedPreviewToken },
  });
}
