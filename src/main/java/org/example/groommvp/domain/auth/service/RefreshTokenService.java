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
    private static final String SESSION_KEY_PREFIX = "auth:session";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedissonClient redissonClient;
    private final JwtProperties jwtProperties;

    public String issue(Long memberId) {
        String refreshToken = generateSecureToken();
        String sessionId = generateSecureToken();
        String tokenHash = sha256(refreshToken);

        Duration ttl = Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds());

        redissonClient
                .getBucket(refreshTokenKey(tokenHash), StringCodec.INSTANCE)
                .set(sessionId, ttl);
        redissonClient
                .getBucket(sessionMemberKey(sessionId), StringCodec.INSTANCE)
                .set(String.valueOf(memberId), ttl);
        redissonClient
                .getBucket(sessionActiveKey(sessionId), StringCodec.INSTANCE)
                .set(tokenHash, ttl);

        return refreshToken;
    }

    public Long validateAndGetMemberId(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Refresh Token이 필요합니다.");
        }

        String tokenHash = sha256(refreshToken);

        RBucket<String> refreshBucket =
                redissonClient.getBucket(refreshTokenKey(tokenHash), StringCodec.INSTANCE);

        String sessionId = refreshBucket.get();

        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 Refresh Token입니다.");
        }

        RBucket<String> revokedBucket =
                redissonClient.getBucket(sessionRevokedKey(sessionId), StringCodec.INSTANCE);

        if (StringUtils.hasText(revokedBucket.get())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "만료된 로그인 세션입니다.");
        }

        RBucket<String> memberBucket =
                redissonClient.getBucket(sessionMemberKey(sessionId), StringCodec.INSTANCE);
        String memberId = memberBucket.get();

        if (!StringUtils.hasText(memberId)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 로그인 세션입니다.");
        }

        return Long.valueOf(memberId);
    }

    public String rotate(String oldRefreshToken, Long expectedMemberId) {
        String newRefreshToken = generateSecureToken();

        String oldTokenHash = sha256(oldRefreshToken);
        String newTokenHash = sha256(newRefreshToken);

        String oldKey = refreshTokenKey(oldTokenHash);
        String newKey = refreshTokenKey(newTokenHash);

        String memberId = String.valueOf(expectedMemberId);
        long ttlMillis = Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()).toMillis();

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                """
                        local sessionId = redis.call('get', KEYS[1])
                        
                        if not sessionId then
                            return 0
                        end
                        
                        local sessionPrefix = ARGV[5] .. ':' .. sessionId
                        local memberKey = sessionPrefix .. ':member'
                        local activeKey = sessionPrefix .. ':active'
                        local revokedKey = sessionPrefix .. ':revoked'
                        local usedKey = sessionPrefix .. ':used:' .. ARGV[1]
                        
                        local memberId = redis.call('get', memberKey)
                        if memberId ~= ARGV[3] then
                            return 0
                        end
                        
                        local revoked = redis.call('get', revokedKey)
                        if revoked then
                            return 0
                        end
                        
                        local activeTokenHash = redis.call('get', activeKey)
                        if not activeTokenHash then
                            return 0
                        end
                        
                        if activeTokenHash ~= ARGV[1] then
                            local used = redis.call('get', usedKey)

                            if used then
                                local activeRefreshKey = ARGV[6] .. ':' .. activeTokenHash
                                
                                redis.call('psetex', revokedKey, ARGV[4], 'true')
                                redis.call('del', activeRefreshKey)
                                redis.call('del', KEYS[1])
                                redis.call('del', activeKey)
                                
                                return -1
                            end
                            
                            return 0
                        end
                        
                        redis.call('psetex', KEYS[1], ARGV[4], sessionId)
                        redis.call('psetex', usedKey, ARGV[4], 'true')
                        redis.call('psetex', KEYS[2], ARGV[4], sessionId)
                        redis.call('psetex', activeKey, ARGV[4], ARGV[2])
                        redis.call('psetex', memberKey, ARGV[4], memberId)
                        
                        return 1
                        """,
                RScript.ReturnType.LONG,
                List.of(oldKey, newKey),
                oldTokenHash,
                newTokenHash,
                memberId,
                String.valueOf(ttlMillis),
                SESSION_KEY_PREFIX,
                REFRESH_TOKEN_KEY_PREFIX

        );

        if (result == null || result == 0) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 Refresh Token입니다.");
        }

        if (result == -1) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh Token 재사용이 감지되어 로그인 세션이 종료되었습니다.");
        }

        return newRefreshToken;
    }

    public void revoke(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        String tokenHash = sha256(refreshToken);
        String refreshKey = refreshTokenKey(tokenHash);
        long ttlMillis = Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()).toMillis();

        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                """
                    local sessionId = redis.call('get', KEYS[1])
                    if not sessionId then
                        return 0
                    end
                   
                    local sessionPrefix = ARGV[2] .. ':' .. sessionId
                    local activeKey = sessionPrefix .. ':active'
                    local revokedKey = sessionPrefix .. ':revoked'
                   
                    local activeTokenHash = redis.call('get', activeKey)
                    if activeTokenHash then
                        local activeRefreshKey = ARGV[3] .. ':' .. activeTokenHash
                        redis.call('del', activeRefreshKey)
                    end
                    
                    redis.call('psetex', revokedKey, ARGV[1], 'true')
                    redis.call('del', KEYS[1])
                    redis.call('del', activeKey)
                    
                    return 1
                    """,
                RScript.ReturnType.LONG,
                List.of(refreshKey),
                String.valueOf(ttlMillis),
                SESSION_KEY_PREFIX,
                REFRESH_TOKEN_KEY_PREFIX
        );
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtProperties.refreshTokenExpirationSeconds();
    }

    @Deprecated
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

    private String refreshTokenKey(String tokenHash) {
        return REFRESH_TOKEN_KEY_PREFIX + ":" + tokenHash;
    }

    private String sessionMemberKey(String sessionId) {
        return SESSION_KEY_PREFIX + ":" + sessionId + ":member";
    }

    private String sessionActiveKey(String sessionId) {
        return SESSION_KEY_PREFIX + ":" + sessionId + ":active";
    }

    private String sessionRevokedKey(String sessionId) {
        return SESSION_KEY_PREFIX + ":" + sessionId + ":revoked";
    }

    @Deprecated
    private String sessionUsedKey(String sessionId, String tokenHash) {
        return SESSION_KEY_PREFIX + ":" + sessionId + ":used:" + tokenHash;
    }
}
