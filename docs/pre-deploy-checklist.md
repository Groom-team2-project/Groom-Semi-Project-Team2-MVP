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

## 6. 참고: E 파트 남은 통합 지점 (배포와 별개)

- 쿠폰/포인트를 실제 주문·결제에 연결(`CouponService.useCoupon`, `PointService.use` 진입점만 존재) — 파트 C/D 와 인터페이스 합의 후 연결.
- 주문 내역 `GET /members/me/orders` — `Order` 에 회원 연결이 생겼으므로 구현 가능(추후).
