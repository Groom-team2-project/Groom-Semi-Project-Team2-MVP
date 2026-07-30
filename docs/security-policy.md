# 인증/인가 정책

현재 프로젝트의 인증/인가 적용 범위를 정리한 문서입니다.

## 기본 원칙

애플리케이션 API는 기본적으로 인증이 필요합니다.

명시적으로 공개한 API만 토큰 없이 접근할 수 있고, 그 외 API는 로그인된 사용자 또는 ADMIN 권한이 필요합니다.

## 공개 API

| 구분 | API |
| --- | --- |
| Swagger | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` |
| 인증 | `/api/v1/auth/**` |
| 상품 조회 | `GET /api/v1/products`, `GET /api/v1/products/{productId}` |
| 재고 조회 | `GET /api/v1/products/{productId}/stock` |
| 카테고리 조회 | `GET /api/v1/categories/**` |
| 리뷰 조회 | `GET /api/v1/products/{productId}/reviews`, `GET /api/v1/reviews/{reviewId}` |

## 로그인 필요 API

| 구분 | API |
| --- | --- |
| 회원 | `/api/v1/members/me/**` |
| 장바구니 | `/api/v1/carts/**` |
| 주문/결제/취소 | `/api/v1/orders/**`, `POST /api/v1/products/{productId}/orders` |
| 쿠폰 발급 | `POST /api/v1/coupons/{couponId}/issue` |
| 선착순 이벤트 참여 | `POST /api/v1/events/{eventId}/participate` |
| 리뷰 작성/수정/삭제 | `POST /api/v1/reviews`, `PUT /api/v1/reviews/{reviewId}`, `DELETE /api/v1/reviews/{reviewId}` |
| 그 외 API | 공개 또는 ADMIN API로 명시되지 않은 모든 요청 |

## ADMIN 필요 API

| 구분 | API |
| --- | --- |
| 쿠폰 관리 | `/api/v1/admin/coupons/**` |
| 상품 관리 | `POST /api/v1/products`, `PUT /api/v1/products/{productId}`, `DELETE /api/v1/products/{productId}` |
| 상품 이미지 관리 | `POST /api/v1/products/{productId}/images`, `DELETE /api/v1/products/{productId}/images` |
| 재고 관리 | `POST /api/v1/products/{productId}/stock-in`, `GET /api/v1/products/{productId}/stock-histories` |
| 카테고리 관리 | 카테고리 생성/수정/삭제 API |

## 새 API 추가 규칙

새 API를 추가할 때는 아래 정책 중 하나를 명확히 선택해야 합니다.

1. 공개 API라면 `permitAll()`에 명시합니다.
2. 회원 API라면 기본 `authenticated()` 정책을 사용하거나 명시합니다.
3. 관리자 API라면 `hasRole("ADMIN")`에 명시합니다.

## 배포 전 체크리스트

- `frontend/src/App.tsx`의 `DEV-ONLY` JWT 발급 UI를 제거합니다.
- 운영 환경에서는 기본 `JWT_SECRET`을 사용하지 않고 외부 Secret으로 주입합니다.
- 로컬 외 환경에서는 `OAUTH_COOKIE_SECURE=true`를 유지합니다.
- Swagger의 Bearer 표시와 실제 `SecurityConfig` 정책이 일치하는지 확인합니다.
- 도메인 API가 추가되면 보안 테스트도 함께 갱신합니다.
