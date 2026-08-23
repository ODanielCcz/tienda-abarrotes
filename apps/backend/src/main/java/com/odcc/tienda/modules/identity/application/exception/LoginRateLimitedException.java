package com.odcc.tienda.modules.identity.application.exception;

import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;

public final class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;
    private final LoginRateLimitDimension dimension;

    public LoginRateLimitedException(long retryAfterSeconds) {
        this(retryAfterSeconds, LoginRateLimitDimension.IP);
    }

    public LoginRateLimitedException(
        long retryAfterSeconds,
        LoginRateLimitDimension dimension
    ) {
        super("Se excedio el limite de intentos de inicio de sesion");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.dimension = dimension;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public LoginRateLimitDimension dimension() {
        return dimension;
    }
}
