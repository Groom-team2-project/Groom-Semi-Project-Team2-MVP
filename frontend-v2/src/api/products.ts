import { api } from './client';
import type { ImageResponse, PageResponse, ProductDetail, ProductListItem } from './types';

export function getProducts(params: { page?: number; size?: number; keyword?: string } = {}) {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 12));
  if (params.keyword) q.set('keyword', params.keyword);
  return api<PageResponse<ProductListItem>>(`/api/v1/products?${q}`);
}

export function getProduct(productId: number | string) {
  return api<ProductDetail>(`/api/v1/products/${productId}`);
}

export function createProduct(body: {
  productName: string;
  productPrice: number;
  stocks: number;
  categoryId: number;
}, productImage: File) {
  const form = new FormData();
  form.append('request', new Blob([JSON.stringify({
    productName: body.productName,
    productPrice: body.productPrice,
    stock: body.stocks,
    category: body.categoryId
  })], { type: 'application/json' }));
  form.append('productImage', productImage);
  return api<unknown>('/api/v1/products', { method: 'POST', body: form, auth: true });
}

export function updateProduct(
  productId: number,
  body: { productName: string; productPrice: number; categoryId: number },
  productImage?: File | null
) {
  const form = new FormData();
  form.append('request', new Blob([JSON.stringify({
    productName: body.productName,
    productPrice: body.productPrice,
    category: body.categoryId
  })], { type: 'application/json' }));
  if (productImage) form.append('productImage', productImage);
  return api<unknown>(`/api/v1/products/${productId}`, { method: 'PUT', body: form, auth: true });
}

export function deleteProduct(productId: number) {
  return api<unknown>(`/api/v1/products/${productId}`, { method: 'DELETE', auth: true });
}

export function addProductImage(productId: number, image: File) {
  const form = new FormData();
  form.append('image', image);
  return api<ImageResponse>(`/api/v1/products/${productId}/images`, {
    method: 'POST',
    body: form,
    auth: true
  });
}

export function updateProductImage(productId: number, imageId: number, image: File) {
  const form = new FormData();
  form.append('image', image);
  return api<ImageResponse>(`/api/v1/products/${productId}/images/${imageId}`, {
    method: 'PUT',
    body: form,
    auth: true
  });
}

export function deleteProductImage(productId: number, imageId: number) {
  return api<ImageResponse>(`/api/v1/products/${productId}/images/${imageId}`, {
    method: 'DELETE',
    auth: true
  });
}
