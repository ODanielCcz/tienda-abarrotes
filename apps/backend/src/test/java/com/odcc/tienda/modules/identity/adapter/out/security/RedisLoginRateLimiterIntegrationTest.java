package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisLoginRateLimiterIntegrationTest {

    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
    private static final String NAMESPACE = "tienda:auth:rate-limit:v1";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
        DockerImageName.parse("redis:8.8.1-alpine")
    )
        .withExposedPorts(6379)
        .withCommand(
            "redis-server",
            "--save",
            "",
            "--appendonly",
            "no",
            "--requirepass",
            REDIS_PASSWORD
        )
        .waitingFor(
            Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                .withStartupTimeout(Duration.ofSeconds(30))
        );

    private static LettuceConnectionFactory connectionFactoryA;
    private static LettuceConnectionFactory connectionFactoryB;
    private static StringRedisTemplate redisA;
    private static StringRedisTemplate redisB;

    @BeforeAll
    static void connect() {
        connectionFactoryA = connectionFactory();
        connectionFactoryB = connectionFactory();
        redisA = template(connectionFactoryA);
        redisB = template(connectionFactoryB);
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactoryA != null) {
            connectionFactoryA.destroy();
        }
        if (connectionFactoryB != null) {
            connectionFactoryB.destroy();
        }
    }

    @BeforeEach
    void clearRedis() {
        redisA.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void twoLimiterInstancesShouldShareCounters() {
        RedisLoginRateLimiter first = limiter(redisA, 100, 2, 100);
        RedisLoginRateLimiter second = limiter(redisB, 100, 2, 100);

        first.check("192.0.2.10", "alice");
        second.check("192.0.2.10", "alice");

        assertThatThrownBy(() -> first.check("192.0.2.10", "alice"))
            .isInstanceOf(LoginRateLimitedException.class)
            .extracting("dimension")
            .isEqualTo(LoginRateLimitDimension.PAIR);
    }

    @Test
    void concurrentFailuresShouldNotLoseIncrements() throws Exception {
        RedisLoginRateLimiter first = limiter(redisA, 20, 100, 100);
        RedisLoginRateLimiter second = limiter(redisB, 20, 100, 100);
        int workers = 20;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                RedisLoginRateLimiter selected = index % 2 == 0
                    ? first
                    : second;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    selected.check("192.0.2.20", "user-" + UUID.randomUUID());
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThatThrownBy(() -> first.check("192.0.2.20", "another-user"))
            .isInstanceOf(LoginRateLimitedException.class)
            .extracting("dimension")
            .isEqualTo(LoginRateLimitDimension.IP);
    }

    @Test
    void concurrentAttemptsAcrossInstancesShouldNotOverAdmit() throws Exception {
        RedisLoginRateLimiter first = limiter(redisA, 100, 5, 100);
        RedisLoginRateLimiter second = limiter(redisB, 100, 5, 100);
        int workers = 20;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(workers);
        AtomicInteger admitted = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                RedisLoginRateLimiter selected = index % 2 == 0
                    ? first
                    : second;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        selected.check("192.0.2.21", "alice");
                        admitted.incrementAndGet();
                        checked.countDown();
                        assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (LoginRateLimitedException exception) {
                        checked.countDown();
                    }
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(admitted.get()).isEqualTo(5);
    }

    @Test
    void shouldBlockEachDimensionAtItsOwnThreshold() {
        RedisLoginRateLimiter ipLimiter = limiter(redisA, 1, 100, 100);
        ipLimiter.check("192.0.2.30", "alice");
        assertBlocked(
            ipLimiter,
            "192.0.2.30",
            "bob",
            LoginRateLimitDimension.IP
        );

        RedisLoginRateLimiter pairLimiter = limiter(redisA, 100, 1, 100);
        pairLimiter.check("192.0.2.31", "carol");
        assertBlocked(
            pairLimiter,
            "192.0.2.31",
            "carol",
            LoginRateLimitDimension.PAIR
        );

        RedisLoginRateLimiter accountLimiter = limiter(redisA, 100, 100, 2);
        accountLimiter.check("192.0.2.32", "dave");
        accountLimiter.check("192.0.2.33", "dave");
        assertBlocked(
            accountLimiter,
            "192.0.2.34",
            "dave",
            LoginRateLimitDimension.ACCOUNT
        );
    }

    @Test
    void shouldReturnTheLongestTtlAmongBlockedDimensions() {
        RedisLoginRateLimiter limiter = limiter(redisA, 1, 1, 1);
        RateLimitKeyEncoder.Keys keys = encoder().encode(
            "192.0.2.35",
            "alice"
        );
        redisA.opsForValue().set(keys.ip(), "1", Duration.ofSeconds(3));
        redisA.opsForValue().set(keys.pair(), "1", Duration.ofSeconds(12));
        redisA.opsForValue().set(keys.account(), "1", Duration.ofSeconds(6));

        assertThatThrownBy(() -> limiter.check("192.0.2.35", "alice"))
            .isInstanceOf(LoginRateLimitedException.class)
            .satisfies(exception -> {
                LoginRateLimitedException limited =
                    (LoginRateLimitedException) exception;
                assertThat(limited.dimension())
                    .isEqualTo(LoginRateLimitDimension.PAIR);
                assertThat(limited.retryAfterSeconds()).isBetween(10L, 12L);
            });
    }

    @Test
    void ttlShouldExpireAndAllowAgain() throws Exception {
        RedisLoginRateLimiter limiter = limiter(
            redisA,
            100,
            1,
            100,
            Duration.ofSeconds(1)
        );
        limiter.check("192.0.2.40", "alice");
        assertBlocked(
            limiter,
            "192.0.2.40",
            "alice",
            LoginRateLimitDimension.PAIR
        );

        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                limiter.check("192.0.2.40", "alice");
                return;
            } catch (LoginRateLimitedException exception) {
                Thread.sleep(50);
            }
        }

        assertThatCode(() -> limiter.check("192.0.2.40", "alice"))
            .doesNotThrowAnyException();
    }

    @Test
    void successfulLoginShouldClearPairAndAccountButKeepIp() {
        RedisLoginRateLimiter limiter = limiter(redisA, 100, 100, 100);
        RateLimitKeyEncoder encoder = encoder();
        RateLimitKeyEncoder.Keys keys = encoder.encode("192.0.2.50", "alice");
        limiter.check("192.0.2.50", "alice");
        limiter.check("192.0.2.50", "alice");

        limiter.onSuccess("192.0.2.50", "alice");

        assertThat(redisA.opsForValue().get(keys.ip())).isEqualTo("1");
        assertThat(redisA.opsForValue().get(keys.pair())).isNull();
        assertThat(redisA.opsForValue().get(keys.account())).isNull();
    }

    @Test
    void storedKeysShouldBeOpaque() {
        RedisLoginRateLimiter limiter = limiter(redisA, 100, 100, 100);
        limiter.check("192.0.2.60", "admin@example.com");

        Set<String> keys = redisA.keys(NAMESPACE + ":*");

        assertThat(keys).hasSize(3);
        assertThat(keys).allMatch(key -> key.matches(
            "tienda:auth:rate-limit:v1:(ip|pair|account):[0-9a-f]{64}"
        ));
        assertThat(keys).noneMatch(key ->
            key.contains("192.0.2.60") || key.contains("admin@example.com")
        );
    }

    private static void assertBlocked(
        RedisLoginRateLimiter limiter,
        String address,
        String username,
        LoginRateLimitDimension dimension
    ) {
        assertThatThrownBy(() -> limiter.check(address, username))
            .isInstanceOf(LoginRateLimitedException.class)
            .extracting("dimension")
            .isEqualTo(dimension);
    }

    private static RedisLoginRateLimiter limiter(
        StringRedisTemplate redis,
        int ipLimit,
        int pairLimit,
        int accountLimit
    ) {
        return limiter(
            redis,
            ipLimit,
            pairLimit,
            accountLimit,
            Duration.ofMinutes(1)
        );
    }

    private static RedisLoginRateLimiter limiter(
        StringRedisTemplate redis,
        int ipLimit,
        int pairLimit,
        int accountLimit,
        Duration window
    ) {
        return new RedisLoginRateLimiter(
            redis,
            RedisScript.of(
                new ClassPathResource("redis/check-rate-limit.lua"),
                String.class
            ),
            RedisScript.of(
                new ClassPathResource("redis/clear-successful-login-reservation.lua"),
                Long.class
            ),
            encoder(),
            properties(ipLimit, pairLimit, accountLimit, window),
            new LoginRateLimitMetrics(new SimpleMeterRegistry())
        );
    }

    private static RateLimitKeyEncoder encoder() {
        return new RateLimitKeyEncoder(NAMESPACE, new byte[32]);
    }

    private static LoginRateLimitProperties properties(
        int ipLimit,
        int pairLimit,
        int accountLimit,
        Duration window
    ) {
        return new LoginRateLimitProperties(
            LoginRateLimitProperties.Provider.REDIS,
            window,
            ipLimit,
            pairLimit,
            accountLimit,
            NAMESPACE,
            Base64.getEncoder().encodeToString(new byte[32]),
            Duration.ofSeconds(5)
        );
    }

    private static LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
            REDIS.getHost(),
            REDIS.getMappedPort(6379)
        );
        server.setPassword(RedisPassword.of(REDIS_PASSWORD));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build())
                .build())
            .commandTimeout(Duration.ofSeconds(1))
            .shutdownTimeout(Duration.ZERO)
            .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            server,
            client
        );
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static StringRedisTemplate template(
        LettuceConnectionFactory connectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate(
            connectionFactory
        );
        template.afterPropertiesSet();
        return template;
    }
}
