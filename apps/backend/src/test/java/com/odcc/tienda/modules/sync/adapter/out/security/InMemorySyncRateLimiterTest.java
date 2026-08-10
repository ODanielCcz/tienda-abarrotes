package com.odcc.tienda.modules.sync.adapter.out.security;

import com.odcc.tienda.modules.sync.application.exception.SyncRateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySyncRateLimiterTest {

    @Test
    void shouldRejectOperationAboveDeviceLimit() {
        InMemorySyncRateLimiter limiter = new InMemorySyncRateLimiter(
            Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC)
        );
        UUID deviceId = UUID.randomUUID();

        for (int attempt = 0; attempt < 300; attempt++) {
            assertDoesNotThrow(() -> limiter.check(deviceId));
        }
        assertThrows(SyncRateLimitedException.class, () -> limiter.check(deviceId));
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()));
    }
}
