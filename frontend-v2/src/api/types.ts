// 백엔드 DTO와 1:1로 맞춘 타입 정의 (org.example.groommvp.domain.*.dto 기준)

export type CommonResponse<T> = {
  success: boolean;
  data: T | null;
  errorCode: string | null;
  message: string | null;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages?: number;
};

// ---------- auth ----------
export type KakaoAuthorizeUrlResponse = { url: string; state: string };

export type LoginResponse = {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
  memberId: number;
  newMember: boolean;
};

export type TokenReissueResponse = {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
};

// ---------- member ----------
export type MemberMeResponse = {
  memberId: number;
  provider: string;
  email: string | null;
  nickname: string | null;
};

// ---------- product / category ----------
export type ProductListItem = {
  productId: number;
  productName: string;
  productPrice: number;
  stocks: number;
  viewCount: number;
  reservedStocks: number;
  availableStocks: number;
  viewCount: number;
};
// 정렬
export type ProductSortType = 'LATEST' | 'POPULAR' | 'VIEW_COUNT' | 'PRICE_ASC' | 'PRICE_DESC';

export type ProductDetail = {
  productName: string;
  productPrice: number;
  productImage: string;
  stocks: number;
  reservedStocks: number;
  availableStocks: number;
  category: number | null;
  detailImages: ImageResponse[];
};

export type CategoryResponse = {
  categoryId: number;
  categoryName: string;
  parentCategory: number | null;
};

// GET /categories/{id} — 대분류면 children이 중분류 목록, 중분류면 상품 목록
export type CategoryDetailResponse = {
  categoryId: number;
  categoryName: string;
  parentCategory: number | null;
  children: { categoryId?: number; categoryName?: string }[];
};

// ---------- cart ----------
export type CartItemResponse = {
  cartItemId: number;
  productId: number;
  productName: string;
  productPrice: number;
  quantity: number;
  lineTotal: number;
};

export type CartResponse = {
  cartId: number | null;
  memberId: number;
  items: CartItemResponse[];
  totalQuantity: number;
  totalPrice: number;
};

export type CartCheckoutResponse = {
  orderId: number;
  items: { productId: number; quantity: number; orderPrice: number; remainingStocks: number }[];
  totalPrice: number;
  orderedAt: string;
};

// ---------- order ----------
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAYMENT_PROCESSING'   // 토스 승인 결과를 기다리는 중 — 결제·취소 모두 불가
  | 'COMPLETED'
  | 'CANCELED'
  | 'PAYMENT_FAILED';

export type OrderItemResponse = {
  orderItemId: number;
  productId: number;
  productName: string;
  quantity: number;
  orderPrice: number;
  itemTotalPrice: number;
};

export type OrderResponse = {
  orderId: number;
  status: OrderStatus;
  totalPrice: number;
  canceledAt: string | null;
  /** 이 시각까지 결제하지 않으면 예약 재고가 해제되고 주문이 취소된다 */
  paymentExpiresAt: string | null;
  createdAt: string;
  orderItems: OrderItemResponse[];
};

export type PurchaseResponse = {
  orderId: number;
  memberId: number | null;
  productId: number;
  purchasedQuantity: number;
  remainingStockQuantity: number;
  orderedAt: string;
};

// ---------- payment ----------
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELED' | 'REFUNDED';

export type PaymentResponse = {
  paymentId: number;
  orderId: number;
  amount: number;
  status: PaymentStatus;
  paidAt: string | null;
};

export type RefundResponse = {
  paymentId: number;
  orderId: number;
  amount: number;
  status: PaymentStatus;
  canceledAt: string | null;
};

// ---------- review ----------
export type ReviewResponse = {
  reviewId: number;
  productId: number;
  memberId: number;
  content: string;
  rating: number;
  createdAt: string;
  updatedAt: string;
};

// ---------- stock ----------
export type StockResponse = {
  productId: number;
  productName: string;
  stocks: number;
  updatedAt: string;
};

export type StockHistoryResponse = {
  historyId: number;
  stockId: number;
  productId: number;
  productName: string;
  orderId: number | null;
  type: 'INBOUND' | 'DECREASE' | 'RESTORE' | 'RESERVE' | 'CONFIRM' | 'RELEASE';
  changedQty: number;
  currentStocks: number;
  reason: string | null;
  createdAt: string;
};

// ---------- image ----------
export type ImageResponse = {
  productId: number;
  imageId: number;
  detailImage: string;
};
