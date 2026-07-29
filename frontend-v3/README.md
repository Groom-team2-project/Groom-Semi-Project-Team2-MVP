# SOLDOUT Frontend V3

기존 `frontend`, `frontend-v2`와 분리된 세 번째 프론트엔드입니다.

## 실행

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

다른 터미널에서:

```bash
cd frontend-v3
npm install
cp .env.example .env
npm run dev
```

- 프론트엔드: `http://localhost:5173`
- API 프록시 기본값: `http://localhost:8080`
- 토스 결제를 사용하려면 `.env`에 `VITE_TOSS_CLIENT_KEY`를 설정합니다.

## 상품·카테고리 관리자 기능 테스트

상품 및 카테고리 변경 API는 `ADMIN` 권한이 필수입니다. 카카오 신규 회원은 기본적으로
`USER`이므로 로그인 후 로컬 DB에서 테스트 계정을 한 번 승격하고 다시 로그인해야 합니다.

```sql
UPDATE members SET role = 'ADMIN' WHERE member_id = 본인_회원_ID;
```

재로그인 후 `Admin Studio` 상단에서 `BACKEND: CONNECTED`,
`ACCESS ROLE: ADMIN`이 표시되는지 확인합니다. 필요하면 발급받은 ADMIN access token을
상단 토큰 입력란에 붙여넣어 바로 테스트할 수도 있습니다.

상품 생성 순서:

1. `CATEGORIES`에서 대분류 생성
2. 대분류의 `+ CHILD`로 중분류 생성
3. `PRODUCTS`에서 해당 중분류와 대표 이미지를 선택해 상품 등록

## 포함 기능

- 카카오 로그인, 토큰 재발급, 로그아웃
- 상품 목록/검색/정렬/상세
- 장바구니 추가/수정/삭제/비우기/체크아웃
- 바로 구매, 주문 조회/취소, 토스 결제 승인, 환불
- 리뷰 등록/수정/삭제
- 내 정보 수정, 주문/쿠폰/포인트 조회
- 쿠폰 발급, 선착순 이벤트 참여
- 관리자 상품/이미지/카테고리/재고/쿠폰 관리
