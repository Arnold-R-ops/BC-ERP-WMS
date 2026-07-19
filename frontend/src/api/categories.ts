import type { components } from './schema';
import { apiRequest } from './http';

export type Category = components['schemas']['CategoryResponse'];
export type CreateCategoryPayload = components['schemas']['CreateCategoryRequest'];
export type UpdateCategoryPayload = components['schemas']['UpdateCategoryRequest'];

export const CATEGORIES_QUERY_KEY = ['categories'] as const;

export async function listCategories(enabledOnly = false): Promise<Category[]> {
  return apiRequest<Category[]>(`/api/categories?enabledOnly=${enabledOnly}`);
}

export async function getCategoryTree(enabledOnly = false): Promise<Category[]> {
  return apiRequest<Category[]>(`/api/categories/tree?enabledOnly=${enabledOnly}`);
}

export async function createCategory(payload: CreateCategoryPayload): Promise<Category> {
  return apiRequest<Category>('/api/categories', { method: 'POST', body: payload });
}

export async function updateCategory(id: number, payload: UpdateCategoryPayload): Promise<Category> {
  return apiRequest<Category>(`/api/categories/${id}`, { method: 'PUT', body: payload });
}

export async function moveCategory(id: number, parentId?: number): Promise<Category> {
  return apiRequest<Category>(`/api/categories/${id}/move`, {
    method: 'PUT',
    body: { parentId },
  });
}

export async function setCategoryEnabled(id: number, enabled: boolean): Promise<Category> {
  return apiRequest<Category>(`/api/categories/${id}/${enabled ? 'activate' : 'deactivate'}`, {
    method: 'PUT',
  });
}

export async function deleteCategory(id: number): Promise<void> {
  return apiRequest<void>(`/api/categories/${id}`, { method: 'DELETE' });
}
