package com.odcc.tienda.modules.inventory.application.command;

import java.util.List;

public record InventoryReceiptPalletCommand(
    String externalPalletCode,
    List<InventoryReceiptItemCommand> items
) {
}
