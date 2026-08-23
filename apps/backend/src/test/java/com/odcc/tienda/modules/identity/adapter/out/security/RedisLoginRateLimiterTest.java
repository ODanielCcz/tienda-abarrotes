package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisLoginRateLimiterTest {

    private StringRedisTemplate redis;
    private RedisScript<String> checkScript;
    private RedisScript<Long> failureScript;
    private RateLimitKeyEncoder keyEncoder;
    private SimpleMeterRegistry meterRegistry;
    private RedisLoginRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        checkScript = mock(RedisScript.class);
        failureScript = mock(RedisScript.class);
        keyEncoder = new RateLimitKeyEncoder(
            "tienda:auth:rate-limit:v1",
            new byte[32]
        );
        meterRegistry = new SimpleMeterRegistry();
        limiter = new RedisLoginRateLimiter(
            redis,
            checkScript,
            failureScript,
            keyEncoder,
            properties(),
            new LoginRateLimitMetrics(meterRegistry)
        );
    }

    @Test
    void shouldAllowWhenScriptReturnsAllowedDecision() {
        given(redis.execute(same(checkScript), anyList(), any(Object[].class)))
            .willReturn("1:0:0");

        assertThatCode(() -> limiter.check("192.0.2.10", "alice"))
            .doesNotThrowAnyException();

        assertThat(meterRegistry.get("auth.rate.limit.allowed")
            .tag("provider", "redis").counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowRateLimitedWithDimensionAndRetryAfter() {
        given(redis.execute(same(checkScript), anyList(), any(Object[].class)))
            .willReturn("0:37:2");

        assertThatThrownBy(() -> limiter.check("192.0.2.10", "alice"))
            .isInstanceOf(LoginRateLimitedException.class)
            .satisfies(exception -> {
                LoginRateLimitedException limited =
                    (LoginRateLimitedException) exception;
                assertThat(limited.retryAfterSeconds()).isEqualTo(37);
                assertThat(limited.dimension())
                    .isEqualTo(LoginRateLimitDimension.PAIR);
            });

        assertThat(meterRegistry.get("auth.rate.limit.blocked")
            .tag("provider", "redis")
            .tag("dimension", "pair")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordAllThreeFailureDimensions() {
        RateLimitKeyEncoder.Keys keys = keyEncoder.encode("192.0.2.10", "alice");
        given(redis.execute(same(failureScript), anyList(), any(Object[].class)))
            .willReturn(1L);

        limiter.onFailure("192.0.2.10", "alice");

        verify(redis).execute(
            same(failureScript),
            eq(keys.asList()),
            eq("60000")
        );
    }

    @Test
    void shouldClearOnlyPairAndAccountAfterSuccess() {
        RateLimitKeyEncoder.Keys keys = keyEncoder.encode("192.0.2.10", "alice");

        limiter.onSuccess("192.0.2.10", "alice");

        verify(redis).delete(java.util.List.of(keys.pair(), keys.account()));
    }

    @Test
    void shouldTranslateRedisFailureToUnavailable() {
        given(redis.execute(same(checkScript), anyList(), any(Object[].class)))
            .willThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> limiter.check("192.0.2.10", "alice"))
            .isInstanceOf(LoginRateLimitUnavailableException.class)
            .satisfies(exception -> assertThat(
                ((LoginRateLimitUnavailableException) exception)
                    .retryAfterSeconds()
            ).isEqualTo(5));

        assertThat(meterRegistry.get("auth.rate.limit.backend.errors")
            .tag("provider", "redis")
            .tag("operation", "check")
            .counter().count()).isEqualTo(1.0);
    }

    private static LoginRateLimitProperties properties() {
        return new LoginRateLimitProperties(
            LoginRateLimitProperties.Provider.REDIS,
            Duration.ofMinutes(1),
            20,
            5,
            10,
            "tienda:auth:rate-limit:v1",
            Base64.getEncoder().encodeToString(new byte[32]),
            Duration.ofSeconds(5)
        );
    }
}
