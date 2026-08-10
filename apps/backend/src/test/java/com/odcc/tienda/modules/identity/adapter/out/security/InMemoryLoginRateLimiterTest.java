package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryLoginRateLimiterTest {

    @Test
    void shouldRejectAttemptsAboveConfiguredLimitForSameAddress() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
            Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC),
            2
        );

        assertDoesNotThrow(() -> limiter.check("192.0.2.10"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.10"));
        assertThrows(LoginRateLimitedException.class, () -> limiter.check("192.0.2.10"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.11"));
    }
}
