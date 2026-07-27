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
                    "redis.port=1",
                    "redis.lock-watchdog-timeout-ms=45000"
            );

    @Test
    @DisplayName("RedissonClient가 Spring Bean으로 등록됩니다")
    void redissonClientBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedissonClient.class);
            assertThat(context.getBean(RedissonClient.class)
                    .getConfig()
                    .getLockWatchdogTimeout())
                    .isEqualTo(45000L);
        });
    }
}
