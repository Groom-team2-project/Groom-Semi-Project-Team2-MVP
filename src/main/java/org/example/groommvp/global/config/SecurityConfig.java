package org.example.groommvp.global.config;

import org.example.groommvp.domain.auth.security.JwtAuthenticationFilter;
import org.example.groommvp.domain.auth.service.JwtTokenProvider;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                .csrf(AbstractHttpConfigurer::disable) // OAuth留??ъ슜?섎?濡?CSRF???? ?? ?먯껜 濡쒓렇???쒖옉???대떦 ??ぉ ??젣??寃?
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable) // ??濡쒓렇??湲곕뒫 紐낆떆???쒓굅
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                SecurityErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler(((request, response, accessDeniedException) ->
                                SecurityErrorResponseWriter.write(response, ErrorCode.FORBIDDEN)))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/stock").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(
                                "/api/v1/members/me",
                                "/api/v1/members/me/**",
                                "/api/v1/carts/**",
                                "/api/v1/orders/**",
                                "/api/v1/products/*/orders",
                                "/api/v1/coupons/*/issue",
                                "/api/v1/events/*/participate"
                        ).authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/*").authenticated()
                        .requestMatchers("/api/v1/admin/coupons/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/stock-in").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/stock-histories").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/products/*/images"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/products/*/images"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/*/children").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/*").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
