package com.odcc.tienda.modules.inventory.application.exception;

import java.util.UUID;

public class InventoryReceiptNotFoundException extends RuntimeException {
    public InventoryReceiptNotFoundException(UUID receiptId) {
        super("No existe una recepcion de inventario con id " + receiptId);
    }
}
