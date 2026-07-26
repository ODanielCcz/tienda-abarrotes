package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class SalesOrderNotFoundException extends RuntimeException {
    public SalesOrderNotFoundException(UUID salesOrderId) {
        super("No existe una venta con id " + salesOrderId);
    }
}
