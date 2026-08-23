package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryLoginRateLimiterTest {

    @Test
    void shouldRejectIpAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(2, 20, 20);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.10", "bob");

        LoginRateLimitedException exception = assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "carol")
        );
        assertThat(exception.dimension()).isEqualTo(LoginRateLimitDimension.IP);
        assertDoesNotThrow(() -> limiter.check("192.0.2.11", "carol"));
    }

    @Test
    void shouldRejectIpAndAccountPairAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(20, 2, 20);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.10", "alice");

        LoginRateLimitedException exception = assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice")
        );
        assertThat(exception.dimension())
            .isEqualTo(LoginRateLimitDimension.PAIR);
        assertDoesNotThrow(() -> limiter.check("192.0.2.11", "alice"));
    }

    @Test
    void shouldRejectAccountAcrossDifferentAddressesAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(20, 20, 2);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.11", "alice");

        LoginRateLimitedException exception = assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.12", "alice")
        );
        assertThat(exception.dimension())
            .isEqualTo(LoginRateLimitDimension.ACCOUNT);
        assertDoesNotThrow(() -> limiter.check("192.0.2.12", "bob"));
    }

    @Test
    void shouldClearPairAndAccountAfterSuccessfulLogin() {
        InMemoryLoginRateLimiter limiter = limiter(20, 1, 1);
        limiter.onFailure("192.0.2.10", "alice");
        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice"));

        limiter.onSuccess("192.0.2.10", "alice");

        assertDoesNotThrow(() -> limiter.check("192.0.2.10", "alice"));
    }

    @Test
    void shouldKeepIpFailuresAfterSuccessfulLogin() {
        InMemoryLoginRateLimiter limiter = limiter(2, 20, 20);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onSuccess("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.10", "bob");

        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "carol"));
    }

    @Test
    void shouldAllowAgainAfterWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-23T12:00:00Z"));
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
            clock,
            properties(20, 1, 20),
            new LoginRateLimitMetrics(new SimpleMeterRegistry())
        );
        limiter.onFailure("192.0.2.10", "alice");
        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice"));

        clock.advance(Duration.ofSeconds(61));

        assertDoesNotThrow(() -> limiter.check("192.0.2.10", "alice"));
    }

    @Test
    void shouldRecordAllowedAndBlockedMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
            Clock.fixed(
                Instant.parse("2026-08-23T12:00:00Z"),
                ZoneOffset.UTC
            ),
            properties(20, 1, 20),
            new LoginRateLimitMetrics(meterRegistry)
        );

        limiter.check("192.0.2.10", "bob");
        limiter.onFailure("192.0.2.10", "alice");
        assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice")
        );

        assertThat(meterRegistry.get("auth.rate.limit.allowed")
            .tag("provider", "memory").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("auth.rate.limit.blocked")
            .tag("provider", "memory")
            .tag("dimension", "pair")
            .counter().count()).isEqualTo(1.0);
    }

    private static InMemoryLoginRateLimiter limiter(
        int ipMaxFailures,
        int pairMaxFailures,
        int accountMaxFailures
    ) {
        return new InMemoryLoginRateLimiter(
            Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
            properties(ipMaxFailures, pairMaxFailures, accountMaxFailures),
            new LoginRateLimitMetrics(new SimpleMeterRegistry())
        );
    }

    private static LoginRateLimitProperties properties(
        int ipMaxFailures,
        int pairMaxFailures,
        int accountMaxFailures
    ) {
        return new LoginRateLimitProperties(
            LoginRateLimitProperties.Provider.MEMORY,
            Duration.ofMinutes(1),
            ipMaxFailures,
            pairMaxFailures,
            accountMaxFailures,
            "tienda:auth:rate-limit:v1",
            null,
            Duration.ofSeconds(5)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
