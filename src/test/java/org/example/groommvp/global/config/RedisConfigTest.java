package org.example.groommvp.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class)
            .withPropertyValues(
                    "redis.host=localhost",
                    "redis.port=6379"
            );

    @Test
    @DisplayName("RedissonClient가 Spring Bean으로 등록됩니다")
    void redissonClientBeanExists() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(RedissonClient.class));
    }
}
