package com.odcc.tienda.modules.identity.application.exception;

public final class UserNotActiveException extends RuntimeException {

    public UserNotActiveException() {
        super("La cuenta de usuario no está activa");
    }
}
