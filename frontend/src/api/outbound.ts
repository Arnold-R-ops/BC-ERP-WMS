import type { components } from './schema';
import { apiRequest } from './http';

export type OutboundTask = components['schemas']['OutboundTaskResponse'];
export type ConfirmPickingPayload = components['schemas']['ConfirmPickingRequest'];
export type OutboundTaskStatus = 'PENDING' | 'PICKING' | 'COMPLETED';

export interface OutboundTaskListParams {
  salesOrderId?: number;
  status?: OutboundTaskStatus;
}

export const OUTBOUND_TASKS_QUERY_KEY = ['outbound-tasks'] as const;

export async function listOutboundTasks(
  params: OutboundTaskListParams = {},
): Promise<OutboundTask[]> {
  const search = new URLSearchParams();
  if (params.salesOrderId !== undefined) {
    search.set('salesOrderId', String(params.salesOrderId));
  }
  if (params.status !== undefined) {
    search.set('status', params.status);
  }
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<OutboundTask[]>(`/api/outbound-tasks${query}`);
}

export async function getOutboundTask(id: number): Promise<OutboundTask> {
  return apiRequest<OutboundTask>(`/api/outbound-tasks/${id}`);
}

export async function confirmOutboundTask(
  id: number,
  payload: ConfirmPickingPayload,
): Promise<OutboundTask> {
  return apiRequest<OutboundTask>(`/api/outbound-tasks/${id}/confirm`, {
    method: 'POST',
    body: payload,
  });
}

export async function batchConfirmOutboundTasks(taskIds: number[]): Promise<OutboundTask[]> {
  return apiRequest<OutboundTask[]>('/api/outbound-tasks/batch-confirm', {
    method: 'POST',
    body: taskIds,
  });
}
