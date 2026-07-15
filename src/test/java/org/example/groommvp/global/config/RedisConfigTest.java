package org.example.groommvp.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class RedisConfigTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("RedissonClient가 Spring Bean으로 등록됩니다")
    void redissonClientBeanExists() {
        assertThat(redissonClient).isNotNull();
    }
}
