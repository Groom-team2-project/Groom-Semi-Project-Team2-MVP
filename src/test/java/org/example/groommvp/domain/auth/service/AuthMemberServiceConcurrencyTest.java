package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthMemberServiceConcurrencyTest {

    @Autowired
    private AuthMemberService authMemberService;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        memberRepository.deleteAllInBatch();
    }

    @Test
    void concurrentLoginWithSameProviderIdCreatesSingleMember() throws InterruptedException {
        String providerId = "kakao-concurrent-user";
        KakaoUserInfo userInfo = new KakaoUserInfo(providerId, "member@example.com", "member");
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Queue<AuthMemberService.MemberLookupResult> results = new ConcurrentLinkedQueue<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    readyLatch.countDown();

                    try {
                        startLatch.await();
                        results.add(authMemberService.findOrCreateMember(userInfo));
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

            startLatch.countDown();

            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            startLatch.countDown();
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                failures.add(new AssertionError("Executor did not terminate."));
            }
        }


        List<Long> memberIds = results.stream()
                .map(result -> result.member().getMemberId())
                .distinct()
                .toList();
        long newMemberCount = results.stream()
                .filter(AuthMemberService.MemberLookupResult::newMember)
                .count();

        assertThat(failures).isEmpty();
        assertThat(results).hasSize(threadCount);
        assertThat(memberIds).hasSize(1);
        assertThat(newMemberCount).isEqualTo(1);
        assertThat(memberRepository.countByProviderAndProviderId(AuthProvider.KAKAO, providerId)).isEqualTo(1);
    }
}
