package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

public final class InMemoryLoginRateLimiter implements LoginRateLimitPort {

    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final int ipMaxFailures;
    private final int pairMaxFailures;
    private final int accountMaxFailures;
    private final Duration windowDuration;
    private final long unavailableRetryAfterSeconds;
    private final LoginRateLimitMetrics metrics;
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryLoginRateLimiter(
        Clock clock,
        LoginRateLimitProperties properties,
        LoginRateLimitMetrics metrics
    ) {
        this.clock = clock;
        this.ipMaxFailures = properties.ipMaxFailures();
        this.pairMaxFailures = properties.pairMaxFailures();
        this.accountMaxFailures = properties.accountMaxFailures();
        this.windowDuration = properties.window();
        this.unavailableRetryAfterSeconds = Math.max(
            1,
            properties.unavailableRetryAfter().toSeconds()
        );
        this.metrics = metrics;
    }

    @Override
    public synchronized void check(String clientAddress) {
        check(clientAddress, "unknown");
    }

    @Override
    public synchronized void check(String clientAddress, String username) {
        Instant now = clock.instant();
        String ipKey = ipKey(clientAddress);
        String pairKey = pairKey(clientAddress, username);
        String accountKey = accountKey(username);
        purgeExpired(now);
        ensureAllowed(
            ipKey,
            ipMaxFailures,
            LoginRateLimitDimension.IP,
            now
        );
        ensureAllowed(
            pairKey,
            pairMaxFailures,
            LoginRateLimitDimension.PAIR,
            now
        );
        ensureAllowed(
            accountKey,
            accountMaxFailures,
            LoginRateLimitDimension.ACCOUNT,
            now
        );
        ensureCapacityFor(List.of(ipKey, pairKey, accountKey));
        record(ipKey, now);
        record(pairKey, now);
        record(accountKey, now);
        metrics.allowed("memory");
    }

    @Override
    public synchronized void onSuccess(String clientAddress, String username) {
        Instant now = clock.instant();
        String ipKey = ipKey(clientAddress);
        Window ipWindow = activeWindow(ipKey, now);
        if (ipWindow != null) {
            if (ipWindow.attempts() <= 1) {
                windows.remove(ipKey);
            } else {
                windows.put(
                    ipKey,
                    new Window(ipWindow.startedAt(), ipWindow.attempts() - 1)
                );
            }
        }
        windows.remove(pairKey(clientAddress, username));
        windows.remove(accountKey(username));
    }

    private void ensureAllowed(
        String key,
        int limit,
        LoginRateLimitDimension dimension,
        Instant now
    ) {
        Window current = activeWindow(key, now);
        if (current == null || current.attempts() < limit) {
            return;
        }

        long retryAfter = Math.max(
            1,
            Duration.between(
                now,
                current.startedAt().plus(windowDuration)
            ).toSeconds()
        );
        metrics.blocked("memory", dimension);
        throw new LoginRateLimitedException(retryAfter, dimension);
    }

    private void record(String key, Instant now) {
        Window current = activeWindow(key, now);
        if (current == null) {
            windows.put(key, new Window(now, 1));
            return;
        }
        windows.put(key, new Window(current.startedAt(), current.attempts() + 1));
    }

    private Window activeWindow(String key, Instant now) {
        Window current = windows.get(key);
        if (
            current != null
                && now.isBefore(current.startedAt().plus(windowDuration))
        ) {
            return current;
        }
        windows.remove(key);
        return null;
    }

    private void purgeExpired(Instant now) {
        windows.entrySet().removeIf(entry -> !now.isBefore(
            entry.getValue().startedAt().plus(windowDuration)
        ));
    }

    private void ensureCapacityFor(List<String> keys) {
        long missingKeys = keys.stream()
            .filter(key -> !windows.containsKey(key))
            .count();
        if (windows.size() + missingKeys <= MAX_KEYS) {
            return;
        }
        metrics.backendError("memory", "capacity");
        throw new LoginRateLimitUnavailableException(
            unavailableRetryAfterSeconds
        );
    }

    private static String ipKey(String clientAddress) {
        return "ip:" + normalize(clientAddress, "unknown");
    }

    private static String pairKey(String clientAddress, String username) {
        return "pair:" + normalize(clientAddress, "unknown") + ':'
            + normalize(username, "unknown");
    }

    private static String accountKey(String username) {
        return "account:" + normalize(username, "unknown");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
            ? fallback
            : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Window(Instant startedAt, int attempts) {
    }
}
