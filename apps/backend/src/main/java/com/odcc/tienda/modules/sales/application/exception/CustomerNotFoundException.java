package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class CustomerNotFoundException extends SalesException {
    public CustomerNotFoundException(UUID customerId) {
        super("No existe el cliente " + customerId);
    }
}
