package org.example.groommvp.global.config;

import org.example.groommvp.domain.auth.security.JwtAuthenticationFilter;
import org.example.groommvp.domain.auth.service.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        return http
                .csrf(AbstractHttpConfigurer::disable) // OAuth만 사용하므로 CSRF도 끔. 단, 자체 로그인 제작시 해당 항목 삭제할 것.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable) // 폼 로그인 기능 명시적 제거
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // 아래에 인증 필요 API 입력
                                "/api/v1/members/me",
                                "/api/v1/carts/**"
                                /*
                                "api/v1/orders/{orderId}",
                                "api/v1/products/{productId}/orders"
                                */
                        ).authenticated()
                        // 이하는 ADMIN 역할이 필요한 API들이며, 개발 완료시 주석 해제하여 사용할 것.
                        /*
                        .requestMatchers(
                                "/api/v1/products/{productId}/stock-in",
                                "/api/v1/products/{productId}/stock",
                                "/api/v1/products/{productId}/stock-histories"
                        ).hasRole("ADMIN")
                         */
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
