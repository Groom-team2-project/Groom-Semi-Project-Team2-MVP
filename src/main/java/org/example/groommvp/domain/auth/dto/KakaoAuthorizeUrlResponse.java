package org.example.groommvp.domain.auth.dto;

public record KakaoAuthorizeUrlResponse (
        String url,
        String state // Swagger 편의상 등록. 제거해도 됩니다.
)
{ }
