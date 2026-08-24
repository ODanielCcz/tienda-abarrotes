package com.odcc.tienda.modules.inventory.application.model;

import java.util.Objects;

public record InventoryReceiptExecution(
    InventoryReceipt receipt,
    InventoryReceiptOutcome outcome
) {
    public InventoryReceiptExecution {
        Objects.requireNonNull(receipt, "La recepcion es obligatoria");
        Objects.requireNonNull(outcome, "El resultado de la recepcion es obligatorio");
    }

    public static InventoryReceiptExecution created(InventoryReceipt receipt) {
        return new InventoryReceiptExecution(receipt, InventoryReceiptOutcome.CREATED);
    }

    public static InventoryReceiptExecution replayed(InventoryReceipt receipt) {
        return new InventoryReceiptExecution(receipt, InventoryReceiptOutcome.REPLAYED);
    }

    public boolean wasCreated() {
        return outcome == InventoryReceiptOutcome.CREATED;
    }
}
