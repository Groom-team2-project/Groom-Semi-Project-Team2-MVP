import { api } from './client';
import type { ReviewResponse } from './types';

export function getProductReviews(productId: number | string) {
  return api<ReviewResponse[]>(`/api/v1/products/${productId}/reviews`);
}

// 결제 완료한 구매자만 작성 가능 — 백엔드가 검증한다
export function createReview(body: { productId: number; content: string; rating: number }) {
  return api<ReviewResponse>('/api/v1/reviews', { method: 'POST', body, auth: true });
}

export function updateReview(reviewId: number, body: { content: string; rating: number }) {
  return api<ReviewResponse>(`/api/v1/reviews/${reviewId}`, { method: 'PUT', body, auth: true });
}

export function deleteReview(reviewId: number) {
  return api<unknown>(`/api/v1/reviews/${reviewId}`, { method: 'DELETE', auth: true });
}
