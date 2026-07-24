package org.example.groommvp.domain.cart.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * 장바구니 조회 캐싱 설정. (파트 E)
 *
 * <p><b>기존 {@code RedisConfig} 와의 관계:</b> 그쪽의 {@code RedissonClient} 는 분산락(파트 C)
 * 전용이라 건드리지 않는다. 캐시는 Spring Cache 추상화 위에 <b>별도로</b> 구성한다.
 * 덕분에 서비스 코드는 {@code @Cacheable}/{@code @CacheEvict} 만 쓰고 Redis 에 직접 의존하지 않으며,
 * 락과 캐시가 서로의 설정에 얽히지 않는다.
 *
 * <p><b>커넥션:</b> 팀이 이미 쓰는 {@code redis.host}/{@code redis.port} 프로퍼티를 그대로
 * 재사용해 접속 정보의 단일 출처를 유지한다. (Spring Boot 기본값인 {@code spring.data.redis.*} 를
 * 새로 도입하면 같은 Redis 주소가 두 군데에 적히게 되므로 쓰지 않는다.)
 *
 * <p><b>직렬화:</b> 값은 JSON 으로 저장한다. {@code redis-cli} 로 사람이 읽을 수 있고,
 * 자바 직렬화와 달리 클래스 시그니처가 바뀌어도 덜 취약하다. 캐시 값 DTO 는 record 로 두어
 * Jackson 이 별도 설정 없이 역직렬화하도록 한다. (Spring Boot 4 는 Jackson 3 를 쓰므로
 * Jackson 2 용 {@code GenericJackson2JsonRedisSerializer} 는 쓰지 않는다.)
 *
 * <p><b>TTL:</b> 무효화(evict)가 주 수단이고, TTL 은 안전망이다. 무효화 경로를 하나 빠뜨렸을 때
 * stale 데이터가 영원히 남지 않도록 한다.
 */
@Configuration
@EnableCaching
public class CartCacheConfig {

    /** 장바구니 캐시 TTL. 무효화가 주 수단이고, 이 값은 안전망이다. */
    private static final Duration CART_TTL = Duration.ofMinutes(30);
    /** 캐시별로 지정하지 않은 경우의 기본 TTL. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfiguration(DEFAULT_TTL))
                .withInitialCacheConfigurations(Map.of(
                        CartCacheNames.CART, cacheConfiguration(CART_TTL)
                ))
                .build();
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                // null 을 캐싱하면 "값 없음"과 "캐시 미적재"를 구분할 수 없어 비활성화한다.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()));
    }

    /**
     * 캐시 값 직렬화기.
     *
     * <p>역직렬화 시 대상 타입을 알아야 하므로 타입 정보(@class)를 함께 저장한다. 다만
     * {@code enableUnsafeDefaultTyping()} 은 Redis 에 적힌 임의의 클래스명을 그대로 믿기 때문에
     * Redis 가 오염되면 역직렬화 가젯 공격에 노출된다. 우리 패키지로 화이트리스트를 좁혀 막는다.
     */
    private GenericJacksonJsonRedisSerializer valueSerializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.example.groommvp.")
                .allowIfSubType(List.class)
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }
}
