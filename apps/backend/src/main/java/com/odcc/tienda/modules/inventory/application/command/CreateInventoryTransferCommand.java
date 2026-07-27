package com.odcc.tienda.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

public record CreateInventoryTransferCommand(
    UUID fromWarehouseId,
    UUID toWarehouseId,
    String reason,
    UUID createdBy,
    List<InventoryTransferItemCommand> items
) {
}
