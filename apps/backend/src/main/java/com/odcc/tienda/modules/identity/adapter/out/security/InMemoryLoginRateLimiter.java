package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class InMemoryLoginRateLimiter implements LoginRateLimitPort {

    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final int ipMaxFailures;
    private final int pairMaxFailures;
    private final int accountMaxFailures;
    private final Duration windowDuration;
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
        this.metrics = metrics;
    }

    @Override
    public synchronized void check(String clientAddress) {
        Instant now = clock.instant();
        String key = ipKey(clientAddress);
        ensureAllowed(key, ipMaxFailures, LoginRateLimitDimension.IP, now);
        metrics.allowed("memory");
        record(key, now);
    }

    @Override
    public synchronized void check(String clientAddress, String username) {
        Instant now = clock.instant();
        ensureAllowed(
            ipKey(clientAddress),
            ipMaxFailures,
            LoginRateLimitDimension.IP,
            now
        );
        ensureAllowed(
            pairKey(clientAddress, username),
            pairMaxFailures,
            LoginRateLimitDimension.PAIR,
            now
        );
        ensureAllowed(
            accountKey(username),
            accountMaxFailures,
            LoginRateLimitDimension.ACCOUNT,
            now
        );
        metrics.allowed("memory");
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
            ensureCapacity();
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

    private void ensureCapacity() {
        if (windows.size() < MAX_KEYS) {
            return;
        }
        windows.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
            .map(Map.Entry::getKey)
            .ifPresent(windows::remove);
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
