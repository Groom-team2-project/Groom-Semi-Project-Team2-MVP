import { api } from './client';
import type { ReviewEligibilityResponse, ReviewResponse } from './types';

export function getProductReviews(productId: number | string) {
  return api<ReviewResponse[]>(`/api/v1/products/${productId}/reviews`);
}
// 로그인 + 결제완료 구매 이력 + 아직 안 쓴 상태일 때만 eligible: true
// 비로그인이어도 호출 가능하고, 그 경우 그냥 eligible: false로 응답한다
export function getReviewEligibility(productId: number | string) {
  return api<ReviewEligibilityResponse>(
      `/api/v1/products/${productId}/reviews/eligibility`,
      { auth: true }
  );
}

// 결제 완료한 구매자만 작성 가능
export function createReview(body: { productId: number; content: string; rating: number }) {
  return api<ReviewResponse>('/api/v1/reviews', { method: 'POST', body, auth: true });
}

export function updateReview(reviewId: number, body: { content: string; rating: number }) {
  return api<ReviewResponse>(`/api/v1/reviews/${reviewId}`, { method: 'PUT', body, auth: true });
}

export function deleteReview(reviewId: number) {
  return api<unknown>(`/api/v1/reviews/${reviewId}`, { method: 'DELETE', auth: true });
}
