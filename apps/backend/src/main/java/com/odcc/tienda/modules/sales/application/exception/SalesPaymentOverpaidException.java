package com.odcc.tienda.modules.sales.application.exception;

public class SalesPaymentOverpaidException extends SalesException {
    public SalesPaymentOverpaidException() {
        super("El pago excede el saldo pendiente de la venta");
    }
}
