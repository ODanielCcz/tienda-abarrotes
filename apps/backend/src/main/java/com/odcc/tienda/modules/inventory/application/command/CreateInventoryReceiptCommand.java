package com.odcc.tienda.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

public record CreateInventoryReceiptCommand(
    UUID warehouseId,
    UUID supplierId,
    UUID idempotencyKey,
    String reason,
    List<InventoryReceiptItemCommand> items,
    List<InventoryReceiptPalletCommand> pallets
) {
}
