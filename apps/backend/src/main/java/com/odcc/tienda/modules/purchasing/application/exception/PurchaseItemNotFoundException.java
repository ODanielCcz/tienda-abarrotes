package com.odcc.tienda.modules.purchasing.application.exception;

import java.util.UUID;

public class PurchaseItemNotFoundException extends RuntimeException {
    public PurchaseItemNotFoundException(UUID purchaseItemId) {
        super("No existe un item de compra con id " + purchaseItemId);
    }
}
