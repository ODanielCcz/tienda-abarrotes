package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class SalesPaymentNotFoundException extends SalesException {

    public SalesPaymentNotFoundException(UUID paymentId) {
        super("Pago no encontrado: " + paymentId);
    }
}