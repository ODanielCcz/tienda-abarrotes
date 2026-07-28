package com.odcc.tienda.modules.identity.application.exception;

import java.util.UUID;

public class BranchNotFoundException extends IdentityException {

    public BranchNotFoundException(UUID branchId) {
        super("Sucursal activa no encontrada: " + branchId);
    }
}