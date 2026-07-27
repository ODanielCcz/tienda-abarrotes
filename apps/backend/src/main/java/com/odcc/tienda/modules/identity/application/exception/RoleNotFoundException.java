package com.odcc.tienda.modules.identity.application.exception;

public class RoleNotFoundException extends IdentityException {

    public RoleNotFoundException(String roleCode) {
        super("Rol activo no encontrado: " + roleCode);
    }
}
