import type { components } from './schema';
import { apiRequest } from './http';

export type OutboundTask = components['schemas']['OutboundTaskResponse'];
export type ConfirmDispatchPayload = components['schemas']['ConfirmDispatchRequest'];
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

function commandKey(operation: string, resource: number | string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `sales-outbound-${operation}-${resource}-${suffix}`;
}

export async function dispatchOutboundTask(
  id: number,
  payload: ConfirmDispatchPayload,
): Promise<OutboundTask> {
  return apiRequest<OutboundTask>(`/api/outbound-tasks/${id}/dispatch`, {
    method: 'POST',
    headers: { 'Idempotency-Key': commandKey('dispatch', id) },
    body: payload,
  });
}

export async function batchDispatchOutboundTasks(taskIds: number[]): Promise<OutboundTask[]> {
  return apiRequest<OutboundTask[]>('/api/outbound-tasks/batch-dispatch', {
    method: 'POST',
    headers: { 'Idempotency-Key': commandKey('batch-dispatch', taskIds.join('-')) },
    body: taskIds,
  });
}
