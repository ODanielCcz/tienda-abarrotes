package com.odcc.tienda.modules.purchasing.application.exception;

import java.util.UUID;

public final class PurchaseItemMismatchException extends RuntimeException {

    public PurchaseItemMismatchException(UUID purchaseId, UUID purchaseItemId) {
        super("La partida " + purchaseItemId + " no pertenece a la compra " + purchaseId);
    }
}
