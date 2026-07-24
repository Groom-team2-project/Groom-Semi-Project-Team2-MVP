# 카테고리 API 명세서

## 1. 개요

- Base URL: `/api/v1/categories`
- Content-Type: `application/json`
- 카테고리는 대분류와 중분류의 2단계 구조로 관리한다.
- 별도의 `depth` 값은 사용하지 않는다.
- 엔티티의 `parentCategory`가 `null`이면 대분류, 값이 있으면 중분류다.
- API 응답에서는 부모 카테고리 객체가 아닌 부모 카테고리 ID를 `parentCategory` 필드로 반환한다.
- 카테고리 상세 조회 시 대분류의 `children`에는 중분류가, 중분류의 `children`에는 상품이 포함된다.
- 모든 응답은 프로젝트의 `CommonResponse` 형식을 사용한다.

## 2. 데이터 구조

### 엔티티 관계

| 필드 | 타입 | 설명 |
|---|---|---|
| `categoryId` | Long | 대분류와 중분류 각각에 부여되는 카테고리 고유 ID |
| `categoryName` | String | 카테고리 이름, 최대 50자 |
| `parentCategory` | CategoryEntity | 중분류가 속한 대분류. 대분류는 `null` |

DB의 `parent_category` 컬럼에는 부모 카테고리의 `category_id`가 외래 키로 저장된다.

```text
전자제품(categoryId=1, parentCategory=null)
 ├─ 노트북(categoryId=2, parentCategory=1)
 └─ 스마트폰(categoryId=3, parentCategory=1)
```

### 단계 판별

| 구분 | 엔티티의 `parentCategory` | API 응답의 `parentCategory` |
|---|---|---|
| 대분류 | `null` | `null` |
| 중분류 | 부모 대분류 엔티티 | 부모 대분류 ID |

중분류를 부모로 지정할 수 없으므로 3단계 이상의 카테고리는 생성할 수 없다.

## 3. 입력 및 이름 정책

- `categoryName`은 필수다.
- 공백만 입력할 수 없다.
- 최대 50자까지 입력할 수 있다.
- Service에서 앞뒤 공백을 제거한 이름을 저장한다.
- 현재 구현은 카테고리 단계나 부모와 관계없이 전체 카테고리에서 이름 중복을 허용하지 않는다.

예를 들어 `전자제품`이라는 대분류가 있으면 다른 대분류나 중분류에 `전자제품`이라는 이름을 다시 사용할 수 없다.

## 4. 공통 응답

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

## 5. API 요약

| 기능 | Method | Endpoint |
|---|---|---|
| 대분류 생성 | `POST` | `/api/v1/categories` |
| 중분류 생성 | `POST` | `/api/v1/categories/{parentId}/children` |
| 대분류 목록 조회 | `GET` | `/api/v1/categories` |
| 카테고리 상세 조회 | `GET` | `/api/v1/categories/{categoryId}` |
| 카테고리 이름 수정 | `PUT` | `/api/v1/categories/{categoryId}` |
| 카테고리 삭제 | `DELETE` | `/api/v1/categories/{categoryId}` |

## 6. 대분류 생성

```http
POST /api/v1/categories
Content-Type: application/json
```

### 요청

```json
{
  "categoryName": "전자제품"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---:|---|
| `categoryName` | String | O | 공백 제외, 최대 50자, 전체 카테고리 이름 중복 불가 |

### 성공 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "categoryId": 1,
    "categoryName": "전자제품",
    "parentCategory": null
  },
  "errorCode": null,
  "message": "카테고리가 생성되었습니다."
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 공백 또는 50자를 초과함 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 동일한 이름의 카테고리가 이미 존재함 |

## 7. 중분류 생성

```http
POST /api/v1/categories/{parentId}/children
Content-Type: application/json
```

### Path parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `parentId` | Long | O | 부모가 될 대분류 ID |

### 요청

```json
{
  "categoryName": "노트북"
}
```

부모 ID는 요청 본문이 아니라 URL의 `parentId`로 전달한다.

### 성공 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북",
    "parentCategory": 1
  },
  "errorCode": null,
  "message": "카테고리가 생성되었습니다."
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 공백 또는 50자를 초과함 |
| 404 | `CATEGORY_NOT_FOUND` | URL로 전달한 부모 카테고리가 존재하지 않음 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 동일한 이름의 카테고리가 이미 존재함 |
| 409 | `PARENT_CATEGORY_MISSING` | 부모 카테고리 ID가 없음 |
| 409 | `INVALID_PARENT_CATEGORY` | 중분류를 부모로 지정함 |

## 8. 대분류 목록 조회

부모가 없는 대분류만 ID 오름차순으로 조회한다. 이 API는 중분류를 `children`으로 묶어 반환하지 않는다.

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
      "parentCategory": null
    },
    {
      "categoryId": 4,
      "categoryName": "생활가전",
      "parentCategory": null
    }
  ],
  "errorCode": null,
  "message": "카테고리 조회 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 404 | `CATEGORY_NOT_FOUND` | 등록된 대분류가 하나도 없음 |

## 9. 카테고리 상세 조회

카테고리 종류에 따라 `children`의 데이터 형태가 달라진다.

```http
GET /api/v1/categories/{categoryId}
```

### Path parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `categoryId` | Long | O | 조회할 대분류 또는 중분류 ID |

### 대분류 조회 성공 응답

대분류를 조회하면 `children`에 소속된 중분류의 ID와 이름이 포함된다.

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "categoryId": 1,
    "categoryName": "전자제품",
    "parentCategory": null,
    "children": [
      {
        "categoryId": 2,
        "categoryName": "노트북"
      },
      {
        "categoryId": 3,
        "categoryName": "스마트폰"
      }
    ]
  },
  "errorCode": null,
  "message": "카테고리 목록 조회 성공"
}
```

