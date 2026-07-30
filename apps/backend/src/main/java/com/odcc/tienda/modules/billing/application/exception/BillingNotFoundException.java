package com.odcc.tienda.modules.billing.application.exception;

public final class BillingNotFoundException extends BillingException {
    public BillingNotFoundException(String resource) {
        super("No se encontro " + resource);
    }
}
