# 인증 / 인가 흐름 정리

## 목적

이 문서는 현재 프로젝트의 Kakao OAuth 로그인, 자체 JWT 발급, OAuth state 검증, 인증 실패 응답 처리 상태를 정리합니다.

장바구니, 주문, 상품 등 다른 도메인의 소유권 정책은 각 담당 파트와 협의가 필요하므로 이 문서에서는 인증 도메인에서 제공하는 공통 흐름만 다룹니다.

## 현재 인증 구성

현재 인증은 다음 구성으로 동작합니다.

| 구분 | 현재 방식 |
| --- | --- |
| 외부 로그인 | Kakao OAuth |
| 회원 식별 | Kakao provider id 기반 회원 조회 / 생성 |
| 서비스 인증 토큰 | 자체 JWT access token |
| JWT 전달 방식 | `Authorization: Bearer {accessToken}` |
| 세션 정책 | Stateless |
| OAuth state 저장소 | Redis |
| OAuth nonce 전달 방식 | `oauth_nonce` HttpOnly Cookie |

## Kakao OAuth 로그인 흐름

### 1. 인가 URL 요청

클라이언트는 먼저 다음 API를 호출합니다.

```http
GET /api/v1/auth/kakao/authorize-url
```

서버는 이 요청에서 두 값을 생성합니다.

| 값 | 역할 |
| --- | --- |
| `state` | Kakao 인가 URL에 포함되어 callback으로 돌아오는 요청 식별값 |
| `nonce` | 요청을 시작한 브라우저를 식별하기 위해 HttpOnly Cookie로 저장되는 값 |

Redis에는 다음 형태로 저장합니다.

```text
key   = oauth:kakao:state:{state}
value = nonce
ttl   = 5 minutes
```

응답 body에는 Kakao 인가 URL과 `state`만 포함됩니다.

```json
{
  "success": true,
  "data": {
    "url": "https://kauth.kakao.com/oauth/authorize?...&state={state}",
    "state": "{state}"
  },
  "errorCode": null,
  "message": "카카오 로그인 URL 조회 성공"
}
```

`nonce`는 body에 넣지 않고 `Set-Cookie` header로만 전달합니다.

```http
Set-Cookie: oauth_nonce={nonce}; Path=/; Max-Age=300; HttpOnly; SameSite=Lax
```

운영 환경에서는 `Secure` 속성도 포함되도록 설정합니다.

### 2. Kakao 로그인 페이지 이동

클라이언트는 응답 body의 `url`로 사용자를 이동시킵니다.

```text
https://kauth.kakao.com/oauth/authorize?...&state={state}
```

Kakao 로그인과 동의가 끝나면 Kakao가 등록된 redirect URI로 사용자를 돌려보냅니다.

```http
GET /api/v1/auth/kakao/callback?code={authorizationCode}&state={state}
```

브라우저는 같은 요청에 `oauth_nonce` 쿠키를 함께 전송합니다.

```http
Cookie: oauth_nonce={nonce}
```

### 3. Callback 검증

서버는 callback에서 다음 값을 함께 확인합니다.

| 값 | 출처 |
| --- | --- |
| `code` | Kakao callback query parameter |
| `state` | Kakao callback query parameter |
| `nonce` | `oauth_nonce` HttpOnly Cookie |

검증 순서는 다음과 같습니다.

1. `state`와 `nonce`가 비어 있지 않은지 확인합니다.
2. Redis에서 `oauth:kakao:state:{state}` 값을 조회하면서 삭제합니다.
3. Redis에 저장된 nonce와 쿠키 nonce가 같은지 비교합니다.
4. 일치하면 Kakao token 요청과 회원 조회 / 생성을 진행합니다.
5. 자체 JWT를 발급합니다.
6. 사용한 `oauth_nonce` 쿠키를 만료시킵니다.

Redis 값은 `getAndDelete()` 방식으로 소비하므로 같은 state는 재사용할 수 없습니다.

## state와 nonce를 함께 쓰는 이유

`state`만 Redis에 저장하고 존재 여부만 확인하면 다음 문제가 생길 수 있습니다.

```text
1. 공격자가 자기 브라우저에서 로그인 요청을 시작한다.
2. 공격자의 유효한 state와 code가 생성된다.
3. 공격자가 피해자 브라우저에서 callback URL을 열게 한다.
4. 서버가 state 존재만 확인하면 공격자의 로그인 요청이 피해자 브라우저에서 완료될 수 있다.
```

이를 막기 위해 현재 구조는 `state`를 브라우저 쿠키의 `nonce`와 연결합니다.

```text
Redis: state -> nonce
Browser Cookie: oauth_nonce=nonce
Callback: state + oauth_nonce 검증
```

공격자가 자신의 `state`와 `code`를 피해자에게 전달하더라도 피해자 브라우저에는 공격자의 `oauth_nonce` 쿠키가 없으므로 검증을 통과할 수 없습니다.

## `/kakao/login`과 `/kakao/callback` 계약

현재 두 로그인 완료 API는 같은 nonce 계약을 사용합니다.

