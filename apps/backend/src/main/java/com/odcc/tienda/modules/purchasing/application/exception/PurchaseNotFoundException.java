package com.odcc.tienda.modules.purchasing.application.exception;

import java.util.UUID;

public class PurchaseNotFoundException extends RuntimeException {
    public PurchaseNotFoundException(UUID purchaseId) {
        super("No existe una compra con id " + purchaseId);
    }
}
