# 카테고리 API 명세서

## 1. 개요

- Base URL: `/api/v1/categories`
- Content-Type: `application/json`
- 카테고리는 **대분류와 중분류의 2단계 구조**만 지원한다.
- 상품은 반드시 하나의 **중분류**에만 속한다.
- 상품을 대분류에 직접 연결할 수 없다.
- 대분류는 소속된 중분류가 없을 때만 삭제할 수 있다.
- 모든 성공 및 실패 응답은 프로젝트의 공통 응답 형식을 사용한다. 단, 삭제 성공 응답은 body가 없다.

## 2. 도메인 규칙

### 2.1 카테고리 단계

| 구분 | `depth` | 부모 카테고리 | 상품 연결 |
|---|---:|---|---|
| 대분류 | `1` | 없음 | 불가능 |
| 중분류 | `2` | 대분류 1개 필수 | 가능 |

다음 구조만 유효하다.

```text
대분류
 ├─ 중분류
 │   ├─ 상품
 │   └─ 상품
 └─ 중분류
     └─ 상품
```

다음 요청은 허용하지 않는다.

- 부모 카테고리를 지정한 대분류 생성
- 부모 카테고리가 없는 중분류 생성
- 중분류를 부모로 지정한 카테고리 생성
- 3단계 이상의 카테고리 생성
- 상품에 대분류 ID 지정
- 하나의 상품에 여러 중분류 지정

### 2.2 이름 정책

- 카테고리 이름은 필수이며, 앞뒤 공백을 제거한 후 1~50자여야 한다.
- 동일한 부모 아래에서는 같은 이름의 카테고리를 중복 생성할 수 없다.
- 서로 다른 대분류 아래의 중분류는 같은 이름을 사용할 수 있다.
- 대분류 이름은 대분류 범위에서 중복될 수 없다.

### 2.3 삭제 정책

- 하위 중분류가 존재하는 대분류는 삭제할 수 없다.
- 하위 중분류가 없는 대분류는 삭제할 수 있다.
- 데이터 무결성을 위해 상품이 연결된 중분류는 삭제할 수 없는 것으로 정의한다.
- 삭제는 프로젝트의 `BaseEntity` 정책에 맞춰 소프트 삭제한다.
- 삭제된 카테고리는 조회 결과와 상품 생성·수정 대상에서 제외한다.

## 3. 공통 응답

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": "요청 처리 성공 메시지"
}
```

### 실패 응답

```json
{
  "success": false,
  "data": null,
  "errorCode": "CATEGORY_NOT_FOUND",
  "message": "카테고리를 찾을 수 없습니다."
}
```

## 4. API 요약

| 기능 | Method | Endpoint |
|---|---|---|
| 대분류 생성 | `POST` | `/api/v1/categories` |
| 중분류 생성 | `POST` | `/api/v1/categories/{parentCategoryId}/children` |
| 전체 카테고리 트리 조회 | `GET` | `/api/v1/categories` |
| 카테고리 단건 조회 | `GET` | `/api/v1/categories/{categoryId}` |
| 카테고리 이름 수정 | `PATCH` | `/api/v1/categories/{categoryId}` |
| 카테고리 삭제 | `DELETE` | `/api/v1/categories/{categoryId}` |

## 5. 대분류 생성

새로운 대분류를 생성한다.

```http
POST /api/v1/categories
```

### 요청

```json
{
  "categoryName": "전자제품"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---:|---|
| `categoryName` | String | O | 공백 제외 1~50자, 대분류 이름 중복 불가 |

### 성공 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "categoryId": 1,
    "categoryName": "전자제품",
    "depth": 1,
    "parentCategoryId": null
  },
  "errorCode": null,
  "message": "대분류 등록 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 길이 조건을 만족하지 않음 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 같은 이름의 대분류가 이미 존재함 |

## 6. 중분류 생성

지정한 대분류 아래에 중분류를 생성한다.

```http
POST /api/v1/categories/{parentCategoryId}/children
```

### Path parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `parentCategoryId` | Long | O | 부모가 될 대분류 ID |

### 요청

```json
{
  "categoryName": "노트북"
}
```

### 성공 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북",
    "depth": 2,
    "parentCategoryId": 1
  },
  "errorCode": null,
  "message": "중분류 등록 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 길이 조건을 만족하지 않음 |
| 400 | `CATEGORY_DEPTH_EXCEEDED` | 중분류를 부모로 지정하여 3단계 생성을 시도함 |
| 404 | `CATEGORY_NOT_FOUND` | 부모 카테고리가 존재하지 않거나 삭제됨 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 같은 대분류 아래에 같은 이름의 중분류가 존재함 |

## 7. 전체 카테고리 트리 조회

대분류와 그 아래의 중분류를 계층형으로 조회한다. 삭제된 카테고리는 제외한다.

```http
GET /api/v1/categories
```

### 성공 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "categoryId": 1,
      "categoryName": "전자제품",
      "depth": 1,
      "children": [
        {
          "categoryId": 2,
          "categoryName": "노트북",
          "depth": 2
        },
        {
          "categoryId": 3,
          "categoryName": "스마트폰",
          "depth": 2
        }
      ]
    },
    {
      "categoryId": 4,
      "categoryName": "생활가전",
      "depth": 1,
      "children": []
    }
  ],
  "errorCode": null,
  "message": "카테고리 목록 조회 성공"
}
```

카테고리가 하나도 없으면 `data`에 빈 배열을 반환한다.

## 8. 카테고리 단건 조회

카테고리의 기본 정보와 상·하위 관계를 조회한다.

```http
GET /api/v1/categories/{categoryId}
```

### 대분류 조회 성공 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "categoryId": 1,
    "categoryName": "전자제품",
    "depth": 1,
    "parent": null,
    "children": [
      {
        "categoryId": 2,
        "categoryName": "노트북"
      }
    ]
  },
  "errorCode": null,
  "message": "카테고리 조회 성공"
}
```

