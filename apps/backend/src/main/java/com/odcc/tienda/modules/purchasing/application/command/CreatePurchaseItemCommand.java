package com.odcc.tienda.modules.purchasing.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseItemCommand(
    UUID productPresentationId,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal discountAmount,
    BigDecimal taxAmount
) {
}
