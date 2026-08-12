package com.odcc.tienda.modules.sales.application.exception;

public final class SalesOrderCancellationConflictException extends SalesException {

    public SalesOrderCancellationConflictException() {
        super("La venta ya no esta disponible para cancelacion");
    }
}
