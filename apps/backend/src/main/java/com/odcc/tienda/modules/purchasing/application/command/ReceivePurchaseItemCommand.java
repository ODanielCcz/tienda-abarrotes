package com.odcc.tienda.modules.purchasing.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivePurchaseItemCommand(
    UUID purchaseItemId,
    String lotNumber,
    LocalDate manufacturedAt,
    LocalDate expiresAt,
    BigDecimal quantity
) {
}
