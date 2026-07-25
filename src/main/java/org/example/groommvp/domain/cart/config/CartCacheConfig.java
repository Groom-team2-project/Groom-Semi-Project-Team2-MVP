package org.example.groommvp.domain.cart.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
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
 *
 * <p><b>장애 흡수:</b> {@link CachingConfigurer#errorHandler()} 로 로깅 후 무시하는 핸들러를 등록해,
 * Redis 장애 시 캐시 조회/저장/무효화 예외가 그대로 전파되어 DB 로 대체 가능한 요청까지
 * 실패하는 일을 막는다. (가용성 우선 — evict 실패로 남는 stale 은 TTL 이 안전망이다.)
 *
 * <p><b>트랜잭션 정합:</b> 캐시 매니저를 {@link TransactionAwareCacheManagerProxy} 로 감싸
 * {@code put}/{@code evict} 가 트랜잭션 <b>커밋 이후</b>에 반영되게 한다. 이로써 커밋 전에 evict
 * 된 틈에 동시 조회가 옛 값을 재적재하는 레이스를 줄이고, 롤백된 변경이 캐시에 새지 않게 한다.
 */
@Configuration
@EnableCaching
public class CartCacheConfig implements CachingConfigurer {

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

    /**
     * 캐시 인프라가 사용할 CacheManager. {@link CachingConfigurer#cacheManager()} 를 오버라이드해야
     * 이 매니저(TransactionAwareCacheManagerProxy)가 확실히 적용된다. 인자 있는 시그니처는
     * 인터페이스 메서드를 오버라이드하지 못해 기본 null 콜백으로 동작하므로 인자 없이 선언한다.
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(redisConnectionFactory())
                .cacheDefaults(cacheConfiguration(DEFAULT_TTL))
                .withInitialCacheConfigurations(Map.of(
                        CartCacheNames.CART, cacheConfiguration(CART_TTL)
                ))
                .build();
        // 커밋 이후에 put/evict 되도록 감싼다. (CartService 조회/변경 메서드와
        // CartOrderService.checkout() 이 같은 캐시를 쓰므로 커밋 전후 stale 노출을 줄인다.)
        return new TransactionAwareCacheManagerProxy(redisCacheManager);
    }

    /**
     * Redis 장애를 흡수한다. 캐시 조회/저장/무효화 중 예외가 나면 로깅만 하고 삼켜,
     * {@code @Cacheable}/{@code @CacheEvict} 경로가 DB 로 대체 가능한 요청까지 실패하지 않게 한다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
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
