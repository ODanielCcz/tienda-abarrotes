package com.odcc.tienda.modules.inventory.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptItemCommand(
    UUID productPresentationId,
    String lotNumber,
    LocalDate manufacturedAt,
    LocalDate expiresAt,
    BigDecimal quantity,
    BigDecimal unitCost
) {
}
