type Envelope<T> = { success: boolean; data: T; errorCode?: string; message?: string };

export class ApiError extends Error {
  constructor(public status: number, public code: string | undefined, message: string) {
    super(message);
  }
}

const ACCESS = 'soldout_v3_access';
const REFRESH = 'soldout_v3_refresh';
export const auth = {
  access: () => localStorage.getItem(ACCESS),
  refresh: () => localStorage.getItem(REFRESH),
  setAccess: (accessToken: string) => localStorage.setItem(ACCESS, accessToken),
  save: (accessToken: string, refreshToken: string) => {
    localStorage.setItem(ACCESS, accessToken);
    localStorage.setItem(REFRESH, refreshToken);
  },
  clear: () => {
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
  },
  loggedIn: () => Boolean(localStorage.getItem(ACCESS))
};

type RequestOptions = { method?: string; body?: unknown; auth?: boolean; form?: FormData };

async function raw(path: string, options: RequestOptions) {
  const headers: Record<string, string> = {};
  if (options.auth && auth.access()) headers.Authorization = `Bearer ${auth.access()}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  return fetch(path, {
    method: options.method ?? 'GET',
    credentials: 'include',
    headers,
    body: options.form ?? (options.body === undefined ? undefined : JSON.stringify(options.body))
  });
}

async function reissue() {
  if (!auth.refresh()) return false;
  const response = await raw('/api/v1/auth/reissue', {
    method: 'POST',
    body: { refreshToken: auth.refresh() }
  });
  if (!response.ok) return false;
  const json = (await response.json()) as Envelope<{ accessToken: string; refreshToken: string }>;
  auth.save(json.data.accessToken, json.data.refreshToken);
  return true;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await raw(path, options);
  if (response.status === 401 && options.auth && (await reissue())) response = await raw(path, options);
  if (response.status === 204) return undefined as T;
  let json: Envelope<T> | undefined;
  try { json = await response.json(); } catch { /* empty response */ }
  if (!response.ok) throw new ApiError(response.status, json?.errorCode, json?.message ?? `요청 실패 (${response.status})`);
  return json?.data as T;
}

export const money = (value: number) => `${Number(value ?? 0).toLocaleString('ko-KR')}원`;
export const date = (value?: string | null) => value ? new Date(value).toLocaleString('ko-KR') : '-';

export type Product = {
  productId: number; productName: string; productPrice: number; stocks: number; viewCount: number;
};
export type ProductDetail = {
  productName: string; productPrice: number; productImage?: string; stocks: number; category: number;
  detailImages?: { productId: number; imageId: number; detailImage: string }[];
};
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number };
export type Cart = {
  cartId: number | null; memberId: number; totalQuantity: number; totalPrice: number;
  items: { cartItemId: number; productId: number; productName: string; productPrice: number; quantity: number; lineTotal: number }[];
};
export type Order = {
  orderId: number; status: string; totalPrice: number; canceledAt?: string; paymentExpiresAt?: string; createdAt: string;
  orderItems: { orderItemId: number; productId: number; productName: string; quantity: number; orderPrice: number; itemTotalPrice: number }[];
};
export type Review = { reviewId: number; productId: number; memberId: number; content: string; rating: number; createdAt: string; updatedAt: string };
export type Category = { categoryId: number; categoryName: string; parentCategory: number | null };
export type MemberCoupon = {
  memberCouponId: number; used: boolean; usedAt?: string; expiresAt: string; usable: boolean;
  coupon: { couponId: number; couponName: string; discountType: string; discountValue: number; maxDiscountAmount?: number; minOrderAmount: number; issueEndAt: string };
};

export const store = {
  products: (page = 0, keyword = '', sort = 'LATEST') =>
    api<Page<Product>>(`/api/v1/products?page=${page}&size=12&keyword=${encodeURIComponent(keyword)}&sort=${sort}`),
  product: (id: number) => api<ProductDetail>(`/api/v1/products/${id}`),
  reviews: (id: number) => api<Review[]>(`/api/v1/products/${id}/reviews`),
  reviewCreate: (body: { productId: number; content: string; rating: number }) => api<Review>('/api/v1/reviews', { method: 'POST', body, auth: true }),
  reviewUpdate: (id: number, body: { content: string; rating: number }) => api<Review>(`/api/v1/reviews/${id}`, { method: 'PUT', body, auth: true }),
  reviewDelete: (id: number) => api<void>(`/api/v1/reviews/${id}`, { method: 'DELETE', auth: true }),
  cart: () => api<Cart>('/api/v1/carts', { auth: true }),
  cartAdd: (productId: number, quantity: number) => api<Cart>('/api/v1/carts/items', { method: 'POST', body: { productId, quantity }, auth: true }),
  cartUpdate: (id: number, quantity: number) => api<Cart>(`/api/v1/carts/items/${id}`, { method: 'PATCH', body: { quantity }, auth: true }),
  cartDelete: (id: number) => api<Cart>(`/api/v1/carts/items/${id}`, { method: 'DELETE', auth: true }),
  cartClear: () => api<Cart>('/api/v1/carts', { method: 'DELETE', auth: true }),
  checkout: () => api<{ orderId: number }>('/api/v1/carts/checkout', { method: 'POST', auth: true }),
  purchase: (productId: number, quantity: number) => api<{ orderId: number }>(`/api/v1/products/${productId}/orders`, { method: 'POST', body: { quantity }, auth: true }),
  order: (id: number) => api<Order>(`/api/v1/orders/${id}`, { auth: true }),
  orders: () => api<Order[]>('/api/v1/members/me/orders', { auth: true }),
  cancel: (id: number) => api(`/api/v1/orders/${id}/cancel`, { method: 'POST', auth: true }),
  refund: (id: number) => api(`/api/v1/orders/${id}/payments/refund`, { method: 'POST', body: { cancelReason: '고객 환불 요청' }, auth: true }),
  me: () => api<{ memberId: number; provider: string; role: 'USER' | 'ADMIN'; email: string | null; nickname: string | null }>('/api/v1/members/me', { auth: true }),
  updateMe: (body: { email: string; nickname: string }) => api('/api/v1/members/me', { method: 'PATCH', body, auth: true }),
  points: () => api<{ memberId: number; balance: number }>('/api/v1/members/me/points', { auth: true }),
  pointHistory: () => api<{ pointHistoryId: number; type: string; amount: number; balanceAfter: number; orderId?: number; createdAt: string }[]>('/api/v1/members/me/points/histories', { auth: true }),
  coupons: () => api<MemberCoupon[]>('/api/v1/members/me/coupons', { auth: true }),
  issueCoupon: (id: number) => api<MemberCoupon>(`/api/v1/coupons/${id}/issue`, { method: 'POST', auth: true }),
  event: (id: number) => api<{ eventId: number; memberId: number; remainingCount: number }>(`/api/v1/events/${id}/participate`, { method: 'POST', auth: true }),
  categories: () => api<Category[]>('/api/v1/categories').catch(() => []),
  category: (id: number) => api<{ children: { categoryId?: number; categoryName?: string }[] }>(`/api/v1/categories/${id}`),
  categoryCreate: (name: string) => api('/api/v1/categories', { method: 'POST', body: { categoryName: name }, auth: true }),
  categoryChild: (id: number, name: string) => api(`/api/v1/categories/${id}/children`, { method: 'POST', body: { categoryName: name }, auth: true }),
  categoryUpdate: (id: number, name: string) => api(`/api/v1/categories/${id}`, { method: 'PUT', body: { categoryName: name }, auth: true }),
  categoryDelete: (id: number) => api(`/api/v1/categories/${id}`, { method: 'DELETE', auth: true }),
  stock: (id: number) => api<{ productName: string; stocks: number }>(`/api/v1/products/${id}/stock`),
  histories: (id: number) => api<{ historyId: number; type: string; changedQty: number; currentStocks: number; reason?: string; createdAt: string }[]>(`/api/v1/products/${id}/stock-histories`),
  stockIn: (id: number, quantity: number, reason: string) => api(`/api/v1/products/${id}/stock-in`, { method: 'POST', body: { quantity, reason }, auth: true }),
  productCreate: (request: unknown, image: File) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    form.append('productImage', image);
    return api<ProductDetail>('/api/v1/products', { method: 'POST', form, auth: true });
  },
  productUpdate: (id: number, request: unknown, image?: File) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    if (image) form.append('productImage', image);
    return api(`/api/v1/products/${id}`, { method: 'PUT', form, auth: true });
  },
  productDelete: (id: number) => api(`/api/v1/products/${id}`, { method: 'DELETE', auth: true }),
  imageAdd: (id: number, image: File) => { const form = new FormData(); form.append('image', image); return api(`/api/v1/products/${id}/images`, { method: 'POST', form, auth: true }); },
  imageUpdate: (pid: number, iid: number, image: File) => { const form = new FormData(); form.append('image', image); return api(`/api/v1/products/${pid}/images/${iid}`, { method: 'PUT', form, auth: true }); },
  imageDelete: (pid: number, iid: number) => api(`/api/v1/products/${pid}/images/${iid}`, { method: 'DELETE', auth: true }),
  couponCreate: (body: unknown) => api('/api/v1/admin/coupons', { method: 'POST', body, auth: true })
};
