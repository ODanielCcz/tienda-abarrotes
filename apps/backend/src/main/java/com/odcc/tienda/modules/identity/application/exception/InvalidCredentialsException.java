package com.odcc.tienda.modules.identity.application.exception;

public final class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Las credenciales proporcionadas no son válidas");
    }
}
