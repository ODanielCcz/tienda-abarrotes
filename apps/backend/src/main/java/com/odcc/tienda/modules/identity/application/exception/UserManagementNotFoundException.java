package com.odcc.tienda.modules.identity.application.exception;

import java.util.UUID;

public class UserManagementNotFoundException extends IdentityException {

    public UserManagementNotFoundException(UUID userId) {
        super("Usuario no encontrado: " + userId);
    }
}
