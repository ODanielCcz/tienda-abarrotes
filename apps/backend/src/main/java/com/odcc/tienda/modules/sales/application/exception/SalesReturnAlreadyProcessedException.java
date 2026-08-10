package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public final class SalesReturnAlreadyProcessedException extends RuntimeException {

    public SalesReturnAlreadyProcessedException(UUID returnId) {
        super("La devolucion " + returnId + " ya fue procesada");
    }
}
