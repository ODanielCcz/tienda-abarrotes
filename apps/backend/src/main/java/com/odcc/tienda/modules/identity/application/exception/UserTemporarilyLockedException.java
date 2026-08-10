package com.odcc.tienda.modules.identity.application.exception;

import java.time.Instant;

public final class UserTemporarilyLockedException extends RuntimeException {

    public UserTemporarilyLockedException(Instant lockedUntil) {
        super("La cuenta esta bloqueada temporalmente hasta " + lockedUntil);
    }
}
