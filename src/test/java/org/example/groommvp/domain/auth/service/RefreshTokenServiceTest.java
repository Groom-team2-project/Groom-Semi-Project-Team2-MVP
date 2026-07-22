package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.example.groommvp.domain.auth.config.JwtProperties;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

class RefreshTokenServiceTest {

    private RedissonClient redissonClient;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        JwtProperties jwtProperties = new JwtProperties("test-secret", 7200L, 1209600L);
        refreshTokenService = new RefreshTokenService(redissonClient, jwtProperties);
    }

    @Test
    void issueStoresRefreshTokenWithMemberIdAndTtl() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

        String refreshToken = refreshTokenService.issue(1L);

        assertThat(refreshToken).isNotBlank();
        verify(redissonClient).getBucket(anyString());
        verify(bucket).set("1", Duration.ofSeconds(1209600L));
    }

    @Test
    void validateAndGetMemberIdReturnsStoredMemberId() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn("1");

        Long memberId = refreshTokenService.validateAndGetMemberId("refresh-token");

        assertThat(memberId).isEqualTo(1L);
    }

    @Test
    void validateAndGetMemberIdThrowsWhenTokenIsMissing() {
        assertThatThrownBy(() -> refreshTokenService.validateAndGetMemberId(" "))
                .isInstanceOf(BusinessException.class);

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    void validateAndGetMemberIdThrowsWhenTokenIsExpiredOrRevoked() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        assertThatThrownBy(() -> refreshTokenService.validateAndGetMemberId("refresh-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rotateReplacesRefreshTokenAtomically() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                eq("1"),
                eq("1209600000")
        )).thenReturn(1L);

        String newRefreshToken = refreshTokenService.rotate("old-refresh-token", 1L);

        assertThat(newRefreshToken).isNotBlank();
        assertThat(newRefreshToken).isNotEqualTo("old-refresh-token");
        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                eq("1"),
                eq("1209600000")
        );
    }

    @Test
    void rotateThrowsWhenOldTokenCannotBeConsumed() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                eq("1"),
                eq("1209600000")
        )).thenReturn(0L);

        assertThatThrownBy(() -> refreshTokenService.rotate("old-refresh-token", 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void revokeDeletesRefreshToken() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

        refreshTokenService.revoke("refresh-token");

        verify(bucket).delete();
    }

    @Test
    void revokeIgnoresBlankToken() {
        refreshTokenService.revoke(" ");

        verify(redissonClient, never()).getBucket(anyString());
    }
}
