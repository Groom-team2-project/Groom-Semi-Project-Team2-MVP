package org.example.groommvp.global.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
                                ## Product, Stock, Order API

                                Provides product management, stock management, purchase, order lookup,
                                and order cancel APIs.

                                ### Common Response
                                Most successful responses use `CommonResponse<T>`.
                                Create APIs may return created resource identifiers, and update/delete APIs
                                may return `204 No Content`.

                                ### Authentication
                                Use the Swagger UI Authorize button with a JWT access token issued by the
                                login API. Swagger sends it as `Authorization: Bearer {token}`.
                                Current API access rules are still open, but requests with a valid token
                                are recognized as authenticated users.
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
                )
                .security(List.of(new SecurityRequirement().addList(JWT_SECURITY_SCHEME_NAME)));
    }
}
