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
항상 `main` 브랜치의 최신 코드를 기반으로 새로운 작업 브랜치를 만듭니다.
```bash
git checkout main
git pull origin main
git checkout -b 본인이름/구현기능
# 예시: git checkout -b sunwoo/order-api
```
