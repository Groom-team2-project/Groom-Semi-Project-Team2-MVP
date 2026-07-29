import { api } from './client';
import type { ImageResponse, PageResponse, ProductDetail, ProductListItem } from './types';

export function getProducts(params: { page?: number; size?: number; keyword?: string } = {}) {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 12));
  if (params.keyword) q.set('keyword', params.keyword);
  return api<PageResponse<ProductListItem>>(`/api/v1/products?${q}`);
}

// 관리자 재고 화면은 일부 페이지가 아닌 전체 상품을 보여야 한다.
// 백엔드 목록 API의 페이지 규격은 유지한 채, totalElements를 기준으로 필요한 페이지를 모두 조회한다.
export async function getAllProducts(): Promise<ProductListItem[]> {
  const firstPage = await getProducts({ page: 0, size: 100 });
  const totalPages = Math.ceil(firstPage.totalElements / firstPage.size);

  if (totalPages <= 1) {
    return firstPage.content;
  }

  const remainingPages = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => getProducts({ page: index + 1, size: firstPage.size }))
  );

  return [firstPage, ...remainingPages].flatMap((page) => page.content);
}

export function getProduct(productId: number | string) {
  return api<ProductDetail>(`/api/v1/products/${productId}`);
}

export function createProduct(body: {
  productName: string;
  productPrice: number;
  stocks: number;
  categoryId?: number | null;
}) {
  return api<{ productId: number }>('/api/v1/products', { method: 'POST', body, auth: true });
}

export function updateProduct(productId: number, body: { productName: string; productPrice: number }) {
  return api<unknown>(`/api/v1/products/${productId}`, { method: 'PUT', body, auth: true });
}

export function deleteProduct(productId: number) {
  return api<unknown>(`/api/v1/products/${productId}`, { method: 'DELETE', auth: true });
}

export function addProductImage(productId: number, imageUrl: string) {
  return api<ImageResponse>(`/api/v1/products/${productId}/images`, {
    method: 'POST',
    body: { imageUrl },
    auth: true
  });
}
