package com.odcc.tienda.modules.purchasing.application.exception;

public class SupplierCodeAlreadyExistsException extends RuntimeException {
    public SupplierCodeAlreadyExistsException(String supplierCode) {
        super("Ya existe un proveedor con codigo " + supplierCode);
    }
}
