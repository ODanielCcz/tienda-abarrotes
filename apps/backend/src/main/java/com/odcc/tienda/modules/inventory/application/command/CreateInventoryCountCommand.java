package com.odcc.tienda.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

public record CreateInventoryCountCommand(
    UUID warehouseId,
    UUID startedBy,
    List<InventoryCountItemCommand> items
) {
}
