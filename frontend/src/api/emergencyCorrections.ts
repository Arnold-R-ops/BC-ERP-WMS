import type { components } from './schema';
import { apiRequest } from './http';

export type EmergencyCorrection = components['schemas']['EmergencyStockCorrectionResponse'];
export type CreateEmergencyCorrectionPayload = components['schemas']['EmergencyStockCorrectionRequest'];
export type EmergencyCorrectionActionPayload = components['schemas']['EmergencyStockCorrectionActionRequest'];
export type EmergencyCorrectionStatus = NonNullable<EmergencyCorrection['status']>;

export const EMERGENCY_CORRECTIONS_QUERY_KEY = ['emergency-stock-corrections'] as const;

export async function listEmergencyCorrections(
  status?: EmergencyCorrectionStatus,
): Promise<EmergencyCorrection[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest<EmergencyCorrection[]>(`/api/emergency-stock-corrections${query}`);
}

export async function getEmergencyCorrection(id: number): Promise<EmergencyCorrection> {
  return apiRequest<EmergencyCorrection>(`/api/emergency-stock-corrections/${id}`);
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

export async function applyEmergencyCorrection(id: number): Promise<EmergencyCorrection> {
  return apiRequest<EmergencyCorrection>(`/api/emergency-stock-corrections/${id}/apply`, {
    method: 'POST',
  });
}
