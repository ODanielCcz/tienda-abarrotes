package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
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
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryLoginRateLimiter(
        Clock clock,
        int ipMaxFailures,
        int pairMaxFailures,
        int accountMaxFailures,
        Duration windowDuration
    ) {
        this.clock = clock;
        this.ipMaxFailures = Math.max(1, ipMaxFailures);
        this.pairMaxFailures = Math.max(1, pairMaxFailures);
        this.accountMaxFailures = Math.max(1, accountMaxFailures);
        this.windowDuration = windowDuration;
    }

    @Override
    public synchronized void check(String clientAddress) {
        Instant now = clock.instant();
        String key = ipKey(clientAddress);
        ensureAllowed(key, ipMaxFailures, now);
        record(key, now);
    }

    @Override
    public synchronized void check(String clientAddress, String username) {
        Instant now = clock.instant();
        ensureAllowed(ipKey(clientAddress), ipMaxFailures, now);
        ensureAllowed(pairKey(clientAddress, username), pairMaxFailures, now);
        ensureAllowed(accountKey(username), accountMaxFailures, now);
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
