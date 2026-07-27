package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class SalesPaymentIdempotencyConflictException extends SalesException {
    public SalesPaymentIdempotencyConflictException(UUID idempotencyKey) {
        super("La llave de idempotencia de pago ya fue usada con otro contenido: " + idempotencyKey);
    }
}
