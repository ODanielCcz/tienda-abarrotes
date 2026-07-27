package com.odcc.tienda.modules.identity.application.exception;

public class LastSystemAdminException extends IdentityException {

    public LastSystemAdminException() {
        super("No se puede quitar o deshabilitar el ultimo usuario con rol SYSTEM_ADMIN");
    }
}
