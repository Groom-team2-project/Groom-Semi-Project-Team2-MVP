# 배포 전 정리 체크리스트 (E 파트 · 회원/장바구니/쿠폰/포인트)

로컬 개발·테스트 편의를 위해 넣은 코드 중 **운영 배포 전 반드시 제거/교체**해야 하는 항목을 모아둔다.

## 1. 프론트엔드 dev 토큰 생성기 (필수 제거)

카카오 로그인 없이 로컬에서 JWT 를 만드는 기능. **`JWT_SECRET` 만 알면 임의 회원/권한(ADMIN 포함) 토큰을 위조**할 수 있으므로 운영에 남으면 심각한 보안 취약점이다.

- 위치: `frontend/src/App.tsx` — 검색 태그 **`DEV-ONLY`** 로 전부 찾을 수 있다.
  ```
  grep -rn "DEV-ONLY" frontend/src
  ```
- 제거 대상: `base64UrlFromBytes`, `genDevToken` 함수 / `devMemberId`·`devRole`·`devSecret` 상태 / 세 곳의 "🔧 테스트용 토큰 발급" UI 카드.
- 제거 후에는 **카카오 실제 로그인**(`/api/v1/auth/kakao/authorize-url` → `/login`)으로만 토큰을 얻도록 남긴다.

## 2. JWT_SECRET (필수 교체)

- 현재 로컬 기본값: `local-dev-jwt-secret-key-change-before-deploy` (`.env.example`, `application-local.yaml`).
- 운영은 환경변수/시크릿 매니저로 **충분히 긴 랜덤 값**을 주입한다. 평문 커밋 금지(공통 규약 §6).

## 3. admin 페이지 접근 제어 (확인)

- 프론트 `/admin` 은 JWT role 을 디코드해 ADMIN 만 진입시키는 게이트가 있다(UX 용).
- **실제 방어선은 백엔드** `SecurityConfig` 의 `requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` 이다. 프론트 게이트는 우회 가능하므로, 신규 admin API 를 추가할 때 **백엔드 인가를 반드시 함께 건다.**

## 4. 인프라 · 보안

- **DB/Redis 포트 노출 금지**: `docker-compose.yml` 은 `127.0.0.1:3306` / `127.0.0.1:6379` 로 loopback 바인딩되어 있다. 외부(0.0.0.0)로 열지 말 것. (약한 비밀번호 + 노출 시 랜섬 봇 침입 사례 있었음)
- **MYSQL_ROOT_PASSWORD** 등 자격증명도 운영은 강한 값으로 교체.

## 5. 테스트 데이터 시드 (운영 반영 금지)

로컬 테스트용으로 DB 에 직접 INSERT 한 데이터(테스트 회원, 상품, 쿠폰 등)는 운영 DB 에 넣지 않는다. `ddl-auto` 도 운영은 `validate`/`none` 권장(현재 로컬은 `update`).

## 6. 유니크 제약 실재 확인 (필수)

E 파트의 여러 방어 로직은 **유니크 제약을 최종 방어선**으로 삼는다. 애플리케이션 검증을 동시 요청이 통과하더라도 DB 가 막아주는 구조다.

| 테이블 | 제약 | 없으면 벌어지는 일 |
|---|---|---|
| `carts` | `member_id` unique | 한 회원에게 장바구니가 여러 개 생김 |
| `member_coupons` | `(member_id, coupon_id)` | 같은 쿠폰 중복 발급 |
| `point_balances` | `member_id` unique | 잔액 행이 갈라져 포인트가 어긋남 |
| `point_histories` | `(member_id, order_id, type)` | 결제 재시도/취소 재전송 시 포인트 이중 반영 |

**문제**: 현재 `ddl-auto: update` 는 **이미 데이터가 있는 테이블에 유니크 제약을 조용히 추가하지 못할 수 있다.** (중복 행이 있으면 특히) 실패해도 애플리케이션은 정상 기동하므로 최종 방어선이 사라진 걸 눈치채지 못한다.

배포 대상 DB 에서 아래를 실행해 4개가 모두 나오는지 확인한다.

```sql
SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLS
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND NON_UNIQUE = 0
  AND TABLE_NAME IN ('carts', 'member_coupons', 'point_balances', 'point_histories')
  AND INDEX_NAME <> 'PRIMARY'
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME;
```

빠진 제약이 있으면 중복 행을 먼저 정리한 뒤 수동으로 추가한다.

```sql
ALTER TABLE point_histories
  ADD CONSTRAINT UK_POINT_HISTORIES_MEMBER_ORDER_TYPE UNIQUE (member_id, order_id, type);
```

> 코드 레벨에서는 `UniqueConstraintTest` 가 실제로 중복 INSERT 를 시도해 제약 존재를 검증한다.
> H2·MySQL 양쪽에서 통과하는 것을 확인했다. 다만 이 테스트는 `create-drop` 으로 새로 만든
> 스키마를 보므로, **운영 DB 에 제약이 붙어 있는지는 위 SQL 로 별도 확인해야 한다.**

## 7. 예약 재고 회수 스케줄러 (신규)

미결제 주문이 재고를 영구 점유하지 않도록 `ReservationExpiryScheduler` 가 주기적으로 예약을 회수한다.

| 설정 | 환경변수 | 기본값 |
|---|---|---|
| 활성화 | `CART_RESERVATION_EXPIRY_ENABLED` | `true` |
| 회수 기준 시간 | `CART_RESERVATION_EXPIRY_TIMEOUT` | `PT30M` (30분) |
| 실행 주기 | `CART_RESERVATION_EXPIRY_INTERVAL` | `PT1M` |
| 주기당 최대 건수 | `CART_RESERVATION_EXPIRY_BATCH_SIZE` | `100` |

- **타임아웃은 결제 제한시간보다 넉넉해야 한다.** 짧으면 결제 중인 주문의 재고를 뺏는다. (주문 행 락 + 상태 재확인으로 이중 처리는 막히지만, 결제 직전 취소되는 UX 문제가 생긴다)
- **다중 인스턴스로 배포하면 한 대만 켠다.** 여러 대가 같은 주문을 집으면 불필요한 락 경합이 생긴다. (정합성은 깨지지 않는다)

## 8. 참고: E 파트 남은 통합 지점 (배포와 별개)

- **쿠폰/포인트를 실제 주문·결제에 연결** — `CouponService.useCoupon`, `PointService.use` 는 진입점만 있고 호출자가 없다. 파트 C/D 와 **할인/포인트를 어느 시점에 적용할지** 합의가 먼저다. 자세한 선택지는 `member-cart-point-design.md` §6 참고.
- ~~주문 내역 `GET /members/me/orders`~~ — 구현 완료. 마이페이지에서 렌더링한다.