### 중분류 조회 성공 응답

중분류를 조회하면 `children`에 연결된 삭제되지 않은 상품의 ID와 이름이 포함된다.

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북",
    "parentCategory": 1,
    "children": [
      {
        "productId": 10,
        "productName": "MacBook Pro"
      }
    ]
  },
  "errorCode": null,
  "message": "카테고리 목록 조회 성공"
}
```

대분류에 중분류가 없거나 중분류에 상품이 없으면 `children`은 빈 배열이다.

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않음 |

## 10. 카테고리 이름 수정

대분류 또는 중분류의 이름만 수정한다. 부모 카테고리는 변경할 수 없다.

```http
PUT /api/v1/categories/{categoryId}
Content-Type: application/json
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
    "parentCategory": 1
  },
  "errorCode": null,
  "message": "카테고리 수정 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT_VALUE` | 이름이 없거나 공백 또는 50자를 초과함 |
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않음 |
| 409 | `CATEGORY_NAME_DUPLICATED` | 동일한 이름의 카테고리가 이미 존재함 |

현재 구현에서는 기존 이름과 동일한 이름으로 수정하는 경우에도 중복으로 판단한다.

## 11. 카테고리 삭제

```http
DELETE /api/v1/categories/{categoryId}
```

### 삭제 조건

- 대분류에 중분류가 하나라도 있으면 삭제할 수 없다.
- 중분류에 상품이 하나라도 연결되어 있으면 삭제할 수 없다.
- 조건을 통과하면 `JpaRepository.delete()`를 사용해 카테고리를 물리 삭제한다.

### 성공 응답

Status: `200 OK`

삭제 직전의 카테고리 정보를 응답한다.

```json
{
  "success": true,
  "data": {
    "categoryId": 2,
    "categoryName": "노트북",
    "parentCategory": 1
  },
  "errorCode": null,
  "message": "카테고리 삭제 성공"
}
```

### 실패 응답

| Status | `errorCode` | 발생 조건 |
|---:|---|---|
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않음 |
| 409 | `CATEGORY_HAS_CHILDREN` | 대분류에 하나 이상의 중분류가 존재함 |
| 409 | `CATEGORY_HAS_PRODUCTS` | 중분류에 하나 이상의 상품이 연결되어 있음 |

## 12. 오류 코드 목록

| `errorCode` | HTTP Status | 기본 메시지 |
|---|---:|---|
| `INVALID_INPUT_VALUE` | 400 | 입력값이 올바르지 않습니다. |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리를 찾을 수 없습니다. |
| `CATEGORY_NAME_DUPLICATED` | 409 | 중복된 카테고리명입니다. |
| `PARENT_CATEGORY_MISSING` | 409 | 중분류 카테고리를 입력하세요. |
| `INVALID_PARENT_CATEGORY` | 409 | 대분류에만 추가할 수 있습니다. |
| `CATEGORY_HAS_CHILDREN` | 409 | 하위 카테고리가 있어 카테고리를 삭제할 수 없습니다. |
| `CATEGORY_HAS_PRODUCTS` | 409 | 연결된 상품이 있어 카테고리를 삭제할 수 없습니다. |

## 13. 현재 구현 기준 확인 사항

- [x] 대분류는 `parentCategory` 없이 생성된다.
- [x] 중분류는 URL로 전달된 대분류 ID를 부모로 사용한다.
- [x] 중분류를 부모로 지정할 수 없다.
- [x] 카테고리 이름의 앞뒤 공백을 제거한다.
- [x] 전체 카테고리 범위에서 이름 중복을 차단한다.
- [x] 대분류 목록은 ID 오름차순으로 반환한다.
- [x] 대분류 상세 조회의 `children`에는 중분류가 포함된다.
- [x] 중분류 상세 조회의 `children`에는 삭제되지 않은 상품이 포함된다.
- [x] 하위 중분류가 있는 대분류 삭제를 차단한다.
- [x] 상품이 연결된 중분류 삭제를 차단한다.
- [x] 삭제 성공 시 삭제된 카테고리 정보와 `200 OK`를 반환한다.
- [x] 카테고리는 소프트 삭제가 아닌 물리 삭제된다.
