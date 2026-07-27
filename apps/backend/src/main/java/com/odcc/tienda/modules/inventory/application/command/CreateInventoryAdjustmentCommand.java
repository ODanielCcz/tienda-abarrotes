package com.odcc.tienda.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

public record CreateInventoryAdjustmentCommand(
    UUID warehouseId,
    String reason,
    UUID createdBy,
    List<InventoryAdjustmentItemCommand> items
) {
}
