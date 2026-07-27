# Frontend API Console

SoldOut 백엔드 API를 브라우저에서 빠르게 확인하기 위한 React/Vite 기반 콘솔입니다.

## 실행 방식

백엔드 서버를 먼저 `localhost:8080`에서 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

프론트엔드는 로컬 Node.js로 실행할 수 있습니다.

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:5173`으로 접속합니다.

## Docker 실행

DB, Redis와 같은 Docker 네트워크를 공유하면서 프론트엔드만 추가로 띄울 수 있습니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.frontend.yml up frontend
```

Docker Desktop 환경에서는 기본적으로 백엔드 프록시 대상이 `http://host.docker.internal:8080`으로 설정됩니다.
필요하면 `.env`에 다음 값을 지정합니다.

```env
FRONTEND_PORT=5173
VITE_API_PROXY_TARGET=http://host.docker.internal:8080
```

## Kakao OAuth 테스트

프론트에서 콜백까지 자동으로 처리하려면 카카오 Redirect URI와 백엔드 환경 변수를 아래 값으로 맞춥니다.

```env
KAKAO_REDIRECT_URI=http://localhost:5173/oauth/kakao/callback
```

설정 후 백엔드 서버를 재시작합니다.

흐름은 다음과 같습니다.

1. 프론트에서 `카카오 로그인 URL 열기`를 누릅니다.
2. 백엔드가 `state`를 발급하고 `oauth_nonce` HttpOnly 쿠키를 내려줍니다.
3. 카카오 로그인 후 프론트 콜백 페이지로 돌아옵니다.
4. 프론트가 `code`, `state`, 쿠키를 함께 사용해 백엔드 로그인 API를 호출합니다.
5. 응답으로 받은 자체 JWT를 브라우저 로컬 스토리지에 저장합니다.

백엔드 콜백 URI를 그대로 쓰는 경우에는 콜백 응답의 JWT를 프론트 화면의 `JWT 직접 입력` 칸에 붙여 넣어도 됩니다.

## 포함된 기능

- 카카오 OAuth 인가 URL 조회 및 로그인 완료
- JWT 저장 및 내 회원 정보 조회
- 상품 목록, 상품 상세, 재고 조회
- 상품 입고, 상품 구매, 주문 조회
- 토큰을 포함한 직접 API 호출
- 요청 상태 코드와 응답 JSON 로그 확인

## 마이페이지 (`/my`)

| 카드 | 내용 |
|---|---|
| 내 정보 | 프로필 조회 · 이메일/닉네임 수정 |
| 장바구니 | 담기 · 수량변경 · 삭제 · 비우기 · 주문(checkout), 담긴 항목 렌더링 |
| **주문 내역** | `GET /api/v1/members/me/orders` — 최신순 주문 목록 |
| 쿠폰 | 발급 · 내 쿠폰 목록 |
| 포인트 | 잔액 · 이력 조회 |

### 주문 내역 카드

- 「조회」를 누르면 주문을 **최신순**으로 렌더링한다. 주문이 없으면 빈 상태 문구를 보여준다.
- 주문마다 **상태 배지**를 색으로 구분한다 — 구매 완료(초록) / 결제 대기(노랑) / 취소됨·결제 실패(빨강).
  모르는 상태 코드가 오면 코드를 그대로 노출해 조용히 숨기지 않는다.
- 주문 상품별로 상품명·수량·단가·합계를, 하단에 주문 금액을 표시한다. 취소된 주문은 취소 시각도 보여준다.
- **장바구니 주문(checkout) 직후 자동으로 갱신**되어, 방금 만든 주문이 바로 목록에 나타난다.
- 응답의 날짜는 타임존 없는 `LocalDateTime` 이라 브라우저 로컬 형식으로 표시한다. 파싱에 실패하면
  원본 문자열을 그대로 보여준다.
