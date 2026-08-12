package com.odcc.tienda.modules.sales.application.exception;

public final class SalesReturnOrderConflictException extends SalesException {

    public SalesReturnOrderConflictException() {
        super("La venta ya no permite confirmar devoluciones");
    }
}
