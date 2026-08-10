package com.odcc.tienda.shared.application.authorization;

import java.util.UUID;

public final class BranchAccessDeniedException extends RuntimeException {

    public BranchAccessDeniedException() {
        super("La operacion requiere acceso global a sucursales");
    }

    public BranchAccessDeniedException(UUID branchId) {
        super("No tienes acceso a la sucursal " + branchId);
    }
}
