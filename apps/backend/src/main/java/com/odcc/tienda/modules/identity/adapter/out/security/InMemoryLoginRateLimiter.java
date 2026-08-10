package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Component
public final class InMemoryLoginRateLimiter implements LoginRateLimitPort {

    private static final int MAX_ADDRESSES = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final int maxAttempts;
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryLoginRateLimiter(
        Clock clock,
        @Value("${app.security.login-rate-limit.max-attempts:20}") int maxAttempts
    ) {
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public synchronized void check(String clientAddress) {
        Instant now = clock.instant();
        String key = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
        Window current = windows.get(key);
        if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
            ensureCapacity();
            windows.put(key, new Window(now, 1));
            return;
        }
        if (current.attempts() >= maxAttempts) {
            long retryAfter = Duration.between(now, current.startedAt().plus(WINDOW)).toSeconds();
            throw new LoginRateLimitedException(retryAfter);
        }
        windows.put(key, new Window(current.startedAt(), current.attempts() + 1));
    }

    private void ensureCapacity() {
        if (windows.size() < MAX_ADDRESSES) return;
        windows.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
            .map(Map.Entry::getKey)
            .ifPresent(windows::remove);
    }

    private record Window(Instant startedAt, int attempts) {}
}
