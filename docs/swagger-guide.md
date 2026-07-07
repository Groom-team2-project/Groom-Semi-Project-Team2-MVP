# Swagger/OpenAPI 문서화 가이드

## 1. Swagger 작성 목적

Swagger 문서는 프론트엔드, QA, 백엔드 팀원이 코드를 직접 보지 않고도 API를 연동하고 검증할 수 있게 만드는 내부 계약서입니다.

완료 기준은 Swagger UI가 열리는 것이 아니라, 실제 요청/응답 JSON, HTTP status, errorCode, message가 문서와 일치하는 것입니다.

## 2. Swagger UI 사용 방법

로컬에서 서버를 실행합니다.

```bash
./gradlew bootRun
```

브라우저에서 Swagger UI에 접속합니다.

```text
http://localhost:8080/swagger-ui/index.html
```

API를 직접 테스트할 때는 다음 순서로 진행합니다.

1. 테스트할 API를 클릭합니다.
2. `Try it out`을 클릭합니다.
3. `PathVariable`, `RequestParam`, `Request body` 값을 입력합니다.
4. `Execute`를 클릭합니다.
5. 아래 `Server response`에서 실제 HTTP status와 response body를 확인합니다.

`Responses` 영역은 가능한 응답 예시를 보여주는 설명서이고, `Server response`가 실제 실행 결과입니다.

예를 들어 상품 목록 조회는 `GET /api/v1/products`를 선택하고 `page=0`, `size=10`으로 실행합니다. `{productId}`가 붙은 `GET /api/v1/products/{productId}`는 상품 단건 조회이므로 혼동하지 않습니다.

## 3. 우리 프로젝트의 공통 응답 구조

대부분의 성공 응답은 `CommonResponse<T>`로 감싸서 반환합니다.

```json
{
  "success": true,
  "data": { "id": 1 },
  "errorCode": null,
  "message": "성공 메시지"
}
```

에러 응답도 같은 최상위 구조를 사용하며, `data`는 항상 `null`입니다.

```json
{
  "success": false,
  "data": null,
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다."
}
```

상품 등록은 `201` 빈 body, 상품 수정/삭제는 `204` 빈 body로 문서화합니다.

## 4. Controller 작성 규칙

- 모든 API에 `@Operation(summary, description)`을 작성합니다.
- 성공 status code는 실제 `ResponseEntity`와 일치시킵니다.
- 주요 실패 status code는 실제 `BusinessException`, validation, 전역 예외 핸들러와 일치시킵니다.
- `PathVariable`, `RequestParam`에는 `@Parameter(description, example)`을 작성합니다.
- `CommonResponse<T>` 응답은 내부 DTO가 아니라 `SwaggerResponse.*CommonResponse` wrapper schema를 지정합니다.
- 빈 body 응답은 `@Content(schema = @Schema(hidden = true))`로 명시합니다.

## 5. DTO 작성 규칙

- Request/Response DTO 주요 필드에 `@Schema(description, example)`을 작성합니다.
- validation annotation과 설명이 서로 다르면 안 됩니다.
- nullable 필드는 description에 null 가능성을 적습니다.
- `LocalDateTime` 필드는 비표준 format을 쓰지 않고 description에 실제 형식을 적습니다.

예시:

```java
@Schema(
    type = "string",
    description = "서버 로컬 시간 기준 ISO-8601 LocalDateTime 형식, 예: 2024-01-15T09:00:00",
    example = "2024-01-15T09:00:00"
)
private LocalDateTime createdAt;
```

## 6. 에러 응답 작성 규칙

- 에러 응답 schema는 `ErrorResponse`를 사용합니다.
- 실제 응답처럼 `success`, `data`, `errorCode`, `message` 구조를 유지합니다.
- validation 실패는 `INVALID_INPUT_VALUE`로 문서화합니다.
- 프론트/QA가 알아야 하는 주요 도메인 에러만 예시에 추가합니다.
- 하나의 status code에 여러 errorCode가 가능하면 `@ExampleObject`를 여러 개 둡니다.

## 7. SwaggerResponse Wrapper 사용 이유와 절차

컨트롤러가 `CommonResponse<ProductResponse>`를 반환하는데 Swagger schema를 `ProductResponse.class`로만 지정하면 실제 JSON의 최상위 필드인 `success`, `data`, `errorCode`, `message`가 문서에서 빠집니다.

그래서 성공 응답은 문서 전용 wrapper인 `SwaggerResponse.*CommonResponse`를 사용합니다.

새 API 추가 절차:

1. 실제 data DTO를 먼저 만듭니다. 예: `CouponResponse`
2. `SwaggerResponse`에 `CouponCommonResponse` record를 추가합니다.
3. record 필드는 `success`, `data`, `errorCode`, `message` 구조로 작성합니다.
4. Controller의 성공 응답 schema에서 `SwaggerResponse.CouponCommonResponse.class`를 참조합니다.

## 8. 좋은 예시

```java
@Operation(summary = "상품 단건 조회", description = "상품 ID로 상품 상세 정보와 현재 재고 수량을 조회합니다.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = SwaggerResponse.ProductCommonResponse.class))),
    @ApiResponse(responseCode = "404", description = "상품 또는 재고 정보를 찾을 수 없음",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = """
                {
                  "success": false,
                  "data": null,
                  "errorCode": "PRODUCT_NOT_FOUND",
                  "message": "상품을 찾을 수 없습니다."
                }
                """)))
})
```

## 9. 나쁜 예시

```java
@Operation(summary = "조회")
@ApiResponse(responseCode = "200",
    content = @Content(schema = @Schema(implementation = ProductResponse.class)))
public ResponseEntity<CommonResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
    ...
}
```

이 예시는 실제 응답이 `CommonResponse<ProductResponse>`인데 schema는 `ProductResponse`만 보여주므로 실제 JSON 구조와 맞지 않습니다.

## 10. PR 체크리스트

- [ ] 신규/수정 API가 Swagger UI에 노출됩니다.
- [ ] summary와 description이 실제 기능을 설명합니다.
- [ ] 요청 DTO의 필수값, 예시값, validation 조건이 이해 가능합니다.
- [ ] 실제 성공 응답 JSON과 Swagger response schema가 일치합니다.
- [ ] 공통 응답 wrapper가 누락되지 않았습니다.
- [ ] 주요 실패 케이스 400/404/409가 실제 errorCode와 함께 문서화되었습니다.
- [ ] 에러 응답 schema가 실제 ErrorResponse 구조와 일치합니다.
- [ ] 운영 환경에서 Swagger가 노출되지 않는 설정을 유지합니다.

## 운영 Swagger 노출 관리

`application.yaml`에는 `spring.profiles.active: local`을 하드코딩하지 않습니다. 운영 배포 시 반드시 `SPRING_PROFILES_ACTIVE=prod`를 지정해야 합니다.

prod 프로파일에서는 다음 설정으로 Swagger를 비활성화합니다.

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

배포 후 `/swagger-ui/index.html`과 `/v3/api-docs`가 노출되지 않는지 확인합니다.
