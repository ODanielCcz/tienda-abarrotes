package com.odcc.tienda.modules.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryTransferItemCommand(
    UUID productPresentationId,
    UUID lotId,
    BigDecimal quantity,
    BigDecimal unitCost
) {
}
