package org.example.groommvp.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final String STATE_KEY_PREFIX = "oauth:kakao:state:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedissonClient redissonClient;

    public String issueState() {
        String state = generateSecureState();

        RBucket<String> bucket = redissonClient.getBucket(STATE_KEY_PREFIX + state);
        bucket.set("valid", STATE_TTL);

        return state;
    }

    public void validateAndConsume(String state) {
        if (!StringUtils.hasText(state)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "OAuth State가 필요합니다.");
        }

        RBucket<String> bucket = redissonClient.getBucket(STATE_KEY_PREFIX + state);

        String value = bucket.getAndDelete();
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않거나 만료된 OAuth State입니다.");
        }
    }

    private String generateSecureState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
