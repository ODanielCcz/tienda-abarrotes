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
import java.util.Locale;
import java.util.Map;

@Component
public final class InMemoryLoginRateLimiter implements LoginRateLimitPort {

    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int PAIR_MAX_FAILURES = 5;
    private static final int ACCOUNT_MAX_FAILURES = 10;

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
        String key = ipKey(clientAddress);
        ensureAllowed(key, maxAttempts, now);
        record(key, now);
    }

    @Override
    public synchronized void check(String clientAddress, String username) {
        Instant now = clock.instant();
        ensureAllowed(ipKey(clientAddress), maxAttempts, now);
        ensureAllowed(pairKey(clientAddress, username), PAIR_MAX_FAILURES, now);
        ensureAllowed(accountKey(username), ACCOUNT_MAX_FAILURES, now);
    }

    @Override
    public synchronized void onFailure(String clientAddress, String username) {
        Instant now = clock.instant();
        record(ipKey(clientAddress), now);
        record(pairKey(clientAddress, username), now);
        record(accountKey(username), now);
    }

    @Override
    public synchronized void onSuccess(String clientAddress, String username) {
        windows.remove(pairKey(clientAddress, username));
        windows.remove(accountKey(username));
    }

    private void ensureAllowed(String key, int limit, Instant now) {
        Window current = activeWindow(key, now);
        if (current == null || current.attempts() < limit) return;
        long retryAfter = Math.max(
            1,
            Duration.between(now, current.startedAt().plus(WINDOW)).toSeconds()
        );
        throw new LoginRateLimitedException(retryAfter);
    }

    private void record(String key, Instant now) {
        Window current = activeWindow(key, now);
        if (current == null) {
            ensureCapacity();
            windows.put(key, new Window(now, 1));
            return;
        }
        windows.put(key, new Window(current.startedAt(), current.attempts() + 1));
    }

    private Window activeWindow(String key, Instant now) {
        Window current = windows.get(key);
        if (current != null && now.isBefore(current.startedAt().plus(WINDOW))) {
            return current;
        }
        windows.remove(key);
        return null;
    }

    private void ensureCapacity() {
        if (windows.size() < MAX_KEYS) return;
        windows.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
            .map(Map.Entry::getKey)
            .ifPresent(windows::remove);
    }

    private static String ipKey(String clientAddress) {
        return "ip:" + normalize(clientAddress, "unknown");
    }

    private static String pairKey(String clientAddress, String username) {
        return "pair:" + normalize(clientAddress, "unknown") + ':' + normalize(username, "unknown");
    }

    private static String accountKey(String username) {
        return "account:" + normalize(username, "unknown");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
            ? fallback
            : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Window(Instant startedAt, int attempts) {}
}
