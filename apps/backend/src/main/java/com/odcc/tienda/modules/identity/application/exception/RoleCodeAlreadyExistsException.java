package com.odcc.tienda.modules.identity.application.exception;

public class RoleCodeAlreadyExistsException extends IdentityException {

    public RoleCodeAlreadyExistsException(String roleCode) {
        super("Ya existe un rol con codigo: " + roleCode);
    }
}