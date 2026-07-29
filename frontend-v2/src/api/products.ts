import { api } from './client';
import type {ImageResponse, PageResponse, ProductDetail, ProductListItem, ProductSortType} from './types';

export function getProducts(params: { page?: number; size?: number; keyword?: string; categoryId?: number; sort?: ProductSortType } = {}) {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 12));
  if (params.keyword) q.set('keyword', params.keyword);
  if (params.categoryId != null) q.set('categoryId', String(params.categoryId));
  if (params.sort) q.set('sort', params.sort);
  return api<PageResponse<ProductListItem>>(`/api/v1/products?${q}`);
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
