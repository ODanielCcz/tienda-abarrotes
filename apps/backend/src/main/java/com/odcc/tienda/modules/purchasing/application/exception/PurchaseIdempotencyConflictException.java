package com.odcc.tienda.modules.purchasing.application.exception;

public final class PurchaseIdempotencyConflictException extends PurchasingException {

    public PurchaseIdempotencyConflictException() {
        super("La llave de idempotencia ya fue utilizada con otra solicitud");
    }
}
