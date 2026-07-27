package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class SalesReturnNotFoundException extends SalesException {

    public SalesReturnNotFoundException(UUID returnId) {
        super("Devolucion no encontrada: " + returnId);
    }
}