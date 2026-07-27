package com.odcc.tienda.modules.inventory.application.command;

import java.util.UUID;

public record ConfirmInventoryCountCommand(
    UUID inventoryCountId,
    UUID confirmedBy
) {
}
