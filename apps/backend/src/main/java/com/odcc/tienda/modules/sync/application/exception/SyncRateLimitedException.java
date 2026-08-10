package com.odcc.tienda.modules.sync.application.exception;

public final class SyncRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public SyncRateLimitedException(long retryAfterSeconds) {
        super("El dispositivo excedio el limite de operaciones Sync");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
