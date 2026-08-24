package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.function.Supplier;

@RequiredArgsConstructor
public final class LoginRateLimitMetrics {

    private final MeterRegistry meterRegistry;

    public void allowed(String provider) {
        counter("auth.rate.limit.allowed", provider, null, null).increment();
    }

    public void blocked(String provider, LoginRateLimitDimension dimension) {
        counter(
            "auth.rate.limit.blocked",
            provider,
            dimension.name().toLowerCase(Locale.ROOT),
            null
        ).increment();
    }

    public void backendError(String provider, String operation) {
        counter(
            "auth.rate.limit.backend.errors",
            provider,
            null,
            operation
        ).increment();
    }

    public <T> T timeRedis(String operation, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } finally {
            sample.stop(
                Timer.builder("auth.rate.limit.redis.latency")
                    .tag("operation", operation)
                    .register(meterRegistry)
            );
        }
    }

    private Counter counter(
        String name,
        String provider,
        String dimension,
        String operation
    ) {
        Counter.Builder builder = Counter.builder(name)
            .tag("provider", provider);
        if (dimension != null) {
            builder.tag("dimension", dimension);
        }
        if (operation != null) {
            builder.tag("operation", operation);
        }
        return builder.register(meterRegistry);
    }
}
