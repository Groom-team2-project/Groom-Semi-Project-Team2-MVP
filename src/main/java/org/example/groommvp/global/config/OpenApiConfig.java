package org.example.groommvp.global.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String JWT_SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Groom MVP API")
                        .description("""
                                ## 상품 관리 및 주문 처리 API

                                상품 등록/조회/수정/삭제, 재고 관리, 주문 생성, 결제, 주문 취소 기능을 제공합니다.

                                ### 공통 응답 형식
                                **대부분의 조회·생성 성공 응답**은 `CommonResponse<T>` 로 감싸져 반환됩니다.
                                상품 등록(POST /api/v1/products)은 **201 상태 코드와 생성된 상품 ID**를 반환합니다.
                                리소스 수정(PUT)·삭제(DELETE)는 **204 빈 body**를 반환합니다.

                                성공 응답의 **Schema 탭**에는 `CommonResponse` 구조와 `data` 필드의 구체 타입을 함께 표시합니다.
                                ```json
                                {
                                  "success": true,
                                  "data": { ... },
                                  "errorCode": null,
                                  "message": "성공 메시지"
                                }
                                ```

                                ### 인증/인가
                                일부 API는 자체 JWT access token 인증이 필요합니다.
                                Swagger 우측 상단 **Authorize** 버튼에 발급받은 access token 값을 입력하면 인증 API를 테스트할 수 있습니다.
                                인증이 필요한 API는 각 Operation에 `bearerAuth` 보안 요구사항이 표시됩니다.
                                인증 정보가 없거나 유효하지 않으면 401, 권한이 부족하면 403 공통 에러 응답을 반환합니다.

                                ### 재고 변동 타입 (StockHistoryType)
                                | 타입 | 설명 |
                                |---|---|
                                | `INBOUND` | 입고로 인한 재고 증가 |
                                | `DECREASE` | 구매로 인한 재고 차감 |
                                | `RESTORE` | 주문 취소로 인한 재고 복구 |
                                | `RESERVE` | 결제 전 재고 임시 예약 |
                                | `CONFIRM` | 결제 성공 후 예약 재고 확정 차감 |
                                | `RELEASE` | 결제 실패/만료 후 예약 재고 해제 |

                                ### 주문 상태 (OrderStatus)
                                | 상태 | 설명 |
                                |---|---|
                                | `PENDING_PAYMENT` | 결제 대기 |
                                | `COMPLETED` | 결제 완료 |
                                | `CANCELED` | 취소됨 |
                                | `PAYMENT_FAILED` | 결제 실패 |
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Groom Team2")
                                .email("team2@groom.com")
                        )
                )
                .servers(List.of(
                        new Server().url("/").description("Current server")
                ))
                .components(new Components()
                        .addSecuritySchemes(
                                JWT_SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
