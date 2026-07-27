package com.odcc.tienda.modules.sales.application.exception;

public class CustomerCodeAlreadyExistsException extends SalesException {
    public CustomerCodeAlreadyExistsException(String customerCode) {
        super("Ya existe un cliente con codigo " + customerCode);
    }
}
