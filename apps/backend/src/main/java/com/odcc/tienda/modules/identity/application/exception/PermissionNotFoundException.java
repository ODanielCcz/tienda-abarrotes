package com.odcc.tienda.modules.identity.application.exception;

public class PermissionNotFoundException extends IdentityException {

    public PermissionNotFoundException(String permissionCode) {
        super("Permiso no encontrado: " + permissionCode);
    }
}