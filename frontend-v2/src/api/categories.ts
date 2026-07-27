import { api } from './client';
import type { CategoryDetailResponse, CategoryResponse } from './types';

// 백엔드 GET /categories는 대분류만 반환한다(중분류 없음).
// 카테고리가 하나도 없으면 CATEGORY_NOT_FOUND(404)를 던지므로 빈 배열로 정규화한다.
export async function getCategories(): Promise<CategoryResponse[]> {
  try {
    return await api<CategoryResponse[]>('/api/v1/categories');
  } catch {
    return [];
  }
}

// 대분류 + 중분류를 모두 담은 평면 목록.
// 전체 트리 API가 없어서 대분류별 상세를 병렬 조회해 합친다.
export async function getCategoryTree(): Promise<CategoryResponse[]> {
  const parents = await getCategories();
  const details = await Promise.all(
    parents.map((p) =>
      api<CategoryDetailResponse>(`/api/v1/categories/${p.categoryId}`).catch(() => null)
    )
  );

  const children: CategoryResponse[] = [];
  details.forEach((detail, i) => {
    const parentId = parents[i].categoryId;
    (detail?.children ?? []).forEach((child) => {
      // 중분류만 수집 (대분류 상세의 children은 중분류 목록)
      if (child.categoryId != null && child.categoryName != null) {
        children.push({
          categoryId: child.categoryId,
          categoryName: child.categoryName,
          parentCategory: parentId
        });
      }
    });
  });

  return [...parents, ...children];
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
