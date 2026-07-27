package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryCountItemView(
    UUID inventoryCountItemId,
    UUID productPresentationId,
    UUID lotId,
    BigDecimal expectedQuantity,
    BigDecimal countedQuantity
) {
}
