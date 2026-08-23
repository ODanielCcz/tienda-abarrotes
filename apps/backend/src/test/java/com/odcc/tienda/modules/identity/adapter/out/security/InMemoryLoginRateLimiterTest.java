package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryLoginRateLimiterTest {

    @Test
    void shouldRejectIpAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(2, 20, 20);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.10", "bob");

        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "carol"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.11", "carol"));
    }

    @Test
    void shouldRejectIpAndAccountPairAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(20, 2, 20);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.10", "alice");

        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.11", "alice"));
    }

    @Test
    void shouldRejectAccountAcrossDifferentAddressesAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(20, 20, 2);
        limiter.onFailure("192.0.2.10", "alice");
        limiter.onFailure("192.0.2.11", "alice");

        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.12", "alice"));
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
            clock, 20, 1, 20, Duration.ofMinutes(1)
        );
        limiter.onFailure("192.0.2.10", "alice");
        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice"));

        clock.advance(Duration.ofSeconds(61));

        assertDoesNotThrow(() -> limiter.check("192.0.2.10", "alice"));
    }

    private static InMemoryLoginRateLimiter limiter(
        int ipMaxFailures,
        int pairMaxFailures,
        int accountMaxFailures
    ) {
        return new InMemoryLoginRateLimiter(
            Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
            ipMaxFailures,
            pairMaxFailures,
            accountMaxFailures,
            Duration.ofMinutes(1)
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