| API | state 전달 | nonce 전달 |
| --- | --- | --- |
| `POST /api/v1/auth/kakao/login` | request body | `oauth_nonce` cookie |
| `GET /api/v1/auth/kakao/callback` | query parameter | `oauth_nonce` cookie |

`nonce`는 HttpOnly Cookie이므로 JavaScript에서 읽을 수 없습니다. 따라서 request body로 nonce를 받지 않고, 서버가 쿠키에서 직접 읽습니다.

## OAuth Cookie 설정

설정값은 다음과 같습니다.

```yaml
app:
  oauth:
    cookie-secure: ${OAUTH_COOKIE_SECURE:true}
```

운영 기본값은 `true`입니다.

로컬 개발 환경에서는 HTTP 테스트가 가능해야 하므로 `application-local.yaml`에서 기본값을 `false`로 둡니다.

```yaml
app:
  oauth:
    cookie-secure: ${OAUTH_COOKIE_SECURE:false}
```

| 환경 | `cookie-secure` | 이유 |
| --- | --- | --- |
| local | `false` | `http://localhost` 환경에서 쿠키 테스트 가능 |
| staging / production | `true` | HTTPS 요청에서만 쿠키 전송 |

## JWT 인증 흐름

로그인 성공 후 서버는 자체 JWT access token을 발급합니다.

클라이언트는 인증이 필요한 API에 다음 header를 포함합니다.

```http
Authorization: Bearer {accessToken}
```

JWT 필터는 다음 순서로 동작합니다.

1. `Authorization` header가 없거나 `Bearer ` 형식이 아니면 다음 필터로 넘깁니다.
2. Bearer token이 있으면 JWT 서명과 만료 시간을 검증합니다.
3. JWT payload에서 `memberId`, `role`, `provider`를 읽습니다.
4. `AuthMember`를 `AuthenticationPrincipal`로 등록합니다.
5. 컨트롤러는 `@AuthenticationPrincipal AuthMember`로 현재 회원 정보를 사용할 수 있습니다.

현재 인증이 필요한 대표 API는 다음과 같습니다.

```http
GET /api/v1/members/me
```

## 현재 테스트 범위

현재 인증 관련 테스트는 다음을 확인합니다.

| 테스트 | 확인 내용 |
| --- | --- |
| `OAuthStateServiceTest` | state / nonce 발급, Redis 저장, nonce 불일치, 만료, 재사용 실패 |
| `AuthServiceTest` | state / nonce 검증 실패 시 Kakao API 호출 차단 |
| `AuthControllerTest` | nonce 쿠키 발급, Secure / HttpOnly 설정, login / callback에서 쿠키 nonce 전달 |
| `JwtTokenProviderTest` | JWT 생성, claim 검증, 잘못된 token 처리 |
| `MemberControllerTest` | `/me` 정상 조회, token 없음 401, 잘못된 token 401 |

## Authentication Failure Response

Authentication failures now return the project common response shape as JSON.

| Case | Status | errorCode |
| --- | --- | --- |
| Protected API without token | 401 | `UNAUTHORIZED` |
| Invalid Bearer token | 401 | `INVALID_TOKEN` |
| Valid Bearer token | API success | - |

Example response when a protected API is called without a token:

```json
{
  "success": false,
  "data": null,
  "errorCode": "UNAUTHORIZED",
  "message": "인증이 필요합니다."
}
```

Example response when an invalid token is provided:

```json
{
  "success": false,
  "data": null,
  "errorCode": "INVALID_TOKEN",
  "message": "유효하지 않은 토큰입니다."
}
```

Implementation points:

1. `SecurityConfig` uses the authentication entry point for missing authentication.
2. `JwtAuthenticationFilter` handles invalid Bearer tokens before the request reaches a controller.
3. Both paths write `CommonResponse.error(...)` through `SecurityErrorResponseWriter`.
4. `MemberControllerTest` verifies 401 status, `success`, `errorCode`, and `message`.
5. `JwtAuthenticationFilterTest` verifies invalid-token JSON body at filter level.

Remaining optional refinement:

- Expired tokens currently use the same `INVALID_TOKEN` response path. If the team wants to expose a separate error code, add `EXPIRED_TOKEN` and distinguish token expiration in `JwtTokenProvider` or the filter.

## Swagger 테스트 주의점

OAuth nonce는 HttpOnly Cookie로 전달되므로 Swagger에서 nonce 값을 직접 읽어 request body에 넣는 방식은 맞지 않습니다.

현재 흐름에서는 다음 방식으로 확인합니다.

1. `GET /api/v1/auth/kakao/authorize-url` 호출
2. 응답 header의 `Set-Cookie: oauth_nonce=...` 확인
3. 응답 body의 Kakao URL로 브라우저 이동
4. callback 요청에서 브라우저가 쿠키를 함께 전송하는지 확인

Swagger는 단일 API 요청 확인에는 유용하지만, OAuth redirect와 cookie가 연결되는 브라우저 흐름 전체를 검증하기에는 제한이 있습니다.