### 중분류 조회 성공 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북",
    "depth": 2,
    "parent": {
      "categoryId": 1,
      "categoryName": "전자제품"
    },
    "children": []
  },
  "errorCode": null,
  "message": "카테고리 조회 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않거나 삭제됨 |

## 9. 카테고리 이름 수정

대분류 또는 중분류의 이름을 수정한다. 카테고리의 단계나 부모 카테고리는 변경할 수 없다.

```http
PATCH /api/v1/categories/{categoryId}
```

### 요청

```json
{
  "categoryName": "노트북·태블릿"
}
```

### 성공 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북·태블릿",
    "depth": 2,
    "parentCategoryId": 1
  },
  "errorCode": null,
  "message": "카테고리 수정 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 길이 조건을 만족하지 않음 |
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않거나 삭제됨 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 동일 계층에 같은 이름의 카테고리가 존재함 |

## 10. 카테고리 삭제

대분류 또는 중분류를 삭제한다. 연관 데이터가 있는 카테고리는 삭제할 수 없다.

```http
DELETE /api/v1/categories/{categoryId}
```

### 성공 응답

Status: `204 No Content`

응답 body는 없다.

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않거나 삭제됨 |
| 409 | `CATEGORY_HAS_CHILDREN` | 대분류에 하나 이상의 중분류가 존재함 |
| 409 | `CATEGORY_HAS_PRODUCTS` | 중분류에 하나 이상의 상품이 연결되어 있음 |

## 11. 상품 API 연동 규칙

상품 생성 및 수정 요청은 중분류 ID를 `categoryId`로 전달한다.

```json
{
  "productName": "MacBook Pro",
  "productPrice": 2500000,
  "stocks": 10,
  "categoryId": 2,
  "imageUrls": [
    "https://example.com/products/macbook-pro-1.jpg"
  ]
}
```

| 검증 | 실패 Status | `errorCode` |
|---|---:|---|
| `categoryId`가 없음 | 400 | `INVALID_INPUT_VALUE` |
| 카테고리가 존재하지 않거나 삭제됨 | 404 | `CATEGORY_NOT_FOUND` |
| 대분류를 상품 카테고리로 지정함 | 400 | `PRODUCT_CATEGORY_MUST_BE_MIDDLE` |

상품 응답에는 중분류와 그 부모인 대분류 정보를 함께 제공한다.

```json
{
  "category": {
    "largeCategoryId": 1,
    "largeCategoryName": "전자제품",
    "middleCategoryId": 2,
    "middleCategoryName": "노트북"
  }
}
```

카테고리별 상품 조회는 기존 상품 목록 API의 선택 필터로 제공한다.

```http
GET /api/v1/products?categoryId=2&page=0&size=10
```

- 중분류 ID를 전달하면 해당 중분류의 상품만 조회한다.
- 대분류 ID를 전달하면 소속된 모든 중분류의 상품을 조회한다.
- 존재하지 않거나 삭제된 카테고리 ID는 `404 CATEGORY_NOT_FOUND`를 반환한다.

## 12. 오류 코드 목록

아래 코드는 카테고리 기능 구현 시 `ErrorCode`에 추가한다.

| `errorCode` | HTTP Status | 기본 메시지 |
|---|---:|---|
| `CATEGORY_NOT_FOUND` | 404 | 카테고리를 찾을 수 없습니다. |
| `CATEGORY_NAME_DUPLICATED` | 409 | 같은 이름의 카테고리가 이미 존재합니다. |
| `CATEGORY_DEPTH_EXCEEDED` | 400 | 카테고리는 중분류까지만 생성할 수 있습니다. |
| `CATEGORY_HAS_CHILDREN` | 409 | 하위 카테고리가 있어 삭제할 수 없습니다. |
| `CATEGORY_HAS_PRODUCTS` | 409 | 연결된 상품이 있어 카테고리를 삭제할 수 없습니다. |
| `PRODUCT_CATEGORY_MUST_BE_MIDDLE` | 400 | 상품은 중분류 카테고리에만 등록할 수 있습니다. |

## 13. 구현 및 QA 체크리스트

- [ ] 대분류는 부모 없이 생성된다.
- [ ] 중분류는 존재하는 대분류 아래에서만 생성된다.
- [ ] 중분류 아래에 카테고리를 추가할 수 없다.
- [ ] 동일 부모 아래의 카테고리 이름 중복이 차단된다.
- [ ] 전체 조회 결과가 대분류-중분류 트리로 반환된다.
- [ ] 중분류가 존재하는 대분류 삭제가 차단된다.
- [ ] 상품이 존재하는 중분류 삭제가 차단된다.
- [ ] 상품 생성과 수정에는 중분류 ID가 반드시 필요하다.
- [ ] 상품에 대분류를 직접 지정할 수 없다.
- [ ] 삭제된 카테고리는 조회 및 상품 연결 대상에서 제외된다.
- [ ] 실제 HTTP status, 공통 응답 구조, Swagger 예시가 이 문서와 일치한다.

## 14. 구현 전 최종 확인 사항

현재 문서에서는 데이터 유실을 방지하기 위해 **상품이 연결된 중분류는 삭제할 수 없음**으로 정의했다. 중분류 삭제 시 상품까지 함께 삭제하거나 다른 중분류로 이동시키는 정책을 원한다면 구현 전에 별도로 변경해야 한다.
