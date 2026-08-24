package com.odcc.tienda.modules.identity.application.exception;

public final class LoginRateLimitUnavailableException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitUnavailableException(long retryAfterSeconds) {
        super("El control de intentos de inicio de sesion no esta disponible");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public LoginRateLimitUnavailableException(
        long retryAfterSeconds,
        Throwable cause
    ) {
        super(
            "El control de intentos de inicio de sesion no esta disponible",
            cause
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
