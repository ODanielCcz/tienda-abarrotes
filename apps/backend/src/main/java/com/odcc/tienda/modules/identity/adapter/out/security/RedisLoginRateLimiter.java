package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.adapter.config.LoginRateLimitProperties;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.function.Supplier;

public final class RedisLoginRateLimiter implements LoginRateLimitPort {

    private static final String PROVIDER = "redis";

    private final StringRedisTemplate redis;
    private final RedisScript<String> checkScript;
    private final RedisScript<Long> successScript;
    private final RateLimitKeyEncoder keyEncoder;
    private final LoginRateLimitProperties properties;
    private final LoginRateLimitMetrics metrics;

    public RedisLoginRateLimiter(
        StringRedisTemplate redis,
        RedisScript<String> checkScript,
        RedisScript<Long> successScript,
        RateLimitKeyEncoder keyEncoder,
        LoginRateLimitProperties properties,
        LoginRateLimitMetrics metrics
    ) {
        this.redis = redis;
        this.checkScript = checkScript;
        this.successScript = successScript;
        this.keyEncoder = keyEncoder;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void check(String clientAddress) {
        check(clientAddress, "unknown");
    }

    @Override
    public void check(String clientAddress, String username) {
        RateLimitKeyEncoder.Keys keys = keyEncoder.encode(
            clientAddress,
            username
        );
        String result = execute(
            "reserve",
            () -> redis.execute(
                checkScript,
                keys.asList(),
                Integer.toString(properties.ipMaxFailures()),
                Integer.toString(properties.pairMaxFailures()),
                Integer.toString(properties.accountMaxFailures()),
                Long.toString(properties.window().toMillis())
            )
        );

        Decision decision = parseDecision(result);
        if (decision.allowed()) {
            metrics.allowed(PROVIDER);
            return;
        }

        metrics.blocked(PROVIDER, decision.dimension());
        throw new LoginRateLimitedException(
            decision.retryAfterSeconds(),
            decision.dimension()
        );
    }

    @Override
    public void onSuccess(String clientAddress, String username) {
        RateLimitKeyEncoder.Keys keys = keyEncoder.encode(
            clientAddress,
            username
        );
        execute(
            "success",
            () -> redis.execute(successScript, keys.asList())
        );
    }

    private <T> T execute(String operation, Supplier<T> supplier) {
        try {
            return metrics.timeRedis(operation, supplier);
        } catch (DataAccessException exception) {
            throw unavailable(operation, exception);
        }
    }

    private Decision parseDecision(String value) {
        if (value == null) {
            throw unavailable(
                "reserve",
                new IllegalStateException("Redis devolvio una decision nula")
            );
        }

        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Formato inesperado");
            }
            boolean allowed = switch (parts[0]) {
                case "1" -> true;
                case "0" -> false;
                default -> throw new IllegalArgumentException(
                    "Estado inesperado"
                );
            };
            long retryAfter = Long.parseLong(parts[1]);
            int dimensionIndex = Integer.parseInt(parts[2]);
            LoginRateLimitDimension dimension = switch (dimensionIndex) {
                case 0, 1 -> LoginRateLimitDimension.IP;
                case 2 -> LoginRateLimitDimension.PAIR;
                case 3 -> LoginRateLimitDimension.ACCOUNT;
                default -> throw new IllegalArgumentException(
                    "Dimension inesperada"
                );
            };
            return new Decision(allowed, Math.max(0, retryAfter), dimension);
        } catch (IllegalArgumentException exception) {
            throw unavailable("reserve", exception);
        }
    }

    private LoginRateLimitUnavailableException unavailable(
        String operation,
        Throwable cause
    ) {
        metrics.backendError(PROVIDER, operation);
        return new LoginRateLimitUnavailableException(
            properties.unavailableRetryAfter().toSeconds(),
            cause
        );
    }

    private record Decision(
        boolean allowed,
        long retryAfterSeconds,
        LoginRateLimitDimension dimension
    ) {
    }
}
