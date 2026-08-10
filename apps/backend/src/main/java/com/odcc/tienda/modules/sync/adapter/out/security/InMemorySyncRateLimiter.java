package com.odcc.tienda.modules.sync.adapter.out.security;

import com.odcc.tienda.modules.sync.application.exception.SyncRateLimitedException;
import com.odcc.tienda.modules.sync.application.port.out.SyncRateLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public final class InMemorySyncRateLimiter implements SyncRateLimitPort {

    private static final int MAX_OPERATIONS = 300;
    private static final int MAX_DEVICES = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<UUID, Window> windows = new HashMap<>();

    @Override
    public synchronized void check(UUID deviceId) {
        if (deviceId == null) return;
        Instant now = clock.instant();
        Window current = windows.get(deviceId);
        if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
            ensureCapacity();
            windows.put(deviceId, new Window(now, 1));
            return;
        }
        if (current.operations() >= MAX_OPERATIONS) {
            throw new SyncRateLimitedException(
                Duration.between(now, current.startedAt().plus(WINDOW)).toSeconds()
            );
        }
        windows.put(deviceId, new Window(current.startedAt(), current.operations() + 1));
    }

    private void ensureCapacity() {
        if (windows.size() < MAX_DEVICES) return;
        windows.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
            .map(Map.Entry::getKey)
            .ifPresent(windows::remove);
    }

    private record Window(Instant startedAt, int operations) {}
}
