import { api } from './client';
import type { CartCheckoutResponse, CartResponse } from './types';

export function getCart() {
  return api<CartResponse>('/api/v1/carts', { auth: true });
}

export function addCartItem(productId: number, quantity: number) {
  return api<CartResponse>('/api/v1/carts/items', {
    method: 'POST',
    body: { productId, quantity },
    auth: true
  });
}

export function updateCartItem(cartItemId: number, quantity: number) {
  return api<CartResponse>(`/api/v1/carts/items/${cartItemId}`, {
    method: 'PATCH',
    body: { quantity },
    auth: true
  });
}

export function removeCartItem(cartItemId: number) {
  return api<CartResponse>(`/api/v1/carts/items/${cartItemId}`, { method: 'DELETE', auth: true });
}

export function clearCart() {
  return api<CartResponse>('/api/v1/carts', { method: 'DELETE', auth: true });
}

export function checkoutCart() {
  return api<CartCheckoutResponse>('/api/v1/carts/checkout', { method: 'POST', auth: true });
}
