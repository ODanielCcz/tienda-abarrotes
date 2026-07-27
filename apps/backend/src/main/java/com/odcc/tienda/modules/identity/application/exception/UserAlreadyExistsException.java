package com.odcc.tienda.modules.identity.application.exception;

public class UserAlreadyExistsException extends IdentityException {

    public UserAlreadyExistsException(String username) {
        super("Ya existe un usuario con username " + username);
    }
}
