package org.example.groommvp.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.groommvp.domain.auth.config.JwtProperties;
import org.example.groommvp.domain.auth.dto.JwtClaims;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String createAccessToken(MemberEntity member) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.accessTokenExpirationSeconds());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(member.getMemberId()));
        payload.put("role", member.getRole().name());
        payload.put("provider", member.getProvider().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;

        return signingInput + "." + sign(signingInput);
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationSeconds();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 생성에 실패했습니다.", e);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    properties.secret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명에 실패했습니다.", e);
        }
    }

    private Map<String, Object> parsePayload(String token) {
        String[] parts = token.split("\\.");

        if(parts.length != 3) {
            throw new IllegalArgumentException("유효하지 않은 JWT 포맷입니다.");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        String actualSignature = parts[2];

        if(!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("유효하지 않은 JWT 서명입니다.");
        }

        try {
            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(decodedPayload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 JWT Payload입니다.");
        }
    }

    public JwtClaims getValidClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT 토큰이 비어있습니다.");
        }
        Map<String, Object> payload = parsePayload(token);
        JwtClaims claims = toJwtClaims(payload);

        if (!claims.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("JWT 토큰이 만료되었습니다.");
        }

        return claims;
    }

    private JwtClaims toJwtClaims(Map<String, Object> payload) {
        return new JwtClaims(
                Long.valueOf(String.valueOf(payload.get("sub"))),
                MemberRole.valueOf(String.valueOf(payload.get("role"))),
                AuthProvider.valueOf(String.valueOf(payload.get("provider"))),
                Instant.ofEpochSecond(toLong(payload.get("iat"))),
                Instant.ofEpochSecond(toLong(payload.get("exp")))
        );
    }

    public boolean validateToken(String token) {
        try {
            getValidClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("JWT 숫자형 Claim이 비어있습니다.");
        }

        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JWT 숫자형 Claim이 유효하지 않습니다.");
        }


    }

    private JwtClaims getClaims(String token) {
        Map<String, Object> payload = parsePayload(token);

        return toJwtClaims(payload);
    }

    public Long getMemberId(String token) {
        return getValidClaims(token).memberId();
    }

    public MemberRole getRole(String token) {
        return getValidClaims(token).role();
    }
}
