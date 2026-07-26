package com.odcc.tienda.modules.inventory.application.exception;

import java.util.UUID;

public class InventoryReceiptAlreadyExistsException extends RuntimeException {
    public InventoryReceiptAlreadyExistsException(UUID idempotencyKey) {
        super("Ya existe una recepcion con la llave de idempotencia " + idempotencyKey + " pero el contenido enviado es diferente");
    }
}
