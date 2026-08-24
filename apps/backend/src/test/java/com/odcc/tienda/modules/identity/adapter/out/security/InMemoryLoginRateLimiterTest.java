package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryLoginRateLimiterTest {

    @Test
    void concurrentAttemptsShouldReserveNoMoreThanConfiguredCapacity()
        throws Exception {
        InMemoryLoginRateLimiter limiter = limiter(100, 5, 100);
        int workers = 20;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(workers);
        AtomicInteger admitted = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        limiter.check("192.0.2.80", "alice");
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
    void capacityExhaustionShouldFailClosedWithoutEvictingBlockedKeys() {
        InMemoryLoginRateLimiter limiter = limiter(100_000, 1, 100_000);
        limiter.check("192.0.2.81", "protected-account");

        boolean capacityRejected = false;
        for (int index = 0; index < 4_000; index++) {
            try {
                limiter.check("198.51.100." + index, "user-" + index);
            } catch (LoginRateLimitUnavailableException exception) {
                capacityRejected = true;
                break;
            }
        }

        assertThat(capacityRejected).isTrue();
        assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.81", "protected-account")
        );
    }

    @Test
    void shouldRejectIpAtConfiguredFailureLimit() {
        InMemoryLoginRateLimiter limiter = limiter(2, 20, 20);
        limiter.check("192.0.2.10", "alice");
        limiter.check("192.0.2.10", "bob");

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
        limiter.check("192.0.2.10", "alice");
        limiter.check("192.0.2.10", "alice");

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
        limiter.check("192.0.2.10", "alice");
        limiter.check("192.0.2.11", "alice");

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
        limiter.check("192.0.2.10", "alice");
        assertThrows(LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice"));

        limiter.onSuccess("192.0.2.10", "alice");

        assertDoesNotThrow(() -> limiter.check("192.0.2.10", "alice"));
    }

    @Test
    void shouldKeepIpFailuresAfterSuccessfulLogin() {
        InMemoryLoginRateLimiter limiter = limiter(2, 20, 20);
        limiter.check("192.0.2.10", "alice");
        limiter.check("192.0.2.10", "alice");
        limiter.onSuccess("192.0.2.10", "alice");
        limiter.check("192.0.2.10", "bob");

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
        limiter.check("192.0.2.10", "alice");
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
        limiter.check("192.0.2.10", "alice");
        assertThrows(
            LoginRateLimitedException.class,
            () -> limiter.check("192.0.2.10", "alice")
        );

        assertThat(meterRegistry.get("auth.rate.limit.allowed")
            .tag("provider", "memory").counter().count()).isEqualTo(2.0);
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
