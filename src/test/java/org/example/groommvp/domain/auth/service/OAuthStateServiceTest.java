package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.example.groommvp.domain.auth.dto.OAuthState;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class OAuthStateServiceTest {

    private static final String STATE_KEY_PREFIX = "oauth:kakao:state:";

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Test
    void issueStateStoresGeneratedStateWithTtl() {
        when(redissonClient.<String>getBucket(startsWith(STATE_KEY_PREFIX))).thenReturn(bucket);
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        OAuthState oAuthState = oAuthStateService.issueState();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nonceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(redissonClient).<String>getBucket(keyCaptor.capture());
        verify(bucket).set(nonceCaptor.capture(), ttlCaptor.capture());

        assertThat(oAuthState.state()).isNotBlank();
        assertThat(oAuthState.nonce()).isNotBlank();
        assertThat(keyCaptor.getValue()).isEqualTo(STATE_KEY_PREFIX + oAuthState.state());
        assertThat(nonceCaptor.getValue()).isEqualTo(oAuthState.nonce());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void validateAndConsumeSucceedsWhenStateExists() {
        String state = "valid-state";
        String nonce = "valid-nonce";
        when(redissonClient.<String>getBucket(STATE_KEY_PREFIX + state)).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(nonce);
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        oAuthStateService.validateAndConsume(state, nonce);

        verify(bucket).getAndDelete();
    }

    @Test
    void validateAndConsumeRejectsBlankState() {
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        assertThatThrownBy(() -> oAuthStateService.validateAndConsume(" ", "nonce"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateAndConsumeRejectsBlankNonce() {
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        assertThatThrownBy(() -> oAuthStateService.validateAndConsume("state", " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateAndConsumeRejectsMissingOrExpiredState() {
        String state = "expired-state";
        when(redissonClient.<String>getBucket(STATE_KEY_PREFIX + state)).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(null);
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        assertThatThrownBy(() -> oAuthStateService.validateAndConsume(state, "nonce"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateAndConsumeRejectsMismatchedNonce() {
        String state = "valid-state";
        when(redissonClient.<String>getBucket(STATE_KEY_PREFIX + state)).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn("saved-nonce");
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        assertThatThrownBy(() -> oAuthStateService.validateAndConsume(state, "wrong-nonce"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateAndConsumeRejectsReusedState() {
        String state = "reused-state";
        String nonce = "valid-nonce";
        when(redissonClient.<String>getBucket(STATE_KEY_PREFIX + state)).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(nonce, (String) null);
        OAuthStateService oAuthStateService = new OAuthStateService(redissonClient);

        oAuthStateService.validateAndConsume(state, nonce);

        assertThatThrownBy(() -> oAuthStateService.validateAndConsume(state, nonce))
                .isInstanceOf(BusinessException.class);
    }
}
