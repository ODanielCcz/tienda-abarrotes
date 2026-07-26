package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record StockMovementItemView(
    UUID stockMovementItemId,
    UUID productPresentationId,
    String sku,
    String presentationName,
    UUID lotId,
    String lotNumber,
    String direction,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal quantityBefore,
    BigDecimal quantityAfter
) {
}
