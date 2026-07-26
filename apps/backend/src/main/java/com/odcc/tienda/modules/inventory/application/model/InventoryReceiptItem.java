package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptItem(
    UUID stockMovementItemId,
    UUID productPresentationId,
    UUID lotId,
    String lotNumber,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal quantityBefore,
    BigDecimal quantityAfter,
    LocalDate manufacturedAt,
    LocalDate expiresAt
) {
}
