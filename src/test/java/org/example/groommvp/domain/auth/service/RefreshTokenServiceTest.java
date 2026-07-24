package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.example.groommvp.domain.auth.config.JwtProperties;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void issueStoresSessionBasedRefreshTokenWithTtl() {
        @SuppressWarnings("unchecked")
        RBucket<String> refreshBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> memberBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> activeBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString(), eq(StringCodec.INSTANCE)))
                .thenReturn(refreshBucket, memberBucket, activeBucket);

        String refreshToken = refreshTokenService.issue(1L);

        assertThat(refreshToken).isNotBlank();
        verify(redissonClient, times(3)).getBucket(anyString(), eq(StringCodec.INSTANCE));
        verify(refreshBucket).set(anyString(), eq(Duration.ofSeconds(1209600L)));
        verify(memberBucket).set("1", Duration.ofSeconds(1209600L));
        verify(activeBucket).set(anyString(), eq(Duration.ofSeconds(1209600L)));
    }

    @Test
    void validateAndGetMemberIdReturnsStoredMemberId() {
        @SuppressWarnings("unchecked")
        RBucket<String> refreshBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> revokedBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> memberBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString(), eq(StringCodec.INSTANCE)))
                .thenReturn(refreshBucket, revokedBucket, memberBucket);
        when(refreshBucket.get()).thenReturn("session-id");
        when(revokedBucket.get()).thenReturn(null);
        when(memberBucket.get()).thenReturn("1");

        Long memberId = refreshTokenService.validateAndGetMemberId("refresh-token");

        assertThat(memberId).isEqualTo(1L);
    }

    @Test
    void validateAndGetMemberIdThrowsWhenTokenIsMissing() {
        assertThatThrownBy(() -> refreshTokenService.validateAndGetMemberId(" "))
                .isInstanceOf(BusinessException.class);

        verify(redissonClient, never()).getBucket(anyString(), eq(StringCodec.INSTANCE));
    }

    @Test
    void validateAndGetMemberIdThrowsWhenTokenIsExpiredOrRevoked() {
        @SuppressWarnings("unchecked")
        RBucket<String> refreshBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(refreshBucket);
        when(refreshBucket.get()).thenReturn(null);

        assertThatThrownBy(() -> refreshTokenService.validateAndGetMemberId("refresh-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateAndGetMemberIdThrowsWhenSessionIsRevoked() {
        @SuppressWarnings("unchecked")
        RBucket<String> refreshBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> revokedBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString(), eq(StringCodec.INSTANCE)))
                .thenReturn(refreshBucket, revokedBucket);
        when(refreshBucket.get()).thenReturn("session-id");
        when(revokedBucket.get()).thenReturn("true");

        assertThatThrownBy(() -> refreshTokenService.validateAndGetMemberId("refresh-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rotateReplacesRefreshTokenAtomically() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(),
                any(),
                eq("1"),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        )).thenReturn(1L);

        String newRefreshToken = refreshTokenService.rotate("old-refresh-token", 1L);

        assertThat(newRefreshToken).isNotBlank();
        assertThat(newRefreshToken).isNotEqualTo("old-refresh-token");
        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                scriptCaptor.capture(),
                eq(RScript.ReturnType.LONG),
                keysCaptor.capture(),
                any(),
                any(),
                eq("1"),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        );
        assertThat(keysCaptor.getValue()).hasSize(2);
        assertThat(scriptCaptor.getValue())
                .contains("psetex', KEYS[1]")
                .contains("usedKey")
                .contains("return -1");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rotateThrowsWhenOldTokenCannotBeConsumed() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(),
                any(),
                eq("1"),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        )).thenReturn(0L);

        assertThatThrownBy(() -> refreshTokenService.rotate("old-refresh-token", 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rotateThrowsWhenUsedRefreshTokenIsReused() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(),
                any(),
                eq("1"),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        )).thenReturn(-1L);

        assertThatThrownBy(() -> refreshTokenService.rotate("old-refresh-token", 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokeRevokesSessionAndDeletesActiveRefreshTokenAtomically() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        )).thenReturn(1L);

        refreshTokenService.revoke("refresh-token");

        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                scriptCaptor.capture(),
                eq(RScript.ReturnType.LONG),
                keysCaptor.capture(),
                eq("1209600000"),
                eq("auth:session"),
                eq("auth:refresh")
        );
        assertThat(keysCaptor.getValue()).hasSize(1);
        assertThat(scriptCaptor.getValue())
                .contains("revokedKey")
                .contains("activeRefreshKey")
                .contains("del', activeKey");
    }

    @Test
    void revokeIgnoresBlankToken() {
        refreshTokenService.revoke(" ");

        verify(redissonClient, never()).getScript(StringCodec.INSTANCE);
    }
}
