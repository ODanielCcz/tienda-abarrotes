package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class SalesOrderIdempotencyConflictException extends RuntimeException {
    public SalesOrderIdempotencyConflictException(UUID idempotencyKey) {
        super("Ya existe una venta con la llave de idempotencia " + idempotencyKey + " pero el contenido enviado es diferente");
    }
}
