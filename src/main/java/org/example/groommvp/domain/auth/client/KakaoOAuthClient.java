package org.example.groommvp.domain.auth.client;

import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.KakaoTokenResponse;
import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class KakaoOAuthClient {

    private final KakaoOAuthProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(KakaoOAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public KakaoUserInfo getUserInfo(String code, String requestRedirectUri) {
        KakaoTokenResponse tokenResponse = requestToken(code, requestRedirectUri);
        Jwt idToken = decodeIdToken(tokenResponse.idToken());

        return new KakaoUserInfo(
                idToken.getSubject(),
                idToken.getClaimAsString("email"),
                idToken.getClaimAsString("nickname")
        );
    }

    private KakaoTokenResponse requestToken(String code, String requestRedirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", properties.clientId());
        body.add("redirect_uri", resolveRedirectUri(requestRedirectUri));
        body.add("code", code);

        if (StringUtils.hasText(properties.clientSecret())) {
            body.add("client_secret", properties.clientSecret());
        }

        try {
            return restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카카오 토큰 요청에 실패했습니다.");
        }
    }

    private Jwt decodeIdToken(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카카오 ID 토큰이 없습니다.");
        }

        try {
            JwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(properties.issuerUri());
            if (jwtDecoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
                nimbusJwtDecoder.setJwtValidator(kakaoIdTokenValidator());
            }
            return jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카카오 ID 토큰 검증에 실패했습니다.");
        }
    }

    private OAuth2TokenValidator<Jwt> kakaoIdTokenValidator() {
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> audience = token.getAudience();
            if (audience != null
                    && StringUtils.hasText(properties.clientId())
                    && audience.contains(properties.clientId())) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Kakao ID token audience is invalid.",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };

        return new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator);
    }

    private String resolveRedirectUri(String requestRedirectUri) {
        if (StringUtils.hasText(requestRedirectUri)) {
            return requestRedirectUri;
        }
        return properties.redirectUri();
    }
}
