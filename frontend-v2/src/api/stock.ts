import { api } from './client';
import type { StockHistoryResponse, StockResponse } from './types';

export function getStock(productId: number | string) {
  return api<StockResponse>(`/api/v1/products/${productId}/stock`);
}

export function stockIn(productId: number | string, quantity: number, reason: string) {
  return api<StockHistoryResponse>(`/api/v1/products/${productId}/stock-in`, {
    method: 'POST',
    body: { quantity, reason },
    auth: true
  });
}

export function getStockHistories(productId: number | string) {
  return api<StockHistoryResponse[]>(`/api/v1/products/${productId}/stock-histories`, { auth: true });
}
