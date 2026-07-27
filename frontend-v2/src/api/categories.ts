import { api } from './client';
import type { CategoryResponse } from './types';

export function getCategories() {
  return api<CategoryResponse[]>('/api/v1/categories');
}

export function createCategory(categoryName: string) {
  return api<CategoryResponse>('/api/v1/categories', {
    method: 'POST',
    body: { categoryName },
    auth: true
  });
}

export function createChildCategory(parentId: number, categoryName: string) {
  return api<CategoryResponse>(`/api/v1/categories/${parentId}/children`, {
    method: 'POST',
    body: { categoryName },
    auth: true
  });
}

export function updateCategory(categoryId: number, categoryName: string) {
  return api<CategoryResponse>(`/api/v1/categories/${categoryId}`, {
    method: 'PUT',
    body: { categoryName },
    auth: true
  });
}

export function deleteCategory(categoryId: number) {
  return api<unknown>(`/api/v1/categories/${categoryId}`, { method: 'DELETE', auth: true });
}
