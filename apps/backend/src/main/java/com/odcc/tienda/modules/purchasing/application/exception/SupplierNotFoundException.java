package com.odcc.tienda.modules.purchasing.application.exception;

import java.util.UUID;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException(UUID supplierId) {
        super("No existe un proveedor con id " + supplierId);
    }
}
