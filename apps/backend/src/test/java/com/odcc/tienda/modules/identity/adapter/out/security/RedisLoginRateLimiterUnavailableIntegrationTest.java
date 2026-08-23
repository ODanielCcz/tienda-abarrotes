package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisLoginRateLimiterUnavailableIntegrationTest {

    @Test
    void shouldFailClosedWithoutFallingBackToMemory() {
        String password = UUID.randomUUID().toString();
        GenericContainer<?> redisContainer = new GenericContainer<>(
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
                password
            )
            .waitingFor(
                Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
            );
        LettuceConnectionFactory connectionFactory = null;

        try {
            redisContainer.start();
            connectionFactory = connectionFactory(redisContainer, password);
            StringRedisTemplate redis = new StringRedisTemplate(
                connectionFactory
            );
            redis.afterPropertiesSet();
            RedisLoginRateLimiter limiter = limiter(redis);
            assertThat(limiter).isNotInstanceOf(InMemoryLoginRateLimiter.class);

            redisContainer.stop();

            assertThatThrownBy(() -> limiter.check("192.0.2.70", "admin"))
                .isInstanceOf(LoginRateLimitUnavailableException.class)
                .satisfies(exception -> assertThat(
                    ((LoginRateLimitUnavailableException) exception)
                        .retryAfterSeconds()
                ).isEqualTo(5));
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
            if (redisContainer.isRunning()) {
                redisContainer.stop();
            }
        }
    }

    private static RedisLoginRateLimiter limiter(StringRedisTemplate redis) {
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
            LoginRateLimitProperties.Provider.REDIS,
            Duration.ofMinutes(1),
            20,
            5,
            10,
            "tienda:auth:rate-limit:v1",
            Base64.getEncoder().encodeToString(new byte[32]),
            Duration.ofSeconds(5)
        );
        return new RedisLoginRateLimiter(
            redis,
            RedisScript.of(
                new ClassPathResource("redis/check-rate-limit.lua"),
                String.class
            ),
            RedisScript.of(
                new ClassPathResource("redis/record-login-failure.lua"),
                Long.class
            ),
            new RateLimitKeyEncoder(
                properties.namespace(),
                new byte[32]
            ),
            properties,
            new LoginRateLimitMetrics(new SimpleMeterRegistry())
        );
    }

    private static LettuceConnectionFactory connectionFactory(
        GenericContainer<?> container,
        String password
    ) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
            container.getHost(),
            container.getMappedPort(6379)
        );
        server.setPassword(RedisPassword.of(password));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofSeconds(1))
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
}
