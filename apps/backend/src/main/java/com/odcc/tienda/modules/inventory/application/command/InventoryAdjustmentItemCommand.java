package com.odcc.tienda.modules.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAdjustmentItemCommand(
    UUID productPresentationId,
    UUID lotId,
    String direction,
    BigDecimal quantity,
    BigDecimal unitCost
) {
}
