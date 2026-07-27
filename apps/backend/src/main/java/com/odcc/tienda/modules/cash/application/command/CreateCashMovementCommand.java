package com.odcc.tienda.modules.cash.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCashMovementCommand(
    UUID cashSessionId,
    String movementType,
    String direction,
    BigDecimal amount,
    String reference,
    String reason,
    UUID createdBy
) {
}