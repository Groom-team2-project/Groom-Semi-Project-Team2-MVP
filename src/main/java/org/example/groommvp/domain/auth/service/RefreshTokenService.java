package org.example.groommvp.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.config.JwtProperties;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedissonClient redissonClient;
    private final JwtProperties jwtProperties;

    public String issue(Long memberId) {
        String refreshToken = generateSecureToken();

        RBucket<String> bucket = redissonClient.getBucket(tokenKey(refreshToken), StringCodec.INSTANCE);
        bucket.set(String.valueOf(memberId), Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()));

        return refreshToken;
    }

    public Long validateAndGetMemberId(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Refresh Token이 필요합니다.");
        }

        RBucket<String> bucket = redissonClient.getBucket(tokenKey(refreshToken));
        String memberId = bucket.get();

        if (!StringUtils.hasText(memberId)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 Refresh Token입니다.");
        }

        return Long.valueOf(memberId);
    }

    public String rotate(String oldRefreshToken, Long expectedMemberId) {
        String newRefreshToken = generateSecureToken();

        String oldKey = tokenKey(oldRefreshToken);
        String newKey = tokenKey(newRefreshToken);
        String memberId = String.valueOf(expectedMemberId);
        long ttlMillis = Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()).toMillis();

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                """
                        local current = redis.call('get', KEYS[1])
                        if not current then
                            return 0
                        end
                        if current ~= ARGV[1] then
                            return 0
                        end
                        redis.call('del', KEYS[1])
                        redis.call('psetex', KEYS[2], ARGV[2], ARGV[1])
                        return 1
                        """,
                RScript.ReturnType.LONG,
                List.of(oldKey, newKey),
                memberId,
                String.valueOf(ttlMillis)
        );

        if (result == null || result == 0) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 Refresh Token입니다.");
        }

        return newRefreshToken;
    }

    public void revoke(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        redissonClient.getBucket(tokenKey(refreshToken)).delete();
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtProperties.refreshTokenExpirationSeconds();
    }

    private String tokenKey(String refreshToken) {
        return REFRESH_TOKEN_KEY_PREFIX + sha256(refreshToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Refresh Token Hashing에 실패하였습니다.");
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
