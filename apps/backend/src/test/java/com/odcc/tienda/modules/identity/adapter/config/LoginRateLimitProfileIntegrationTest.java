package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.adapter.out.security.InMemoryLoginRateLimiter;
import com.odcc.tienda.modules.identity.adapter.out.security.RedisLoginRateLimiter;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LoginRateLimitProfileIntegrationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                LoginRateLimitConfiguration.class,
                TestDependencies.class
            );

    @Test
    void localProfileShouldStartWithoutRedis() {
        contextRunner
            .withPropertyValues("spring.profiles.active=local")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(LoginRateLimitPort.class);
                assertThat(context.getBean(LoginRateLimitPort.class))
                    .isInstanceOf(InMemoryLoginRateLimiter.class);
            });
    }

    @Test
    void redisProfileShouldSelectRedisAdapter() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=redis",
                "app.security.login-rate-limit.key-secret-base64="
                    + validSecret()
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(LoginRateLimitPort.class);
                assertThat(context.getBean(LoginRateLimitPort.class))
                    .isInstanceOf(RedisLoginRateLimiter.class);
            });
    }

    @Test
    void prodProfileShouldRejectMissingHmacSecret() {
        contextRunner
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                        "Redis requiere un secreto Base64 de al menos 32 bytes"
                    );
            });
    }

    @Test
    void prodProfileShouldRejectMemoryOverride() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "app.security.login-rate-limit.provider=memory"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                        "El perfil prod requiere el proveedor redis"
                    );
            });
    }

    private static String validSecret() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
