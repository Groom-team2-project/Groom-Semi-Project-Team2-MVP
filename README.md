# Groom-Semi-Project-Team2-MVP

- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 4.1.0
- **Build Tool:** Gradle - Groovy
- **Database:** MySQL
- **JPA & ORM:** Spring Data JPA
- **Testing:** JUnit5

### 프로젝트 패키지 구조

```text
com.shop.buyingmvp (최상위 패키지)
├── global                   # 프로젝트 공통 관리
│   ├── config               # 인프라, 보안, Swagger 등 공통 설정
│   ├── error                # 글로벌 예외 처리 (Exception Handler, ErrorCode)
│   └── common               # 공통 유틸, BaseEntity (생성/수정일자 등)
│
├── domain                   # 비즈니스 도메인별 패키지
│   ├── product              # 상품 도메인
│   │   ├── controller       # 상품 API 컨트롤러
│   │   ├── service          # 상품 비즈니스 로직 인터페이스 및 구현체
│   │   ├── repository       # 상품 JPA 레포지토리
│   │   ├── dto              # 상품 관련 Request / Response DTO
│   │   └── entity           # 상품 JPA 엔티티 (Product)
│   │
│   ├── order                # 주문/구매 도메인
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── dto
│   │   └── entity           # 주문 엔티티 (Order, OrderItem)
│   │
│   ├── cancel               # 구매 취소 도메인
│   │   ├── controller
│   │   ├── service
│   │   └── dto
│   │
│   └── stock                # 재고/입고 히스토리 도메인
│       ├── controller
│       ├── service
│       ├── repository
│       ├── dto
│       └── entity           # 재고 히스토리 엔티티 (StockHistory)
│
└── GroomMvpApplication.java # 메인 스프링부트 실행 클래스
```

## 🔀 브랜치 전략 및 협업 가이드

### 1. 브랜치 구조 및 명명 규칙

* **`개인 이름/기능`** : 각자 맡은 MVP 기능을 개발하는 로컬/원격 작업 브랜치입니다.
  * *예시 (박선우) :* `sunwoo/order-api`
  * *예시 (노현섭) :* `hyunseop/stock-history`
  * *예시 (김승진) :* `seungjin/cancel-logic`
  * *예시 (주정현) :* `junghyun/product-list`
  * *예시 (박소빈) :* `sobin/product-crud`

---

### 2. 기본 작업 워크플로우 (Workflow)

새로운 기능을 개발할 때는 반드시 아래 순서대로 깃 명령어를 수행해 주세요.

#### ① 로컬 최신화 및 브랜치 생성
항상 `develop` 브랜치의 최신 코드를 기반으로 새로운 작업 브랜치를 만듭니다.
```bash
git fetch origin
git merge origin/develop
```

### 3. 컨벤션 

- Java 코드
  camelCase를 사용합니다.

```java
private Long productId;
private Long productId;

@Column(name = "product_id")
private Long productId;
```

- DB 테이블/컬럼
  snake_case를 사용하며, 테이블명은 복수형을 사용합니다.

```
@Table(name = "products")
@Table(name = "stocks")
@Table(name = "orders")
@Table(name = "order_items")
@Table(name = "stock_histories")
```

- API 경로
  API 경로는 복수형 리소스명을 기준으로 작성합니다.

```
/api/v1/products
/api/v1/products/{productId}
/api/v1/products/{productId}/stock-in
/api/v1/products/{productId}/stock-histories
/api/v1/products/{productId}/orders
/api/v1/orders/{orderId}/cancel
```

- 응답 형식
  API 응답은 공통 응답 객체를 사용하도록 통일합니다.

ex) 성공 / 실패
```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": "요청이 성공했습니다."
}

또는,

{
  "success": false,
  "data": null,
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다."
}
```
추가로, 해당 응답을 위해서 아래와 같은 반환 타입을 따르면 됩니다.
```java
ResponseEntity<CommonResponse<[Type]>>

ex)
@PostMapping("/{productId}/orders")
    public ResponseEntity<CommonResponse<PurchaseResponse>> purchase(
            @PathVariable Long productId,
            @Valid @RequestBody PurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(purchaseService.purchase(productId, request), "구매가 정상적으로 처리되었습니다."));
    }
```
