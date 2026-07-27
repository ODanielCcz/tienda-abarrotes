package com.odcc.tienda.modules.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryCountItemCommand(
    UUID productPresentationId,
    UUID lotId,
    BigDecimal countedQuantity
) {
}
