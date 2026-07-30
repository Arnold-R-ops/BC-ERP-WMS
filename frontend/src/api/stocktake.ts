import type { components } from './schema';
import { apiRequest } from './http';

export type StocktakeTask = components['schemas']['StocktakeTaskResponse'];
export type StocktakeItem = components['schemas']['StocktakeItemResponse'];
export type StocktakeReviewItem = components['schemas']['StocktakeItemDetailResponse'];
export type CreateStocktakePayload = components['schemas']['CreateStocktakeTaskRequest'];
export type SubmitCountPayload = components['schemas']['SubmitCountRequest'];
export type ReviewStocktakePayload = components['schemas']['ReviewStocktakeRequest'];

export type StocktakeStatus =
  | 'CREATED'
  | 'COUNTING'
  | 'REVIEWING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'VOIDED';

export type StocktakeCycleType = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'ADHOC';

export interface StocktakeListParams {
  warehouseId?: number;
  status?: StocktakeStatus;
}

export const STOCKTAKE_TASKS_QUERY_KEY = ['stocktake-tasks'] as const;

export async function listStocktakeTasks(
  params: StocktakeListParams = {},
): Promise<StocktakeTask[]> {
  const search = new URLSearchParams();
  if (params.warehouseId !== undefined) search.set('warehouseId', String(params.warehouseId));
  if (params.status !== undefined) search.set('status', params.status);
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<StocktakeTask[]>(`/api/stocktake/tasks${query}`);
}

export async function getStocktakeTask(id: number): Promise<StocktakeTask> {
  return apiRequest<StocktakeTask>(`/api/stocktake/tasks/${id}`);
}

export async function createStocktakeTask(
  payload: CreateStocktakePayload,
): Promise<StocktakeTask> {
  return apiRequest<StocktakeTask>('/api/stocktake/tasks', { method: 'POST', body: payload });
}

export async function startStocktake(id: number): Promise<StocktakeTask> {
  return apiRequest<StocktakeTask>(`/api/stocktake/tasks/${id}/start`, { method: 'POST' });
}

export async function listStocktakeItems(id: number): Promise<StocktakeItem[]> {
  return apiRequest<StocktakeItem[]>(`/api/stocktake/tasks/${id}/items`);
}

export async function submitStocktakeCount(
  taskId: number,
  itemId: number,
  payload: SubmitCountPayload,
): Promise<StocktakeItem> {
  return apiRequest<StocktakeItem>(`/api/stocktake/tasks/${taskId}/items/${itemId}/count`, {
    method: 'POST',
    body: payload,
  });
}

export async function finishStocktake(id: number): Promise<StocktakeTask> {
  return apiRequest<StocktakeTask>(`/api/stocktake/tasks/${id}/finish`, { method: 'POST' });
}

export async function listStocktakeReviewItems(id: number): Promise<StocktakeReviewItem[]> {
  return apiRequest<StocktakeReviewItem[]>(`/api/stocktake/tasks/${id}/review-items`);
}

export async function reviewStocktake(
  id: number,
  payload: ReviewStocktakePayload,
): Promise<StocktakeTask> {
  return apiRequest<StocktakeTask>(`/api/stocktake/tasks/${id}/review`, {
    method: 'POST',
    body: payload,
  });
}
